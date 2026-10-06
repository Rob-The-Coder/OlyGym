package olygym.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;
import java.util.Locale;

/**
 * The rest timer's lock-screen notification: an ongoing, silent chronometer SystemUI ticks down
 * from `endsAt`, with -15s / +15s / Skip handled by {@link RestActionReceiver}.
 *
 * Not a media notification: a MediaSession would hijack whatever music is actually playing.
 * The countdown itself is never pushed per second — SystemUI renders it from the `when`
 * timestamp alone, so it keeps ticking with the process dead.
 *
 * What does need writing is the Live Update progress bar and critical text (Android 16+), which
 * are plain values with no timer behind them; {@link RestTimerService} is the foreground service
 * that rewrites them each second while a rest runs.
 *
 * The mirror in SharedPreferences is what makes the buttons work after the app is gone; the
 * JS-facing half is {@link RestNotificationPlugin}.
 */
final class RestNotification {

    static final String CHANNEL_ID = "rest_timer";
    // A channel's importance is frozen at creation, so a `rest_timer` an earlier build made
    // silent/LOW can never be raised. Post through a second channel instead of deleting one the
    // user may have tuned — see channelId().
    static final String CHANNEL_ID_CURRENT = "rest_timer_2";
    static final int NOTIFICATION_ID = 2000;
    static final String PREFS = "olygym_rest";

    static final String ACTION_PLUS15 = "olygym.app.action.REST_PLUS15";
    static final String ACTION_MINUS15 = "olygym.app.action.REST_MINUS15";
    static final String ACTION_SKIP = "olygym.app.action.REST_SKIP";
    static final String ACTION_EXPIRE = "olygym.app.action.REST_EXPIRE";

    private static final int REQUEST_CONTENT = 0;
    private static final int REQUEST_PLUS = 1;
    private static final int REQUEST_MINUS = 2;
    private static final int REQUEST_SKIP = 3;
    private static final int REQUEST_EXPIRE = 4;

    private static final String TAG = "OlyGymRest";

    private RestNotification() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context ctx, long endsAtMs, int totalSec, Integer forIdx, String title, String text,
                     String sub, String big) {
        SharedPreferences.Editor e = prefs(ctx).edit()
                .putBoolean("active", true)
                .putLong("endsAtMs", endsAtMs)
                .putInt("totalSec", totalSec)
                .putString("title", title)
                .putString("text", text)
                .putString("sub", sub)
                .putString("big", big);
        if (forIdx != null) e.putInt("forIdx", forIdx);
        else e.remove("forIdx");
        e.apply();
    }

    /**
     * The notification for the current mirror, or null when there is no rest left to show. Every
     * caller rebuilds from the mirror rather than caching one — that is what keeps the service,
     * the buttons and a cold start rendering the same thing.
     */
    static Notification build(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean("active", false)) return null;
        long endsAt = p.getLong("endsAtMs", 0L);
        long remaining = endsAt - System.currentTimeMillis();
        if (remaining <= 0) return null;
        String channel = channelId(ctx);

        String title = p.getString("title", ctx.getString(R.string.rest_channel_name));
        String text = p.getString("text", "");
        String sub = p.getString("sub", "");
        String big = p.getString("big", text);

        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(ctx, REQUEST_CONTENT, open, pendingFlags());

        // The framework Builder rather than NotificationCompat: the Live Update extras below are
        // API 36, and this project cannot resolve an androidx.core new enough to model them.
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(ctx, channel)
                : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endsAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                // A normal-importance notification, not a "silent" one: the channel carries no
                // sound, but the system files silent notifications under a collapsed group (and
                // can hide them on the lock screen), which is the opposite of the point here.
                .setDefaults(0)
                .setSound(null)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setPriority(Notification.PRIORITY_LOW)
                // The expanded / lock-screen card names the workout alongside the set count while
                // the collapsed line stays short and the header keeps the ticking chronometer.
                .setStyle(new Notification.BigTextStyle().bigText(big));
        if (!sub.isEmpty()) b.setSubText(sub);

        b.addAction(action(ctx, ACTION_MINUS15, REQUEST_MINUS, R.string.rest_minus15));
        b.addAction(action(ctx, ACTION_PLUS15, REQUEST_PLUS, R.string.rest_plus15));
        b.addAction(action(ctx, ACTION_SKIP, REQUEST_SKIP, R.string.rest_skip));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The OS clears it when the rest is over, with no process involved.
            b.setTimeoutAfter(remaining);
        } else {
            // API 24-25 predate timeoutAfter: cancel it from an alarm instead.
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.RTC, endsAt, expireIntent(ctx));
        }

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 "Live Updates": the promoted card, a progress bar that really fills, and
            // a critical text that really counts down. RestTimerService rewrites both each second;
            // without it the bar would be a stale number, which is why we never leave it running
            // off a single post.
            int totalSec = Math.max(1, p.getInt("totalSec", 0));
            long elapsed = Math.max(0L, totalSec * 1000L - remaining);
            int percent = (int) Math.min(100L, Math.round(elapsed * 100.0 / (totalSec * 1000L)));
            b.getExtras().putBoolean("android.requestPromotedOngoing", true);
            b.setShortCriticalText(clock(remaining));
            b.setStyle(new Notification.ProgressStyle().setProgress(percent));
        }

        return b.build();
    }

    /** Post (or re-post) the notification from the current mirror. */
    static void post(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Log.i(TAG, "post active=" + p.getBoolean("active", false));
        Notification n = build(ctx);
        if (n == null) {
            // Ran out while nobody was looking: drop the mirror so the next start is clean.
            if (p.getBoolean("active", false)) clear(ctx);
            return;
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS revoked between the JS check and here; the timer is unaffected.
            Log.w(TAG, "not posted: " + e.getMessage());
        }
    }

    /** Drop the mirror and the notification. */
    static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(expireIntent(ctx));
        }
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID);
    }

    /** m:ss, the shape SystemUI shows for the chronometer it is replacing in the chip. */
    private static String clock(long ms) {
        long sec = Math.max(0L, (ms + 999L) / 1000L);
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    /**
     * The channel to post through. Importance is frozen when a channel is created, so a
     * `rest_timer` an earlier build made silent/LOW keeps hiding the timer under "Silent
     * notifications" for good. Rather than delete a channel the user may have tuned, a second one
     * carries the timer from then on.
     */
    private static String channelId(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return CHANNEL_ID;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing == null) { createChannel(nm, ctx, CHANNEL_ID); return CHANNEL_ID; }
        if (existing.getImportance() == NotificationManager.IMPORTANCE_DEFAULT) return CHANNEL_ID;
        if (nm.getNotificationChannel(CHANNEL_ID_CURRENT) == null) createChannel(nm, ctx, CHANNEL_ID_CURRENT);
        return CHANNEL_ID_CURRENT;
    }

    private static void createChannel(NotificationManager nm, Context ctx, String id) {
        NotificationChannel ch = new NotificationChannel(
                id, ctx.getString(R.string.rest_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(ctx.getString(R.string.rest_channel_desc));
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static Notification.Action action(Context ctx, String action, int request, int label) {
        Intent i = new Intent(ctx, RestActionReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, request, i, pendingFlags());
        // The icon is decorative: Android hides action icons since 7.0, so the timer glyph just
        // keeps an older skin from showing an empty slot.
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_stat_rest), ctx.getString(label), pi).build();
    }

    private static PendingIntent expireIntent(Context ctx) {
        Intent i = new Intent(ctx, RestActionReceiver.class).setAction(ACTION_EXPIRE);
        return PendingIntent.getBroadcast(ctx, REQUEST_EXPIRE, i, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}

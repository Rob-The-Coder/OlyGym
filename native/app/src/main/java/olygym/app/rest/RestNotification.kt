package olygym.app.rest

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import olygym.app.MainActivity
import olygym.app.R
import olygym.app.lib.I18nCore

/**
 * The rest timer's lock-screen notification: an ongoing, silent chronometer SystemUI ticks down
 * from `endsAt`, with -15s / +15s / Skip handled by [RestActionReceiver].
 *
 * Not a media notification: a MediaSession would hijack whatever music is actually playing.
 * The countdown itself is never pushed per second — SystemUI renders it from the `when`
 * timestamp alone, so it keeps ticking with the process dead.
 *
 * What does need writing is the Live Update progress bar and critical text (Android 16+), which
 * are plain values with no timer behind them; [RestTimerService] is the foreground service that
 * rewrites them each second while a rest runs.
 *
 * The mirror in SharedPreferences is what makes the buttons work after the app is gone. The
 * JS-facing half (RestNotificationPlugin.java) is not ported: the screen is already native.
 */
object RestNotification {

    const val CHANNEL_ID = "rest_timer"
    // A channel's importance is frozen at creation, so a `rest_timer` an earlier build made
    // silent/LOW can never be raised. Post through a second channel instead of deleting one the
    // user may have tuned — see channelId().
    const val CHANNEL_ID_CURRENT = "rest_timer_2"
    const val NOTIFICATION_ID = 2000
    const val PREFS = "olygym_rest"

    const val ACTION_PLUS15 = "olygym.app.action.REST_PLUS15"
    const val ACTION_MINUS15 = "olygym.app.action.REST_MINUS15"
    const val ACTION_SKIP = "olygym.app.action.REST_SKIP"
    const val ACTION_EXPIRE = "olygym.app.action.REST_EXPIRE"

    private const val REQUEST_CONTENT = 0
    private const val REQUEST_PLUS = 1
    private const val REQUEST_MINUS = 2
    private const val REQUEST_SKIP = 3
    private const val REQUEST_EXPIRE = 4

    private const val TAG = "OlyGymRest"

    // The copy follows the profile's language, like everything else in the app: the shipping
    // locale pack is what this app translates with, so the keys are the shipping strings rather
    // than new ones. "Rest timer" and "Skip" are in it.js already; the description falls back to
    // its English source, which is the convention for a key with no entry.
    internal val CHANNEL_NAME: String get() = I18nCore.t("Rest timer")
    private val CHANNEL_DESC: String get() = I18nCore.t("Shows the rest countdown between sets.")
    // These two are units, not words, so they are the same in every language the app ships.
    private const val LABEL_MINUS15 = "-15s"
    private const val LABEL_PLUS15 = "+15s"
    private val LABEL_SKIP: String get() = I18nCore.t("Skip")

    // RestNotification.java's status-bar glyph, carried over with the port.
    private val ICON = R.drawable.ic_stat_rest

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        ctx: Context,
        endsAtMs: Long,
        totalSec: Int,
        title: String,
        text: String,
        sub: String,
        big: String,
    ) {
        // The Java also mirrors forIdx; the native entry point (RestTimer) has no workout index to
        // pass, so the key is not written.
        prefs(ctx).edit()
            .putBoolean("active", true)
            .putLong("endsAtMs", endsAtMs)
            .putInt("totalSec", totalSec)
            .putString("title", title)
            .putString("text", text)
            .putString("sub", sub)
            .putString("big", big)
            .apply()
    }

    /**
     * The notification for the current mirror, or null when there is no rest left to show. Every
     * caller rebuilds from the mirror rather than caching one — that is what keeps the service,
     * the buttons and a cold start rendering the same thing.
     */
    fun build(ctx: Context): Notification? {
        val p = prefs(ctx)
        if (!p.getBoolean("active", false)) return null
        val endsAt = p.getLong("endsAtMs", 0L)
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining <= 0) return null
        val channel = channelId(ctx)

        val title = p.getString("title", CHANNEL_NAME) ?: CHANNEL_NAME
        val text = p.getString("text", "") ?: ""
        val sub = p.getString("sub", "") ?: ""
        val big = p.getString("big", text) ?: text

        val open = Intent(ctx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val content = PendingIntent.getActivity(ctx, REQUEST_CONTENT, open, pendingFlags())

        // The framework Builder rather than NotificationCompat: the Live Update extras below are
        // API 36, and this project cannot resolve an androidx.core new enough to model them.
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channel)
        } else {
            Notification.Builder(ctx)
        }
        b.setSmallIcon(ICON)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // A normal-importance notification, not a "silent" one: the channel carries no sound,
            // but the system files silent notifications under a collapsed group (and can hide them
            // on the lock screen), which is the opposite of the point here.
            .setDefaults(0)
            .setSound(null)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setPriority(Notification.PRIORITY_LOW)
            // The expanded / lock-screen card names the workout alongside the set count while the
            // collapsed line stays short and the header keeps the ticking chronometer.
            .setStyle(Notification.BigTextStyle().bigText(big))
        if (sub.isNotEmpty()) b.setSubText(sub)

        b.addAction(action(ctx, ACTION_MINUS15, REQUEST_MINUS, LABEL_MINUS15))
        b.addAction(action(ctx, ACTION_PLUS15, REQUEST_PLUS, LABEL_PLUS15))
        b.addAction(action(ctx, ACTION_SKIP, REQUEST_SKIP, LABEL_SKIP))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The OS clears it when the rest is over, with no process involved.
            b.setTimeoutAfter(remaining)
        } else {
            // API 24-25 predate timeoutAfter: cancel it from an alarm instead.
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.set(AlarmManager.RTC, endsAt, expireIntent(ctx))
        }

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 "Live Updates": the promoted card, a progress bar that really fills, and
            // a critical text that really counts down. RestTimerService rewrites both each second;
            // without it the bar would be a stale number, which is why we never leave it running
            // off a single post.
            val totalSec = p.getInt("totalSec", 0)
            b.extras.putBoolean("android.requestPromotedOngoing", true)
            b.setShortCriticalText(restClock(remaining))
            b.setStyle(Notification.ProgressStyle().setProgress(restProgressPercent(remaining, totalSec)))
        }

        return b.build()
    }

    /** Post (or re-post) the notification from the current mirror. */
    fun post(ctx: Context) {
        val p = prefs(ctx)
        Log.i(TAG, "post active=" + p.getBoolean("active", false))
        val n = build(ctx)
        if (n == null) {
            // Ran out while nobody was looking: drop the mirror so the next start is clean.
            if (p.getBoolean("active", false)) clear(ctx)
            return
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and here; the timer is unaffected.
            Log.w(TAG, "not posted: " + e.message)
        }
    }

    /** Drop the mirror and the notification. */
    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(expireIntent(ctx))
        }
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }

    /**
     * The channel to post through. Importance is frozen when a channel is created, so a
     * `rest_timer` an earlier build made silent/LOW keeps hiding the timer under "Silent
     * notifications" for good. Rather than delete a channel the user may have tuned, a second one
     * carries the timer from then on.
     */
    private fun channelId(ctx: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return CHANNEL_ID
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            createChannel(nm, ctx, CHANNEL_ID)
            return CHANNEL_ID
        }
        if (existing.importance == NotificationManager.IMPORTANCE_DEFAULT) return CHANNEL_ID
        if (nm.getNotificationChannel(CHANNEL_ID_CURRENT) == null) {
            createChannel(nm, ctx, CHANNEL_ID_CURRENT)
        }
        return CHANNEL_ID_CURRENT
    }

    private fun createChannel(nm: NotificationManager, ctx: Context, id: String) {
        val ch = NotificationChannel(id, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = CHANNEL_DESC
        ch.setShowBadge(false)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    private fun action(ctx: Context, action: String, request: Int, label: String): Notification.Action {
        val i = Intent(ctx, RestActionReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(ctx, request, i, pendingFlags())
        // The icon is decorative: Android hides action icons since 7.0, so the timer glyph just
        // keeps an older skin from showing an empty slot.
        return Notification.Action.Builder(
            Icon.createWithResource(ctx, ICON), label, pi,
        ).build()
    }

    private fun expireIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, RestActionReceiver::class.java).setAction(ACTION_EXPIRE)
        return PendingIntent.getBroadcast(ctx, REQUEST_EXPIRE, i, pendingFlags())
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

/** m:ss, the shape SystemUI shows for the chronometer it is replacing in the chip. Pure. */
internal fun restClock(ms: Long): String {
    val sec = maxOf(0L, (ms + 999L) / 1000L)
    return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
}

/** The Live Update progress bar's fill: 0 at the start of the rest, 100 at its end. Pure. */
internal fun restProgressPercent(remainingMs: Long, totalSec: Int): Int {
    val total = maxOf(1, totalSec)
    val elapsed = maxOf(0L, total * 1000L - remainingMs)
    return minOf(100L, Math.round(elapsed * 100.0 / (total * 1000L))).toInt()
}

package olygym.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps the lock-screen card live on Android 16+: a foreground service that rewrites the
 * notification once a second so the Live Update progress bar actually fills and its critical text
 * actually counts down.
 *
 * The chronometer needs no help — SystemUI ticks it from the `when` timestamp. A progress value
 * and a critical text are plain numbers, and the app cannot keep writing them while the WebView is
 * paused and the process is cached, which is exactly when someone is looking at the lock screen.
 *
 * shortService rather than a general foreground service: a rest is a bounded, user-started timer,
 * and the type caps it at a few minutes. It is only ever started on API 36+, where the progress
 * card exists at all, so older devices gain no background process for no visual gain.
 */
public class RestTimerService extends Service {

    private static final long TICK_MS = 1000L;
    private static final String TAG = "OlyGymRest";
    /** Live Updates ship in Android 16. Below it there is no bar to keep fresh. */
    private static final int API_LIVE_UPDATES = 36;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!refresh()) { stopSelf(); return; }
            handler.postDelayed(this, TICK_MS);
        }
    };

    /** true while the rest still has something to show; false once it is over. */
    private boolean refresh() {
        Notification n = RestNotification.build(this);
        if (n == null) {
            // Ran out (or was cleared from a button) while the ticker was running: drop the
            // mirror too, so the next start does not resurrect a dead rest.
            RestNotification.clear(this);
            return false;
        }
        try {
            NotificationManagerCompat.from(this).notify(RestNotification.NOTIFICATION_ID, n);
        } catch (SecurityException e) {
            Log.w(TAG, "service not posted: " + e.getMessage());
        }
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = RestNotification.build(this);
        if (n == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(RestNotification.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            } else {
                startForeground(RestNotification.NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            // The system refused the promotion (background start, type not allowed): the plain
            // notification posted by the plugin still stands, it just stops counting the bar.
            Log.w(TAG, "foreground not started: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Start (or re-start) the ticker. A no-op off Android 16. */
    static void start(Context ctx) {
        if (Build.VERSION.SDK_INT < API_LIVE_UPDATES) return;
        try {
            ContextCompat.startForegroundService(ctx, new Intent(ctx, RestTimerService.class));
        } catch (Exception e) {
            Log.w(TAG, "service not started: " + e.getMessage());
        }
    }

    static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, RestTimerService.class));
        } catch (Exception ignored) {
            // Nothing running: nothing to stop.
        }
    }
}

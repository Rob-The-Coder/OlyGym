package olygym.app.rest

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the lock-screen card live on Android 16+: a foreground service that rewrites the
 * notification once a second so the Live Update progress bar actually fills and its critical text
 * actually counts down.
 *
 * The chronometer needs no help — SystemUI ticks it from the `when` timestamp. A progress value
 * and a critical text are plain numbers, and a Compose activity cannot keep writing them while the
 * process is cached, which is exactly when someone is looking at the lock screen.
 *
 * shortService rather than a general foreground service: a rest is a bounded, user-started timer,
 * and the type caps it at a few minutes. It is only ever started on API 36+, where the progress
 * card exists at all, so older devices gain no background process for no visual gain.
 */
class RestTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!refresh()) {
                stopSelf()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** true while the rest still has something to show; false once it is over. */
    private fun refresh(): Boolean {
        val n = RestNotification.build(this)
        if (n == null) {
            // Ran out (or was cleared from a button) while the ticker was running: drop the mirror
            // too, so the next start does not resurrect a dead rest.
            RestNotification.clear(this)
            return false
        }
        try {
            NotificationManagerCompat.from(this).notify(RestNotification.NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "service not posted: " + e.message)
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = RestNotification.build(this)
        if (n == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    RestNotification.NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            } else {
                startForeground(RestNotification.NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            // The system refused the promotion (background start, type not allowed): the plain
            // notification posted by RestTimer still stands, it just stops counting the bar.
            Log.w(TAG, "foreground not started: " + e.message)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TICK_MS = 1000L
        private const val TAG = "OlyGymRest"

        /** Live Updates ship in Android 16. Below it there is no bar to keep fresh. */
        private const val API_LIVE_UPDATES = 36

        /** Start (or re-start) the ticker. A no-op off Android 16. */
        fun start(ctx: Context) {
            if (Build.VERSION.SDK_INT < API_LIVE_UPDATES) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, RestTimerService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "service not started: " + e.message)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, RestTimerService::class.java))
            } catch (ignored: Exception) {
                // Nothing running: nothing to stop.
            }
        }
    }
}

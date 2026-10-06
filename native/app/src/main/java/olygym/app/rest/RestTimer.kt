package olygym.app.rest

import android.content.Context

/**
 * The entry point a workout screen calls — a Compose screen calls `RestTimer` directly; the
 * shipping app's Capacitor plugin (RestNotificationPlugin.java) is not ported.
 *
 * `start`/`update` write the native mirror the notification is drawn from and make sure the
 * Android 16+ ticker is running; `stop` drops both. The countdown itself is never pushed: SystemUI
 * reads it from the mirror's end time, so it keeps ticking with the process dead.
 */
object RestTimer {

    /** Start (or replace) a rest. */
    fun start(context: Context, endsAtMillis: Long, totalMillis: Long) {
        RestNotification.save(
            context,
            endsAtMs = endsAtMillis,
            totalSec = totalSeconds(totalMillis),
            title = RestNotification.CHANNEL_NAME,
            text = "",
            sub = "",
            big = "",
        )
        RestNotification.post(context)
        RestTimerService.start(context)
    }

    /** The running rest moved its end time (or its length); same mirror, same ticker. */
    fun update(context: Context, endsAtMillis: Long, totalMillis: Long) {
        start(context, endsAtMillis, totalMillis)
    }

    /** Drop the mirror and the notification, and stop the ticker. */
    fun stop(context: Context) {
        RestTimerService.stop(context)
        RestNotification.clear(context)
    }
}

/**
 * The mirror's `totalSec` from the millisecond argument: the native of the JS payload's
 * `Math.max(1, Math.round(timer.total || 0))`, which is fed seconds. Pure.
 */
internal fun totalSeconds(totalMillis: Long): Int =
    maxOf(1, Math.round(totalMillis / 1000.0).toInt())

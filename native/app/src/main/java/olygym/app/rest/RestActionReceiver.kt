package olygym.app.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** The notification's two step buttons move the end by this much. */
internal const val REST_STEP_MS = 15_000L

/** The end-time delta for a notification action, or null when it is not a step. Pure. */
internal fun restStepMillis(action: String): Long? = when (action) {
    RestNotification.ACTION_PLUS15 -> REST_STEP_MS
    RestNotification.ACTION_MINUS15 -> -REST_STEP_MS
    else -> null
}

/**
 * The end time after a step, or null when taking off more than is left means "I'm ready now",
 * exactly like the in-app bar. Pure.
 */
internal fun restSteppedEnd(endsAtMs: Long, step: Long, nowMs: Long): Long? {
    val next = endsAtMs + step
    return if (next <= nowMs) null else next
}

/**
 * The notification's buttons, handled with no app running: it only touches the native mirror
 * ([RestNotification]). The shipping receiver also forwards the action to the live JS bridge
 * (RestNotificationPlugin.emit) so JS can adopt the result on resume; there is no bridge here, so
 * the mirror is the only record and a screen that comes back reads it through
 * [RestNotification.prefs].
 */
class RestActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("OlyGymRest", "action " + action)
        val p = RestNotification.prefs(context)
        val active = p.getBoolean("active", false)

        if (RestNotification.ACTION_EXPIRE == action) {
            RestNotification.clear(context)
            return
        }
        if (!active) return

        if (RestNotification.ACTION_SKIP == action) {
            RestNotification.clear(context)
            return
        }

        val step = restStepMillis(action) ?: return
        val endsAt = restSteppedEnd(p.getLong("endsAtMs", 0L), step, System.currentTimeMillis())
        if (endsAt == null) {
            // Taking off more than is left means "I'm ready now", exactly like the in-app bar.
            RestNotification.clear(context)
            return
        }
        p.edit().putLong("endsAtMs", endsAt).apply()
        RestNotification.post(context)
    }
}

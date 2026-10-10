package olygym.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system asks for less animation — the accessibility "Remove animations" setting, or
 * an animator scale of zero.
 *
 * Compose does publish the same fact as a MotionDurationScale, but only in a coroutine context, and
 * a composable is exactly where the app has to decide whether to start an infinite drift. Reading
 * the platform's own two scales is what the framework itself does, and it is the same answer the
 * web app gets from prefers-reduced-motion.
 *
 * Three animations in this app are the app's own rather than Material's, and all three answer to
 * this flag: the wave's drift, the route fade, and the timer's flash.
 */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animator == 0f || transition == 0f
    }
}

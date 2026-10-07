package olygym.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One duration scale and the four expressive curves, from frontend/src/m3.tokens.css. Five names
 * used to describe three durations in the web app; this is the three, and a new duration or a
 * bespoke curve is the thing the design system exists to stop.
 *
 * Two of the four have a consumer the app draws itself: the route fade (emphasizedDecelerate) and
 * the Start disc's press (spring). The others curve motion the app does not own — M3's own sheets,
 * dialogs and segmented thumb — and emphasized has no consumer at all, in the web either: it is
 * defined in m3.tokens.css and referenced by nothing.
 */
internal object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 400

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasized: Easing = CubicBezierEasing(0.3f, 0f, 0f, 1f)
    /** Fast out of the gate, long settle — entrance motion. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** The soft end of the Expressive spring: a gentle overshoot, not a toy. */
    val spring: Easing = CubicBezierEasing(0.34f, 1.32f, 0.64f, 1f)
}

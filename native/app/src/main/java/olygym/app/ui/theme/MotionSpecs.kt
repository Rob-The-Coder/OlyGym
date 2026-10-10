package olygym.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The scheme's own animation specs, for the motion the app draws itself.
 *
 * The curves in Motion.kt are the token table ported from m3.tokens.css and stay as the named
 * fallback, but the app does not have to describe motion in béziers any more: Material publishes
 * the specs its own components animate on, and using them is what makes the route fade, the Start
 * press and the media expand feel like the same system as the sheet, the dialog and the segmented
 * thumb. A spring is not a curve with a longer tail — it is what the rest of the app already does.
 *
 * spatial is for a change of position or size; effects is for a change of colour or alpha, where a
 * spring's overshoot would mean nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@ReadOnlyComposable
fun <T> spatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

/** The quick end of the spatial scale — a press, not a journey. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@ReadOnlyComposable
fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

/** Colour and alpha. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@ReadOnlyComposable
fun <T> effectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

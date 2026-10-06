package olygym.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The roles Compose's ColorScheme has no slot for, from frontend/src/m3.tokens.css: a divider
 * hairline and the disabled text tone. Screens read these instead of a raw colour, the same way
 * they read MaterialTheme.colorScheme for everything M3 covers.
 */
@Immutable
data class ExtraColors(
    /** --m3-hairline: the divider between rows. */
    val hairline: Color,
    /** --m3-on-surface-disabled: decorative and disabled text only, never a label. */
    val onSurfaceDisabled: Color,
)

internal val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color.Unspecified, Color.Unspecified)
}

val MaterialTheme.extraColors: ExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current

/**
 * The interaction state layers, one percentage per state. M3's own components apply theirs; these
 * are for the app's custom controls, which paint the layer in the container's own on-colour rather
 * than in a second hue.
 */
internal object StateLayers {
    const val HOVER = 0.08f
    const val FOCUS = 0.10f
    const val PRESS = 0.10f
    const val DRAG = 0.16f
}

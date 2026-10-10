package olygym.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import olygym.app.lib.DEFAULT_ACCENT

/** The three values the theme setting can hold. 'system' follows the OS, as it does on the web. */
const val THEME_DARK = "dark"
const val THEME_LIGHT = "light"
const val THEME_SYSTEM = "system"

/**
 * The app theme: the generated schemes from ui/theme/Scheme.kt, the ported type scale, shape and
 * the extra roles — as M3 Expressive, so every Material component moves on the expressive motion
 * scheme instead of the baseline one.
 *
 * The ColorScheme, Shapes and Typography handed over are the app's own, so this changes *motion*
 * and nothing that is painted: the palette, the geometry and the type scale are the ones the port
 * already had. That is what lets it land before the expressive work does, as a commit that is
 * meant not to move a pixel.
 *
 * An unknown accent falls back to the default rather than leaving the UI without a scheme, the
 * same guard the web app has.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OlyGymTheme(
    theme: String = THEME_DARK,
    accent: String = DEFAULT_ACCENT,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val pair = SCHEMES[accent] ?: SCHEMES.getValue(DEFAULT_ACCENT)
    val roles = if (dark) pair.dark else pair.light
    // The other theme's roles: what a snackbar or a scrim inverts to.
    val other = if (dark) pair.light else pair.dark

    val extra = ExtraColors(
        hairline = Color(roles.hairline),
        onSurfaceDisabled = Color(roles.onSurfaceDisabled),
        yellow = Color(if (dark) YELLOW_DARK else YELLOW_LIGHT),
    )

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialExpressiveTheme(
            colorScheme = roles.toColorScheme(other, dark),
            motionScheme = MotionScheme.expressive(),
            shapes = OlyGymShapes,
            typography = OlyGymTypography,
            content = content,
        )
    }
}

/**
 * Our roles onto Compose's slots. One accent is the app's rule, so the secondary and tertiary
 * families are echoes of it rather than a second and third hue — a stock M3 component can then
 * never paint a colour the design system did not choose.
 *
 * The *Fixed* roles are theme-invariant by definition. Our palette is theme-dependent, so they are
 * derived from the same roles as the rest; nothing in the app reads them.
 */
private fun Roles.toColorScheme(other: Roles, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(other.primary),
        secondary = Color(outline),
        onSecondary = Color(surface),
        secondaryContainer = Color(containerHigh),
        onSecondaryContainer = Color(onSurface),
        tertiary = Color(primary),
        onTertiary = Color(onPrimary),
        tertiaryContainer = Color(primaryContainer),
        onTertiaryContainer = Color(onPrimaryContainer),
        background = Color(surface),
        onBackground = Color(onSurface),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(containerHighest),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = Color(other.container),
        inverseOnSurface = Color(other.onSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(0xFF000000),
        surfaceBright = Color(containerHighest),
        surfaceDim = Color(surface),
        surfaceContainer = Color(container),
        surfaceContainerHigh = Color(containerHigh),
        surfaceContainerHighest = Color(containerHighest),
        surfaceContainerLow = Color(containerLow),
        surfaceContainerLowest = Color(surface),
        primaryFixed = Color(primaryContainer),
        primaryFixedDim = Color(primary),
        onPrimaryFixed = Color(onPrimaryContainer),
        onPrimaryFixedVariant = Color(onPrimaryContainer),
        secondaryFixed = Color(containerHigh),
        secondaryFixedDim = Color(container),
        onSecondaryFixed = Color(onSurface),
        onSecondaryFixedVariant = Color(onSurfaceVariant),
        tertiaryFixed = Color(primaryContainer),
        tertiaryFixedDim = Color(primary),
        onTertiaryFixed = Color(onPrimaryContainer),
        onTertiaryFixedVariant = Color(onPrimaryContainer),
    )
}

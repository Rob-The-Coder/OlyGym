package olygym.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * The app's own colour names, as the helpers carry them.
 *
 * lib/ returns colours as the CSS variable names the web writes ("var(--red)", "var(--acc)"), and
 * the two roles M3 owns are read from the scheme: --acc is the primary and --red is the error, in
 * both themes, by the generated tokens. The six that index.css defines per theme are constants here,
 * dark and light, exactly as index.css has them.
 *
 * This is what makes the effort ramp render at all: colorOf parsed only "#rrggbb", so every band
 * colour came back null and the cells fell back to a neutral container.
 */
private val DARK = mapOf(
    "blue" to 0xFF0A84FF, "green" to 0xFF30D158, "red" to 0xFFFF453A, "orange" to 0xFFFF9F0A,
    "yellow" to 0xFFFFD60A, "teal" to 0xFF40C8E0, "indigo" to 0xFF5E5CE6, "pink" to 0xFFFF375F,
    "purple" to 0xFFBF5AF2, "mint" to 0xFF63E6E2, "brown" to 0xFFAC8E68, "grey" to 0xFF8E8E93,
)

private val LIGHT = mapOf(
    "blue" to 0xFF007AFF, "green" to 0xFF34C759, "red" to 0xFFFF3B30, "orange" to 0xFFFF9500,
    "yellow" to 0xFFFFCC00, "teal" to 0xFF30B0C7, "indigo" to 0xFF5856D6, "pink" to 0xFFFF2D55,
    "purple" to 0xFFAF52DE, "mint" to 0xFF00C7BE, "brown" to 0xFFA2845E, "grey" to 0xFF8E8E93,
)

/** "#rrggbb", or the app's own named colour, or null. */
@Composable
@ReadOnlyComposable
fun appColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val name = value.removePrefix("var(").removeSuffix(")").removePrefix("--").trim()
    if (name != "acc" && name != "red" && name != "yellow" && name != "acc-2") {
        val hex = value.removePrefix("#")
        val argb = if (hex.length == 6) "FF" + hex else hex
        argb.toLongOrNull(16)?.let { return Color(it) }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (name) {
        "acc", "acc-2" -> MaterialTheme.colorScheme.primary
        "red" -> MaterialTheme.colorScheme.error
        "yellow" -> MaterialTheme.extraColors.yellow
        else -> (if (dark) DARK else LIGHT)[name]?.let { Color(it) }
    }
}

/**
 * The fatigue ramp (index.css's .hm-fatigue): red past 0.55, orange past 0.4, yellow past 0.25, and a
 * hint of yellow below that. It is a different scale from the accent ramp on purpose — "more" here
 * means "rest", not "trained".
 */
@Composable
@ReadOnlyComposable
fun fatigueLevelColor(level: Int): Color {
    val base = levelBase()
    val yellow = MaterialTheme.extraColors.yellow
    return when {
        level >= 4 -> MaterialTheme.colorScheme.error
        level == 3 -> Color(0xFFFF9F0A)
        level == 2 -> yellow
        level == 1 -> androidx.compose.ui.graphics.lerp(base, yellow, 0.32f)
        else -> base
    }
}

/** index.css's --surface-2: the second container step, and the base of the level ramp. */
@Composable
@ReadOnlyComposable
fun levelBase(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * The five-step ramp the activity heatmap and the muscle map share (the web's .hm-c.l0…l4): the
 * accent mixed into the container at 30%, 55% and 78%, then the accent itself. One accent scale
 * means "more training" everywhere in the app rather than two.
 */
@Composable
@ReadOnlyComposable
fun levelColor(level: Int): Color {
    val base = levelBase()
    val accent = MaterialTheme.colorScheme.primary
    val mix = when (level) {
        1 -> 0.30f
        2 -> 0.55f
        3 -> 0.78f
        else -> 1f
    }
    return if (level <= 0) base else androidx.compose.ui.graphics.lerp(base, accent, mix)
}

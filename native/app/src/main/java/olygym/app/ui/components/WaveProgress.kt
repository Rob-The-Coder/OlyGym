package olygym.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One full sine every 20dp — the tile the web app's mask repeats. */
private val WAVE_LENGTH = 20.dp

/** The mask's own stroke, from the web's SVG: 3.4 on a 6px bar. */
private val WAVE_STROKE = 3.4.dp

/** How long one wavelength takes to drift past, from the web's `m3flow`. */
private const val WAVE_PERIOD_MS = 2400

/**
 * M3 Expressive's wavy progress bar — the web app's `#timer .bar` and `.wprog`, which mask a
 * straight fill with a drifting sine. The web masks with a repeating SVG; the same hump is drawn
 * here, because a divider-less bar that flows reads as a countdown in a way a rectangle does not.
 *
 * Six is the height the web settled on: at four there is no room for a wave to read. The fill still
 * advances by width, so what the bar measures is exactly as accurate as it was as a plain fill, and
 * the *data* bars (the muscle balance) stay straight on purpose — this is the one bar in the app
 * that is decorative rather than informational.
 */
@Composable
fun WaveProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 6.dp,
) {
    val drift = rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing)),
        label = "wave-drift",
    )
    val filled = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track),
    ) {
        if (filled <= 0f) return@Box
        Canvas(Modifier.fillMaxWidth(filled).height(height)) {
            val wavelength = WAVE_LENGTH.toPx()
            val middle = size.height / 2f
            // Starting a whole wavelength to the left is what makes the drift seamless: at the end
            // of the cycle the path is exactly the one it started from.
            val start = -wavelength + drift.value * wavelength
            val path = Path()
            path.moveTo(start, middle)
            var x = start
            while (x < size.width + wavelength) {
                // The mask's hump: control at the bar's top, back to the middle half a wave later.
                path.quadraticBezierTo(x + wavelength / 4f, 0f, x + wavelength / 2f, middle)
                x += wavelength / 2f
            }
            drawPath(path, fill, style = Stroke(width = WAVE_STROKE.toPx(), cap = StrokeCap.Round))
        }
    }
}

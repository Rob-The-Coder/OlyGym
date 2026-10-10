package olygym.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import olygym.app.ui.theme.reduceMotion

/**
 * M3 Expressive's wavy progress bar, for the web app's `#timer .bar` and `.wprog`.
 *
 * The app used to draw the sine itself — one quadratic hump per half wavelength, drifting — because
 * the CSS does it with a masked SVG and the port matched it. Material ships the indicator, so this
 * is now a call to it: the same wave, on the scheme's own motion rather than this file's constant,
 * and one fewer thing in the app that Material already owns.
 *
 * Six is the height the web settled on; at four there is no room for a wave to read. The fill still
 * advances by width, so what the bar measures is exactly as accurate as it was as a plain rectangle,
 * and the *data* bars (the muscle balance) stay straight on purpose — this is the one bar in the app
 * that is decorative rather than informational.
 *
 * Under a reduced-motion request it becomes a plain linear indicator: the bar still fills and still
 * measures, it simply stops flowing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val value = { fraction.coerceIn(0f, 1f) }
    // The indicator sizes itself: its stroke and its amplitude together decide the height, and
    // constraining it forced the wave into a box too short to read — the first cut of this swap
    // rendered a bar that was there and almost invisible.
    val sized = modifier.fillMaxWidth()
    if (reduceMotion()) {
        LinearProgressIndicator(
            progress = value,
            modifier = sized,
            color = fill,
            trackColor = track,
        )
    } else {
        LinearWavyProgressIndicator(
            progress = value,
            modifier = sized,
            color = fill,
            trackColor = track,
            // The web's masked SVG strokes 3.4 on a 6px bar and settled there because 4 leaves no
            // room for the wave to read. Material's own default is the thinner one.
            stroke = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}

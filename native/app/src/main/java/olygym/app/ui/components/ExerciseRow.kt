package olygym.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.bestWeightFor
import olygym.app.lib.capWords
import olygym.app.lib.fmtNum
import olygym.app.ui.t

/**
 * One catalogue row: the demo picture place, the name, the line under it, and whatever the screen
 * puts at the end — a best weight, a Chosen tag and its plus, or nothing. The Library, its By-muscle
 * explorer and the picker all draw this same row, and each of the three used to carry its own copy.
 *
 * The thumb is the app own glyph until media lands: the web shows the exercise YouTube poster frame
 * here, which is its own phase.
 */
@Composable
fun ExerciseRow(
    ex: Exercise,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favourite: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(ex)
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (favourite) {
                    GlyphIcon(
                        Glyph.STAR_FILLED,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = capWords(Catalogue.nameOf(ex.id)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke(this)
    }
}

/** The best weight an exercise has ever moved, as the row's trailing pill — nothing when it has none. */
@Composable
fun BestWeightTag(S: JsonObject?, exId: String) {
    val best = bestWeightFor(S, exId)
    if (best > 0.0) Tag(fmtNum(best), accent = true)
}

/** The line under a catalogue row name: Muscle and Equipment, translated. */
fun exerciseSubtitle(ex: Exercise): String {
    val muscle = ex.tg?.let { MUSCLE_NAME[it] ?: it } ?: ex.bp
    return listOfNotNull(muscle?.let { t(it) }, ex.eq?.let { t(it) }).joinToString(" · ")
}

/** The picture place: the web poster frame, drawn as the app own glyph for now. */
@Composable
fun Thumb(ex: Exercise?) {
    val tint = if (ex == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (ex != null) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            if (ex == null) Glyph.SPARKLES else Glyph.DUMBBELL,
            Modifier.size(20.dp),
            tint = tint,
        )
    }
}

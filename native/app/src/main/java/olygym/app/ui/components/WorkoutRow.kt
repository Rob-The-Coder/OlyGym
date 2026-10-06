package olygym.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.lib.durPart
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtVol
import olygym.app.lib.setsDone
import olygym.app.ui.t

/**
 * One session in a list — a port of WorkoutRow in frontend/src/sheets.jsx: the date, how long it
 * ran, how many sets and how much volume, with the PR count when it earned one. A legacy workout
 * filed under a routine that no longer exists shows its own stored name, as on the web.
 */
@Composable
fun WorkoutRow(
    w: JsonObject,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val start = w.num("start") ?: 0.0
    val end = w.num("end") ?: start
    val sets = setsDone(w)
    val prs = (w["prs"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    val subtitle = listOfNotNull(
        w.str("d")?.let { fmtDate(it, long = true) },
        durPart((end - start).toLong()).firstOrNull(),
        t(if (sets == 1) "{0} set" else "{0} sets", sets),
        fmtVol(w.num("vol") ?: 0.0, unit),
    ).joinToString(" · ")
    ListRow(
        title = w.str("name").orEmpty(),
        icon = Glyph.DUMBBELL,
        subtitle = subtitle,
        accessory = Accessory.CHEVRON,
        modifier = modifier,
        onClick = onClick,
        trailing = if (prs > 0) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GlyphIcon(
                        Glyph.TROPHY,
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = prs.toString() + " PR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
    )
}

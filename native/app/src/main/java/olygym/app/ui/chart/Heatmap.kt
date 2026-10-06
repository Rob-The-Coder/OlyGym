package olygym.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import olygym.app.lib.activityByDay
import olygym.app.lib.activityGridStart
import olygym.app.lib.activityLevel
import olygym.app.lib.activityMonthLabels
import olygym.app.lib.activityThresholds
import olygym.app.lib.weekDayOffset
import olygym.app.ui.t
import olygym.app.ui.theme.levelBase
import olygym.app.ui.theme.levelColor

private val CELL = 11.dp
private val GAP = 3.dp

/**
 * GitHub-style activity heatmap — a port of frontend/src/components/Heatmap.jsx. 53 columns of whole
 * weeks, shaded by the time trained that day, with the month labelled where a column starts one.
 *
 * The web builds the grid as 53 columns and scrolls itself to the right on mount, because the newest
 * week is the one you read; this does the same.
 */
@Composable
fun Heatmap(
    workouts: JsonElement?,
    todayIso: String,
    weekStart: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onDay: ((String) -> Unit)? = null,
) {
    val byDay = activityByDay(workouts)
    val thresholds = activityThresholds(byDay)
    val start = activityGridStart(todayIso, weekStart)
    val labels = activityMonthLabels(start)
    val scroll = rememberScrollState()
    LaunchedEffect(labels) { scroll.scrollTo(scroll.maxValue) }
    val today = java.time.LocalDate.parse(todayIso)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            // The weekday column, then the weeks themselves.
            Column(Modifier.width(36.dp).padding(top = 17.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
                for (row in 0..6) {
                    val day = (0..6).firstOrNull { weekDayOffset(it, weekStart) == row }
                    val label = when (day) {
                        1 -> t("Mon")
                        3 -> t("Wed")
                        5 -> t("Fri")
                        else -> null
                    }
                    Box(Modifier.height(CELL), contentAlignment = Alignment.CenterStart) {
                        if (label != null) {
                            // One line, unclipped by the 11dp cell: "Mag" is three characters and
                            // wrapping it inside the cell printed it as "M a g".
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    labels.forEach { label ->
                        Box(Modifier.width(CELL), contentAlignment = Alignment.CenterStart) {
                            if (label != null) {
                                // The months span their own column and overflow into the next, as
                                // the web's do: three letters do not fit an 11dp cell.
                                Text(
                                    text = t(label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    for (col in 0 until 53) {
                        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                            for (row in 0..6) {
                                val date = start.plusDays(col.toLong() * 7 + row)
                                val iso = date.toString()
                                val day = byDay[iso]
                                val level = activityLevel(day, thresholds)
                                val isToday = iso == todayIso
                                val future = date.isAfter(today)
                                Box(
                                    Modifier
                                        .size(CELL)
                                        .alpha(if (future) 0.3f else 1f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (level <= 0) levelBase() else levelColor(level))
                                        .then(
                                            if (isToday) {
                                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .then(
                                            if (day != null && onDay != null) {
                                                Modifier.clickable { onDay(iso) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = t("Less time"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (level in 0..4) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (level == 0) levelBase() else levelColor(level)),
                )
            }
            Text(
                text = t("More time"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package olygym.app.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.str
import olygym.app.lib.ExerciseSession
import olygym.app.lib.exerciseHistory
import olygym.app.lib.exNoteFor
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.fmtVol
import olygym.app.lib.setLabel
import olygym.app.ui.chart.ChartPoint
import olygym.app.ui.chart.LineChart
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.theme.appColor
import olygym.app.ui.ui

/**
 * What you did on this exercise before (issue #43): the curve first, then the last sessions set by
 * set, so "what did I do last month" is answered without leaving for Stats. A port of the
 * ExerciseHistory sheet in frontend/src/sheets.jsx.
 *
 * The standing note is read and corrected here — it belongs to the movement, not to a session, and
 * this is the one place it can be fixed with nothing running.
 */
fun exerciseHistorySheet(exId: String) {
    ui.openSheet { close -> ExerciseHistorySheet(exId, close) }
}

@Composable
private fun ExerciseHistorySheet(exId: String, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val S = profile.raw
    // Read on each pass rather than memoised: the sheet is short-lived, and one scan of the log is
    // cheaper than comparing two state objects to decide whether to scan.
    val h = exerciseHistory(S, exId)
    val unit = when (h.metric) {
        "weight" -> profile.settings.unit
        "reps" -> t("reps")
        "sec" -> "s"
        else -> t("min")
    }
    val byId = S.arr("workouts").mapNotNull { it.asObj() }.associateBy { it.str("id").orEmpty() }
    val last = h.sessions.firstOrNull { (it.value ?: 0.0) > 0.0 }
    val prDay = h.prId?.let { byId[it]?.str("d") }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(Catalogue.nameOf(exId), capitalize = true)
        Text(
            text = t("Exercise history") + " · " +
                t(if (h.total == 1) "{0} session" else "{0} sessions", h.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        StandingNote(exNoteFor(S, exId)) { standingNoteSheet(exId) }

        if (h.total == 0) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlyphIcon(Glyph.HISTORY, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = t("No sessions logged yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                label = t("Best"),
                value = fmtNum(h.best) + " " + unit,
                suffix = prDay?.let { fmtDate(it, long = false) },
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = t("Last"),
                value = last?.value?.let { fmtNum(it) + " " + unit } ?: "—",
                suffix = last?.let { fmtDate(it.d, long = false) },
                modifier = Modifier.weight(1f),
            )
        }

        LineChart(
            points = h.points.map { ChartPoint(it.t, it.y, it.d) },
            modifier = Modifier.padding(top = 12.dp),
            height = 140.dp,
            unit = unit,
            color = appColor("var(--blue)") ?: MaterialTheme.colorScheme.primary,
        )

        Overline(
            t(if (h.sessions.size < h.total) "Last {0} sessions" else "Sessions", h.sessions.size),
            Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        h.sessions.forEach { session ->
            SessionRow(exId, session, byId[session.id], profile.settings.unit, unit)
        }
    }
}

/**
 * The cue that belongs to the movement, and the door to the sheet that writes it. Read-only here:
 * the block is the control, as in the web, because a cue you can see and not fix is worse than none.
 */
@Composable
private fun StandingNote(text: String?, onEdit: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable(onClick = onEdit),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = t("Every session"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlyphIcon(Glyph.PENCIL, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = text ?: t("Seat height, pin position, a form cue."),
                style = MaterialTheme.typography.bodyMedium,
                color = if (text != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** One session: its date and PR mark, its sets labelled by their own target, and its value. */
@Composable
private fun SessionRow(
    exId: String,
    session: ExerciseSession,
    workout: JsonObject?,
    bodyUnit: String,
    unit: String,
) {
    // A session you are looking at is a session you can open, when the log still has it.
    val open = if (workout != null) {
        Modifier.clickable { workoutDetailSheet(workout) }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(open)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = fmtDate(session.d, long = true),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (session.pr) {
                    GlyphIcon(Glyph.TROPHY, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = t("PR"),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = session.sets.joinToString("  ·  ") { setLabel(exId, it, session.target) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val volume = session.volume?.takeIf { it > 0.0 }
            if (volume != null) {
                Text(
                    text = t("Volume") + " " + fmtVol(volume, bodyUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        val value = session.value?.takeIf { it > 0.0 }
        if (value != null) {
            Text(
                text = fmtNum(value) + " " + unit,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (workout != null) {
            GlyphIcon(
                Glyph.CHEVRON_RIGHT,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                stroke = 2.2f,
            )
        }
    }
}

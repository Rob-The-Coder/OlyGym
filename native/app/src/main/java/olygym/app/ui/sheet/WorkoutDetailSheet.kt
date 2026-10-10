package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.NOTE_MAX
import olygym.app.lib.capWords
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtDur
import olygym.app.lib.fmtNum
import olygym.app.lib.fmtVol
import olygym.app.lib.hasCompletedWork
import olygym.app.lib.setLabel
import olygym.app.lib.setsDone
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * One logged session, read back — a port of WorkoutDetail in frontend/src/sheets.jsx: the four
 * numbers the finish summary shows, every entry with the sets it actually did, its note, and the
 * delete behind the ⋯ menu.
 *
 * Not ported: the per-routine grouping of legacy workouts (a workout that listed several routines
 * and stamped each entry's rid). Every workout since the dated-weeks model is one day, so it reads
 * as the flat list the web also uses for those; the group headers are the only thing missing, and
 * only for data written before 1a.
 */
fun workoutDetailSheet(w: JsonObject): Long = ui.openSheet { close -> WorkoutDetail(w, close) }

@Composable
private fun WorkoutDetail(w: JsonObject, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    // Read the record fresh: the sheet outlives an edit made underneath it.
    val id = w.str("id").orEmpty()
    val live = profile.workouts.lastOrNull { it.str("id") == id } ?: w
    val name = live.str("name").orEmpty()
    val prs = live.arr("prs").mapNotNull { it.asStr() }
    var note by remember(id) { mutableStateOf(live.str("note").orEmpty()) }
    val start = live.num("start") ?: 0.0
    val end = live.num("end") ?: start

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // The last full-width destructive button in the module became a menu, as on the week,
            // the day, the config and the exercise detail.
            IconButton(Glyph.MORE, t("More"), onClick = {
                menuSheet(
                    title = name,
                    items = listOf(
                        MenuItem(
                            label = t("Delete workout"),
                            icon = Glyph.TRASH,
                            danger = true,
                            onClick = {
                                confirmSheet(
                                    title = t("Delete workout?"),
                                    message = t("This removes it from your history for good."),
                                    confirmText = t("Delete"),
                                    danger = true,
                                    onConfirm = {
                                        editProfile { raw ->
                                            raw.with(
                                                "workouts",
                                                JsonArray(raw.arr("workouts").filterNot { it.asObj()?.str("id") == id }),
                                            )
                                        }
                                        close()
                                        ui.toast(t("Workout deleted"))
                                    },
                                )
                            },
                        ),
                    ),
                )
            })
        }
        Text(
            text = listOfNotNull(
                live.str("d")?.let { fmtDate(it, long = true) },
                live.num("bw")?.takeIf { it > 0.0 }?.let { fmtNum(it) + " " + unit },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // Two by two: the web's .tiles grid on a phone. Four across leaves "1h 22m" and
        // "4.132,5 kg" cut off mid-value.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                label = t("Duration"),
                value = fmtDur((end - start).toLong()),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = t("Volume"),
                value = fmtVol(live.num("vol") ?: 0.0, unit),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                label = t("Sets"),
                value = setsDone(live).toString(),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = t("PRs"),
                value = if (prs.isEmpty()) "—" else prs.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        live.arr("entries").forEachIndexed { _, element ->
            val entry = element.asObj() ?: return@forEachIndexed
            val exId = entry.str("id").orEmpty()
            val known = Catalogue[exId] != null
            val labels = entry.arr("sets")
                .filter { hasCompletedWork(it) }
                .map { setLabel(exId, it, entry.obj("target")) }
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (known) capWords(Catalogue.nameOf(exId)) else (entry.str("n") ?: exId),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (prs.contains(exId)) {
                        Text(
                            text = "PR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = labels.joinToString("  ·  ").ifEmpty { t("no sets") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val entryNote = entry.str("note")
                if (!entryNote.isNullOrBlank()) {
                    Text(
                        text = entryNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        Overline(t("Note"), Modifier.padding(top = 18.dp, bottom = 6.dp))
        NoteField(
            value = note,
            onChange = { text ->
                note = text
                editProfile { raw ->
                    raw.with(
                        "workouts",
                        JsonArray(
                            raw.arr("workouts").map { element ->
                                val rec = element.asObj() ?: return@map element
                                if (rec.str("id") != id) {
                                    element
                                } else {
                                    val trimmed = text.trim().take(NOTE_MAX)
                                    if (trimmed.isEmpty()) rec.without("note") else rec.with("note", trimmed)
                                }
                            },
                        ),
                    )
                }
            },
            placeholder = t("How did it go?"),
            max = NOTE_MAX,
        )
        Button(
            text = t("Done"),
            onClick = close,
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

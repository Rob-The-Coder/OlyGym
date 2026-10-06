package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.bool
import olygym.app.data.editArray
import olygym.app.data.editObject
import olygym.app.data.editAt
import olygym.app.data.obj
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.NOTE_MAX
import olygym.app.lib.capWords
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Section
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * The three note sheets. All three are a field and a Save, and they differ only in where the text
 * lands: one exercise in today's session, the session as a whole, or the session's name.
 */

/** The note that belongs to one exercise of the running session, plus the cue that outlives it. */
fun exerciseNoteSheet(entryIdx: Int) {
    ui.openSheet { close -> ExerciseNoteSheet(entryIdx, close) }
}

@Composable
private fun ExerciseNoteSheet(entryIdx: Int, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val entry = profile.entries.getOrNull(entryIdx) ?: return
    val id = entry.str("id").orEmpty()
    val ex = olygym.app.data.Catalogue[id]

    var note by remember { mutableStateOf(entry.str("note").orEmpty()) }
    var pin by remember { mutableStateOf(entry.bool("notePin") == true) }
    var standing by remember { mutableStateOf(profile.raw.obj("exNotes")?.str(id).orEmpty()) }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(Catalogue.nameOf(id), capitalize = true)

        FieldLabel(
            t("This session"),
            t("Kept with today’s workout — what happened, how it felt."),
        )
        NoteField(
            value = note,
            onChange = { note = it },
            placeholder = t("How it went, what to change."),
            minLines = 3,
            max = NOTE_MAX,
        )
        Section(modifier = Modifier.padding(top = 10.dp)) {
            ListRow(
                title = t("Show this next time"),
                icon = Glyph.FLAG,
                subtitle = t("Brings it up again the next time you train this exercise."),
                trailing = {
                    Switch(
                        checked = pin,
                        onCheckedChange = { pin = it },
                        enabled = note.isNotBlank(),
                    )
                },
            )
        }

        FieldLabel(
            t("Every session"),
            t("Shown every time you train this exercise — seat height, pin position, a form cue."),
        )
        NoteField(
            value = standing,
            onChange = { standing = it },
            placeholder = t("Seat height, pin position, a form cue."),
            minLines = 2,
            max = NOTE_MAX,
        )

        Button(
            text = t("Save"),
            onClick = {
                saveNotes(entryIdx, id, note, pin, standing)
                close()
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/**
 * Both notes in one write: the session's note is written through the entry index, so a session that
 * moved under the sheet still lands on the entry it was opened for, and the standing note belongs
 * to the exercise id.
 */
private fun saveNotes(entryIdx: Int, id: String, note: String, pin: Boolean, standing: String) {
    val today = note.trim().take(NOTE_MAX)
    val always = standing.trim().take(NOTE_MAX)
    editProfile { raw ->
        val withEntry = raw.editObject("active") { active ->
            active.editArray("entries") { entries ->
                entries.editAt(entryIdx) { entry ->
                    if (today.isNotEmpty()) {
                        val withPin = if (pin) entry.with("notePin", true) else entry.without("notePin")
                        withPin.with("note", today)
                    } else {
                        entry.without("note").without("notePin")
                    }
                }
            }
        }
        // The web assigns s.exNotes = s.exNotes || {} whether or not it writes a note, so the key
        // survives a cleared one.
        val exNotes = withEntry.obj("exNotes") ?: JsonObject(emptyMap())
        withEntry.with("exNotes", if (always.isNotEmpty()) exNotes.with(id, always) else exNotes.without(id))
    }
}

/** How the whole workout went. Carried onto the finished workout by buildCompletedWorkout. */
fun sessionNoteSheet() {
    ui.openSheet { close -> SessionNoteSheet(close) }
}

@Composable
private fun SessionNoteSheet(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val active = profile.active ?: return
    var note by remember { mutableStateOf(active.str("note").orEmpty()) }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Session note"))
        FieldLabel(
            t("The session"),
            t("How the whole workout went. Kept with today’s workout, and editable from History afterwards."),
        )
        NoteField(
            value = note,
            onChange = { note = it },
            placeholder = t("How the session went as a whole."),
            minLines = 4,
            max = NOTE_MAX,
        )
        Button(
            text = t("Save"),
            onClick = {
                val text = note.trim().take(NOTE_MAX)
                editProfile { raw ->
                    raw.editObject("active") { a ->
                        if (text.isNotEmpty()) a.with("note", text) else a.without("note")
                    }
                }
                close()
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/** The session's name, as it will read in the training log. */
fun renameWorkoutSheet() {
    ui.openSheet { close -> RenameWorkoutSheet(close) }
}

@Composable
private fun RenameWorkoutSheet(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val active = profile.active ?: return
    var name by remember { mutableStateOf(active.str("name").orEmpty()) }
    val trimmed = name.trim().take(60)
    val canSave = trimmed.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Rename workout"))
        NoteField(
            value = name,
            onChange = { name = it },
            placeholder = t("Workout title"),
            singleLine = true,
            max = 60,
        )
        Button(
            text = t("Save"),
            onClick = {
                if (!canSave) return@Button
                editProfile { raw ->
                    raw.editObject("active") { a -> a.with("name", trimmed).with("customName", true) }
                }
                close()
            },
            variant = ButtonVariant.PRIMARY,
            enabled = canSave,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

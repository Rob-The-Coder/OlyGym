package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.editArray
import olygym.app.data.editAt
import olygym.app.data.js
import olygym.app.data.asStr
import olygym.app.data.obj
import olygym.app.data.objectAt
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.MUSCLES
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.allEquipment
import olygym.app.lib.allExercises
import olygym.app.lib.bodyParts
import olygym.app.lib.capWords
import olygym.app.lib.cleanupSg
import olygym.app.lib.exerciseMuscleSnapshot
import olygym.app.lib.hasExplicitMuscleMetadata
import olygym.app.lib.inMuscleOrder
import olygym.app.lib.normalizeMuscleGroups
import olygym.app.lib.uid
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.RowDivider
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * The user's own exercises: a name, a body part, equipment and the muscles it hits. A port of
 * CustomExForm in frontend/src/sheets.jsx.
 *
 * The write goes into the raw S.customEx objects rather than through the data class, so the muscle
 * metadata this sheet does not own — and anything a later build added — survives an edit. The
 * catalogue index is rebuilt from them on the next profile read, which is what makes a day that
 * references a custom show its name.
 */
fun customExSheet(existing: Exercise? = null, onDone: ((Exercise) -> Unit)? = null, prefill: String = "") {
    ui.openSheet { close -> CustomExSheet(existing, onDone, prefill, close) }
}

@Composable
private fun CustomExSheet(
    existing: Exercise?,
    onDone: ((Exercise) -> Unit)?,
    prefill: String,
    close: () -> Unit,
) {
    val profile = currentProfile() ?: return
    val rawExisting = existing?.let { ex ->
        profile.raw.arr("customEx").mapNotNull { it.asObj() }.firstOrNull { it.str("id") == ex.id }
    }
    var name by remember { mutableStateOf(existing?.n ?: prefill) }
    var bodyPart by remember { mutableStateOf(existing?.bp.orEmpty()) }
    var equipment by remember { mutableStateOf(existing?.eq.orEmpty()) }
    var description by remember { mutableStateOf(existing?.desc.orEmpty()) }
    // An existing exercise seeds the two muscle lists from its own metadata, in the map's order.
    val seeded = remember {
        val norm = if (rawExisting != null && hasExplicitMuscleMetadata(rawExisting)) {
            normalizeMuscleGroups(rawExisting)
        } else {
            emptyList()
        }
        norm
    }
    val primaries = remember {
        mutableStateListOf(*(existing?.primaries?.takeIf { it.isNotEmpty() } ?: seeded.take(1)).toTypedArray())
    }
    val secondaries = remember {
        mutableStateListOf(
            *(existing?.secondaries?.takeIf { it.isNotEmpty() } ?: seeded.drop(1)).toTypedArray()
        )
    }
    // Every primary this sheet saw a tap on, in that order: it decides the one-word target when the
    // muscle the exercise had is un-ticked.
    val taps = remember { mutableStateListOf<String>() }

    val bodyPartsList = bodyParts(Catalogue.list)
    val equipmentList = allEquipment(Catalogue.list)
    val actions = if (existing != null) {
        listOf(
            MenuItem(
                label = t("Delete exercise"),
                icon = Glyph.TRASH,
                danger = true,
                onClick = {
                    close()
                    deleteCustomEx(existing)
                },
            ),
        )
    } else {
        emptyList()
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = if (existing != null) t("Edit custom exercise") else t("Create your own exercise"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actions.isNotEmpty()) {
                IconButton(Glyph.MORE, onClick = {
                    menuSheet(title = name.trim().ifEmpty { t("Exercise") }, items = actions)
                })
            }
        }
        SheetNote(t("Name it and pick a body part — it behaves like any other exercise, just without a demo video."))

        NoteField(
            value = name,
            onChange = { name = it },
            placeholder = t("Exercise name"),
            singleLine = true,
            max = 60,
        )

        Column(Modifier.padding(top = 12.dp)) {
            SelectRow(
                title = t("Body part"),
                value = bodyPart,
                sheetTitle = t("Body part"),
                options = listOf(SelectOption("", t("Choose…"))) +
                    pickable(bodyPartsList, bodyPart),
                onChange = { bodyPart = it },
            )
            RowDivider()
            SelectRow(
                title = t("Equipment"),
                value = equipment,
                sheetTitle = t("Equipment"),
                options = listOf(SelectOption("", t("Choose…"))) +
                    pickable(equipmentList, equipment),
                onChange = { equipment = it },
            )
            if (bodyPart.isNotEmpty()) {
                RowDivider()
                MultiSelectRow(
                    title = t("Primary muscle groups"),
                    values = primaries,
                    options = MUSCLES.map { SelectOption(it, t(MUSCLE_NAME[it] ?: it)) },
                    onToggle = { value ->
                        if (!taps.contains(value)) taps.add(value)
                        if (primaries.contains(value)) primaries.remove(value) else primaries.add(value)
                    },
                    noneLabel = t("No explicit muscle group"),
                )
                RowDivider()
                MultiSelectRow(
                    title = t("Additional muscle groups"),
                    values = secondaries,
                    options = MUSCLES.filter { !primaries.contains(it) }.map { SelectOption(it, t(MUSCLE_NAME[it] ?: it)) },
                    onToggle = { value ->
                        if (secondaries.contains(value)) secondaries.remove(value) else secondaries.add(value)
                    },
                    noneLabel = t("No explicit muscle group"),
                )
            }
        }

        NoteField(
            value = description,
            onChange = { description = it },
            placeholder = t("Description (optional) — setup, cues, anything you want to remember"),
            minLines = 4,
            max = 1000,
            modifier = Modifier.padding(top = 12.dp),
        )

        Button(
            text = if (existing != null) t("Save") else t("Create exercise"),
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    ui.toast(t("Give it a name"))
                    return@Button
                }
                if (bodyPart.isEmpty()) {
                    ui.toast(t("Pick a body part"))
                    return@Button
                }
                if (equipment.isEmpty()) {
                    ui.toast(t("Pick equipment"))
                    return@Button
                }
                val duplicate = allExercises(profile.customEx).firstOrNull {
                    it.n.lowercase() == trimmed.lowercase() && it.id != existing?.id
                }
                if (duplicate != null) {
                    ui.toast(t("“{0}” already exists", duplicate.n))
                    return@Button
                }
                // Stored in the map's order, not the order the chips were tapped in: the tags used to
                // shuffle with every edit.
                val prim = inMuscleOrder(primaries)
                val secondary = inMuscleOrder(secondaries.filter { !prim.contains(it) })
                val groups = prim + secondary
                // The one-word target is the primary tapped first, keeping the exercise's own target
                // as long as that muscle is still a primary.
                val target = if (existing != null && existing.tg != null && prim.contains(existing.tg)) {
                    existing.tg
                } else {
                    taps.firstOrNull { prim.contains(it) } ?: prim.firstOrNull() ?: ""
                }
                val id = existing?.id ?: ("c" + uid())
                saveCustomExercise(
                    id = id,
                    rawExisting = rawExisting,
                    name = trimmed,
                    bodyPart = bodyPart,
                    equipment = equipment,
                    description = description.trim().take(1000),
                    target = target,
                    primaries = prim,
                    secondaries = secondary,
                    groups = groups,
                )
                close()
                ui.toast(if (existing != null) t("Saved") else t("“{0}” created", trimmed))
                onDone?.invoke(Catalogue[id] ?: Exercise(id = id, n = trimmed))
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** The options a select offers: the catalogue's own values, plus the one currently stored. */
internal fun pickable(values: List<String>, current: String): List<SelectOption> {
    val all = if (current.isNotEmpty() && !values.contains(current)) values + current else values
    return all.map { SelectOption(it, capWords(it)) }
}

private fun saveCustomExercise(
    id: String,
    rawExisting: JsonObject?,
    name: String,
    bodyPart: String,
    equipment: String,
    description: String,
    target: String,
    primaries: List<String>,
    secondaries: List<String>,
    groups: List<String>,
) {
    val row = js(
        "id" to id,
        "n" to name,
        "bp" to bodyPart,
        "eq" to equipment,
        "desc" to description,
        "tg" to target,
        "sm" to secondaries,
        "muscleGroups" to groups,
        "primaries" to primaries,
        "secondaries" to secondaries,
        "custom" to true,
    )
    editProfile { raw ->
        val list = raw.arr("customEx")
        val at = list.indexOfFirst { it.asObj()?.str("id") == id }
        if (at >= 0) {
            // An edit changes the fields the form owns and leaves the rest of the object alone.
            val existing = list.objectAt(at) ?: return@editProfile raw
            val merged = JsonObject(existing.toMutableMap().apply { putAll(row) })
            raw.with("customEx", list.editAt(at) { merged })
        } else {
            raw.with("customEx", JsonArray(list + row))
        }
    }
}

/**
 * Deleting one of your own exercises: gone from the routines and the favourites, its sets kept in
 * the workouts that already logged them, with the muscle snapshot taken before the catalogue entry
 * disappears.
 */
fun deleteCustomEx(ex: Exercise, afterDelete: (() -> Unit)? = null) {
    val profile = olygym.app.ui.profileNow()
    if (profile?.active?.let { active -> active.arr("entries").any { it.asObj()?.str("id") == ex.id } } == true) {
        ui.toast(t("Finish your current workout first"))
        return
    }
    confirmSheet(
        title = t("Delete “{0}”?", ex.n),
        message = t("It will be removed from your routines. Already-logged workouts keep their sets."),
        confirmText = t("Delete"),
        danger = true,
        onConfirm = {
            val snapshot = exerciseMuscleSnapshot(js("id" to ex.id, "n" to ex.n, "tg" to ex.tg, "sm" to ex.sm))
            editProfile { raw ->
                var next = raw
                // Keep the display name and the muscle metadata in the history before the catalogue
                // row goes.
                next = next.with(
                    "workouts",
                    JsonArray(
                        next.arr("workouts").map { workout ->
                            val w = workout.asObj() ?: return@map workout
                            w.with(
                                "entries",
                                JsonArray(
                                    w.arr("entries").map { entry ->
                                        val e = entry.asObj() ?: return@map entry
                                        if (e.str("id") != ex.id) {
                                            entry
                                        } else {
                                            val withName = e.with("n", ex.n)
                                            if (e.obj("muscleSnapshot")?.isNotEmpty() == true) {
                                                withName
                                            } else {
                                                withName.with("muscleSnapshot", snapshot)
                                            }
                                        }
                                    }
                                ),
                            )
                        }
                    ),
                )
                next = next.with("customEx", JsonArray(next.arr("customEx").filterNot { it.asObj()?.str("id") == ex.id }))
                next = next.with(
                    "weeks",
                    JsonArray(
                        next.arr("weeks").map { week ->
                            val w = week.asObj() ?: return@map week
                            w.with(
                                "days",
                                JsonArray(
                                    w.arr("days").map { day ->
                                        val d = day.asObj() ?: return@map day
                                        d.with("ex", cleanupSg(JsonArray(d.arr("ex").filterNot { it.asObj()?.str("id") == ex.id })))
                                    }
                                ),
                            )
                        }
                    ),
                )
                val weights = next.obj("exWeights") ?: JsonObject(emptyMap())
                next = next.with("exWeights", weights.without(ex.id))
                next.with("favEx", JsonArray(next.arr("favEx").filterNot { it.asStr() == ex.id }))
            }
            ui.toast(t("Exercise deleted"))
            afterDelete?.invoke()
        },
    )
}

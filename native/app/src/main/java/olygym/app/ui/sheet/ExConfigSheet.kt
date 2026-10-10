package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.lib.MAX_PLANNED_WARMUPS
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.NOTE_MAX
import olygym.app.lib.POLICY_DESC
import olygym.app.lib.POLICY_NAME
import olygym.app.lib.POLICIES_FOR
import olygym.app.lib.defaultConfig
import olygym.app.lib.defaultPolicy
import olygym.app.lib.exOr
import olygym.app.lib.isBodyweightEq
import olygym.app.lib.isBw
import olygym.app.lib.modeOf
import olygym.app.lib.policyFor
import olygym.app.lib.progressionStepIsValid
import olygym.app.lib.progressionStepOf
import olygym.app.lib.smOf
import olygym.app.lib.usesBar
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Section
import olygym.app.ui.components.Segmented
import olygym.app.ui.components.Stepper
import olygym.app.ui.components.Tag
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.theme.extraColors
import olygym.app.ui.ui

/*
 * The exercise config: what an exercise is set to do, and how it progresses. A port of ExConfig in
 * frontend/src/sheets.jsx, used by both flows — "Add exercise" and the ⋯ menu's progression
 * settings during a session.
 *
 * Not ported: the demo media at the top of the sheet (its own phase), and the "remove from routine"
 * action, which belongs to the plan editor. The ⋯ menu keeps the one action that applies here —
 * editing or deleting an exercise of your own.
 */
fun exConfigSheet(
    ex: Exercise?,
    existing: JsonObject? = null,
    onSave: (JsonObject) -> Unit,
    onDelete: (() -> Unit)? = null,
    routine: JsonObject? = null,
    initial: JsonObject? = null,
) {
    ui.openSheet { close -> ExConfigSheet(ex, existing, onSave, onDelete, routine, initial, close) }
}

@Composable
private fun ExConfigSheet(
    ex: Exercise?,
    existing: JsonObject?,
    onSave: (JsonObject) -> Unit,
    onDelete: (() -> Unit)?,
    routine: JsonObject?,
    initial: JsonObject?,
    close: () -> Unit,
) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    // A caller can hand us an id the catalogue no longer resolves — a plan written against another
    // dataset, or an exercise deleted on another device. exOr keeps the sheet usable.
    val id = ex?.id ?: existing?.str("id") ?: initial?.str("id") ?: ""
    val resolved = ex ?: Catalogue[id] ?: exOr(id)
    val seed = existing ?: initial ?: defaultConfig(id)
    var c by remember { mutableStateOf(seed) }

    val cfg = c.with("id", id)
    val mode = modeOf(cfg)
    val bw = isBw(cfg)
    val policy = policyFor(cfg, routine, mode, defaultPolicy(profile.raw))
    val stepInvalid = !progressionStepIsValid(progressionStepOf(c, mode, id), policy)
    // Keep whatever the other mode already had and fill only what is missing.
    val setMode = { m: String ->
        // The other mode's values stay, and only the mode itself is overwritten.
        val merged = (defaultConfig(id, m)).toMutableMap()
        merged.putAll(c)
        merged["mode"] = kotlinx.serialization.json.JsonPrimitive(m)
        c = JsonObject(merged)
    }
    val actions = listOfNotNull(
        if (resolved.custom) {
            MenuItem(
                label = t("Edit or delete this exercise"),
                icon = Glyph.PENCIL,
                onClick = {
                    close()
                    customExSheet(resolved)
                },
            )
        } else {
            null
        },
        onDelete?.let {
            MenuItem(label = t("Remove from routine"), icon = Glyph.TRASH, danger = true, onClick = it)
        },
    )

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = Catalogue.nameOf(id),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actions.isNotEmpty()) {
                IconButton(Glyph.MORE, t("More"), onClick = {
                    menuSheet(
                        title = Catalogue.nameOf(id),
                        subtitle = t("This routine entry"),
                        items = actions,
                    )
                })
            }
        }

        // The same tags the detail sheet shows, secondaries included: choosing what goes into a plan
        // is exactly when "what else does this hit" matters.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            resolved.bp?.let { Tag(t(it), accent = true) }
            (if (resolved.primaries.isNotEmpty()) resolved.primaries else listOfNotNull(resolved.tg))
                .forEach { Tag(t(MUSCLE_NAME[it] ?: it)) }
            resolved.eq?.let { Tag(t(it)) }
            (if (resolved.secondaries.isNotEmpty()) resolved.secondaries else smOf(resolved))
                .take(3)
                .forEach { Tag(t(MUSCLE_NAME[it] ?: it)) }
        }
        resolved.desc?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Overline(t("The set"), Modifier.padding(bottom = 6.dp))
        Segmented(
            options = listOf("reps", "time"),
            labels = listOf(t("Reps"), t("Time")),
            value = mode,
            onChange = setMode,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Stepper(
            label = t("Sets"),
            value = c.num("sets") ?: 3.0,
            step = 1.0,
            decimal = false,
            onChange = { c = c.with("sets", it) },
        )
        if (mode == "time") {
            Stepper(
                label = t("Seconds"),
                value = c.num("sec") ?: 45.0,
                step = 5.0,
                decimal = false,
                onChange = { c = c.with("sec", it) },
                modifier = Modifier.padding(top = 8.dp),
            )
            Stepper(
                label = t("Weight ({0})", unit),
                value = c.num("weight") ?: 0.0,
                step = 2.5,
                onChange = { c = c.with("weight", it) },
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Stepper(
                label = t("Reps"),
                value = c.num("reps") ?: 10.0,
                step = 1.0,
                decimal = false,
                onChange = { c = c.with("reps", it) },
                modifier = Modifier.padding(top = 8.dp),
            )
            // On bodyweight work the weight stepper is the field #32 is about, so it is not here
            // until there is a belt to describe — see the added-weight row below.
            if (!bw) {
                Stepper(
                    label = t("Weight ({0})", unit),
                    value = c.num("weight") ?: 0.0,
                    step = 2.5,
                    onChange = { c = c.with("weight", it) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Planned warm-ups and per-exercise rest: the two fields nobody guesses, in the same control
        // as the three above rather than full width with a paragraph each.
        Stepper(
            label = t("Warm-up sets"),
            value = c.num("warmupSets") ?: 0.0,
            step = 1.0,
            decimal = false,
            onChange = { c = c.with("warmupSets", maxOf(0.0, minOf(MAX_PLANNED_WARMUPS.toDouble(), Math.round(it).toDouble()))) },
            modifier = Modifier.padding(top = 12.dp),
        )
        Stepper(
            label = t("Rest (s)"),
            value = c.num("restSec") ?: 0.0,
            step = 15.0,
            decimal = false,
            onChange = { c = c.with("restSec", it) },
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = (if ((c.num("warmupSets") ?: 0.0) > 0) {
                t("Warm-ups stay out of volume, records and progression.")
            } else {
                t("Ramp-up sets are added before the work sets.")
            }) + " " + (if ((c.num("restSec") ?: 0.0) > 0) {
                t("Rest runs after each set here.")
            } else {
                t("Rest at 0 uses your default timer.")
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (mode == "time" && !bw) {
            Text(
                text = t("A timer runs while you hold the set. Leave the weight at 0 for bodyweight holds."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Overline(t("Bodyweight"), Modifier.padding(top = 16.dp, bottom = 6.dp))
        Section(modifier = Modifier.padding(bottom = 8.dp)) {
            ListRow(
                title = t("Bodyweight"),
                icon = Glyph.DUMBBELL,
                subtitle = if (bw) {
                    t("No weight to enter — just log the reps.")
                } else {
                    t("Ask for a weight on every set.")
                },
                trailing = {
                    Switch(
                        checked = bw,
                        onCheckedChange = { on ->
                            c = c.with("bodyweight", on).with("weight", if (on) 0.0 else (c.num("weight") ?: 0.0))
                        },
                    )
                },
            )
        }
        if (bw) {
            Stepper(
                label = t("Added ({0})", unit),
                value = c.num("weight") ?: 0.0,
                step = 2.5,
                onChange = { c = c.with("weight", it) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = t("For dips or pull-ups with a belt. Progression then follows the weight."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // The bar's own weight, for the plate maths — per exercise, not per plan.
        if (usesBar(js("id" to id, "eq" to resolved.eq))) {
            Overline(t("Bar"), Modifier.padding(bottom = 6.dp))
            BarWeightEditor(id, t("Applies to this exercise everywhere, not just this plan."))
        }

        ProgressionFields(
            exId = id,
            mode = mode,
            c = c,
            routine = routine,
            unit = unit,
            fallback = defaultPolicy(profile.raw),
            onChange = { c = it },
        )

        Overline(t("Note"), Modifier.padding(top = 16.dp, bottom = 6.dp))
        NoteField(
            value = c.str("note").orEmpty(),
            onChange = { c = c.with("note", it) },
            placeholder = t("Loading cues, anything worth remembering here"),
            minLines = 3,
            max = NOTE_MAX,
        )

        Button(
            text = if (existing != null) t("Save") else t("Add to routine"),
            onClick = {
                if (stepInvalid) return@Button
                close()
                onSave(buildConfig(c, id, mode, bw))
            },
            variant = ButtonVariant.PRIMARY,
            enabled = !stepInvalid,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/**
 * The config the sheet hands back: only the keys that have something to say, so a plan file stays
 * readable and "follow the routine" keeps meaning exactly that.
 */
private fun buildConfig(c: JsonObject, id: String, mode: String, bw: Boolean): JsonObject {
    var out = JsonObject(emptyMap())
    c.num("sets")?.let { out = out.with("sets", maxOf(1, Math.round(it).toInt())) }
    if (mode == "time") {
        out = out.with("mode", "time")
        out = out.with("sec", maxOf(1, Math.round(c.num("sec") ?: 45.0).toInt()))
    } else {
        out = out.with("mode", "reps")
        out = out.with("reps", maxOf(1, Math.round(c.num("reps") ?: 10.0).toInt()))
    }
    out = out.with("weight", maxOf(0.0, c.num("weight") ?: 0.0))
    // Written only when it differs from what the dataset already says.
    if (bw != isBodyweightEq(id)) out = out.with("bodyweight", bw)
    c.str("prog")?.takeIf { it.isNotEmpty() }?.let { out = out.with("prog", it) }
    val inc = c.num("inc") ?: 0.0
    if (inc > 0) out = out.with("inc", inc)
    val note = c.str("note").orEmpty().trim().take(NOTE_MAX)
    if (note.isNotEmpty()) out = out.with("note", note)
    val warmups = maxOf(0, minOf(MAX_PLANNED_WARMUPS, Math.round(c.num("warmupSets") ?: 0.0).toInt()))
    if (warmups > 0) out = out.with("warmupSets", warmups)
    val restSec = maxOf(0, Math.round(c.num("restSec") ?: 0.0).toInt())
    if (restSec > 0) out = out.with("restSec", restSec)
    return out
}

/**
 * How this exercise's load goes up. Left on "follow the routine" it inherits the profile's own
 * setting, so most people never touch it — issue #17.
 */
@Composable
private fun ProgressionFields(
    exId: String,
    mode: String,
    c: JsonObject,
    routine: JsonObject?,
    unit: String,
    fallback: String,
    onChange: (JsonObject) -> Unit,
) {
    val options = POLICIES_FOR[mode] ?: listOf("off")
    if (options.size < 2) return
    val inherited = policyFor(js("id" to exId), routine, mode, fallback)
    val active = policyFor(c.with("id", exId), routine, mode, fallback)
    val step = progressionStepOf(c, mode, exId)
    val invalid = !progressionStepIsValid(step, active)

    Overline(t("Progression"), Modifier.padding(top = 16.dp, bottom = 6.dp))
    Section(modifier = Modifier.padding(bottom = 8.dp)) {
        SelectRow(
            title = t("Rule"),
            value = c.str("prog").orEmpty(),
            sheetTitle = t("Progression"),
            options = listOf(
                SelectOption("", t("Follow the routine ({0})", t(POLICY_NAME[inherited] ?: "off")))
            ) + options.map { SelectOption(it, t(POLICY_NAME[it] ?: it)) },
            onChange = { value -> onChange(c.with("prog", value.ifEmpty { null })) },
        )
    }
    Text(
        text = t(POLICY_DESC[active] ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = if (active == "off") 18.dp else 10.dp),
    )
    if (active != "off") {
        Stepper(
            label = if (mode == "time") t("Step (seconds)") else t("Step ({0})", unit),
            value = step,
            step = if (mode == "time") 5.0 else 1.25,
            decimal = mode != "time",
            onChange = { onChange(c.with("inc", it)) },
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
    if (invalid) {
        Text(
            text = t("Enter a positive step to use this progression rule."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}


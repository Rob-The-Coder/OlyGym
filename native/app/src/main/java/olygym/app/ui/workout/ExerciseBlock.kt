package olygym.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.lib.EFFORT
import olygym.app.data.Exercise
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.barWeightFor
import olygym.app.lib.bestWeightFor
import olygym.app.lib.effortColor
import olygym.app.lib.effortOf
import olygym.app.lib.exNoteFor
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.isBw
import olygym.app.lib.isWarmupRow
import olygym.app.lib.lastEntryFor
import olygym.app.lib.modeOf
import olygym.app.lib.pinnedNoteFor
import olygym.app.lib.plateSplit
import olygym.app.lib.progressionGuidance
import olygym.app.lib.setLabel
import olygym.app.lib.toScale
import olygym.app.lib.stepEffort
import olygym.app.lib.usesBar
import olygym.app.lib.weightIncrement
import olygym.app.lib.workoutControls
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Check
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.NumberField
import olygym.app.ui.components.Overline
import olygym.app.ui.components.SectionCard
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.tMessage
import olygym.app.ui.sheet.MenuItem
import olygym.app.ui.sheet.colorOf
import olygym.app.ui.sheet.menuSheet
import olygym.app.ui.theme.FullShape
import olygym.app.ui.theme.extraColors
import olygym.app.ui.ui

/** One column of the set table: which field, how it steps, and what it is called. */
internal data class SetColumn(
    val f: String,
    val step: Double,
    val decimal: Boolean,
    val hd: String,
    val opt: Boolean = false,
    val eff: String? = null,
)

/** The value of one cell: a field with the two step buttons the profile asked for, or none. */
@Composable
private fun CellStepper(
    value: Double?,
    onChange: (Double?) -> Unit,
    decimal: Boolean,
    nullable: Boolean,
    buttons: Boolean,
    step: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(FullShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (buttons) {
            StepButton(Glyph.MINUS, "Decrease") { onChange(stepValue(value, -1.0, step)) }
        }
        NumberField(
            value = value,
            onChange = onChange,
            decimal = decimal,
            nullable = nullable,
            // A two-digit value has to fit between two step buttons on a 360dp phone: the smaller
            // role is what the web's media queries achieve by shrinking the cell at this width.
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (buttons) {
            StepButton(Glyph.PLUS, "Increase") { onChange(stepValue(value, 1.0, step)) }
        }
    }
}

/** Typed or stepped, the value is clamped at zero and rounded to two decimals, as the web does. */
internal fun stepValue(current: Double?, dir: Double, step: Double = 1.0): Double {
    val raw = (current ?: 0.0) + dir * step
    val rounded = Math.round(raw * 100) / 100.0
    return maxOf(0.0, rounded)
}

@Composable
private fun StepButton(glyph: Glyph, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(26.dp, 40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Modifier.size(16.dp))
    }
}

/**
 * The effort cell: the rating in the profile's scale, tinted by how close to failure it was, and
 * four dots so the band is readable as a shape and not only as a colour. It is a button, not a
 * stepper — picking a value is what sets a rating, and the picker opens on tap.
 */
@Composable
private fun EffortCell(
    value: Double?,
    kind: String,
    hd: String,
    buttons: Boolean,
    onOpen: () -> Unit,
    onStep: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rir = if (kind == "rpe") value?.let { 10 - it } else value
    val color = colorOf(effortColor(rir))
    val band = if (rir == null) 0 else maxOf(1, 4 - minOf(3, maxOf(0, Math.round(rir).toInt())))
    Row(
        modifier = modifier
            .clip(FullShape)
            .background(color?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (buttons) StepButton(Glyph.MINUS, "Decrease") { onStep(-1.0) }
        Box(
            modifier = Modifier.weight(1f).height(40.dp).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (value == null) {
                Text(
                    text = hd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = fmtNum(value),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color ?: MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(4) { index ->
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index < band) {
                                            color ?: MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
        if (buttons) StepButton(Glyph.PLUS, "Increase") { onStep(1.0) }
    }
}

/**
 * One exercise block: the name, what is known about it, and the table of sets you are logging. The
 * port of ExerciseBlock in frontend/src/views/Workout.jsx, minus the two things that need a screen
 * this phase does not have: the demo media and the two rows that open the detail and history
 * sheets.
 */
@Composable
fun ExerciseBlock(
    entryIdx: Int,
    onToggle: (Int) -> Unit,
    onField: (Int, String, Double?) -> Unit,
    onAddSet: () -> Unit,
    onAddWarmup: () -> Unit,
    onRemoveSetAt: (Int) -> Unit,
    onStartTimed: (Int) -> Unit,
    onBarWeight: () -> Unit,
    onNote: () -> Unit,
    onRemoveExercise: () -> Unit,
    onDetails: () -> Unit,
    onProgression: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    onPairPrev: (() -> Unit)? = null,
    onPairNext: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    step: Int? = null,
    compact: Boolean = false,
    dense: Boolean = false,
    headOnly: Boolean = false,
    busy: Boolean = false,
) {
    val profile = currentProfile() ?: return
    val entry = profile.entries.getOrNull(entryIdx) ?: return
    val id = entry.str("id").orEmpty()
    val ex: Exercise? = Catalogue[id]
    val unit = profile.settings.unit
    val raw = profile.raw
    // The prescribed numbers live on target; the config the readers want is the two merged, with the
    // id on top, exactly as the web builds it.
    val cfg = (entry.obj("target") ?: JsonObject(emptyMap())).with("id", id)
    val mode = modeOf(cfg)
    val timed = mode == "time"
    val sets = entry.arr("sets").mapNotNull { it.asObj() }

    val last = lastEntryFor(raw, id)
    val standingNote = exNoteFor(raw, id)
    // Only worth surfacing while there is still work left: once the exercise is finished, a note
    // telling you what to do in it is behind you.
    val pinnedNote = if (sets.any { it.bool("done") != true }) pinnedNoteFor(raw, id) else null
    val bestHist = bestWeightFor(raw, id)
    val bestKept = raw.obj("exWeights")?.obj(id)?.num("w") ?: 0.0
    val best = maxOf(bestHist, bestKept)

    val bw = isBw(cfg)
    val added = bw && sets.any { (it.num("w") ?: 0.0) > 0 }
    val loadStep = if (mode == "reps") weightIncrement(cfg) else 2.5
    val loadCol = SetColumn(
        f = "w",
        step = loadStep,
        decimal = true,
        hd = if (bw) t("Added ({0})", unit) else t("Weight ({0})", unit),
    )
    val repCol = SetColumn(f = "r", step = 1.0, decimal = false, hd = t("Reps"))
    val col1 = when {
        timed -> SetColumn(f = "sec", step = 5.0, decimal = false, hd = t("Seconds"))
        bw && !added -> repCol
        else -> loadCol
    }
    val col2 = when {
        timed -> if (bw && !added) null else loadCol
        bw && !added -> null
        else -> repCol
    }
    val kind = effortOf(raw)
    val scale = EFFORT[kind]
    val col3 = if (mode == "reps" && scale != null) {
        SetColumn(f = scale.f, step = scale.step, decimal = true, hd = t(scale.hd), opt = true, eff = kind)
    } else {
        null
    }

    val controls = workoutControls(raw)
    val guidance = progressionGuidance(entry["plan"])
    val barInfo = if (!(bw && !added) && usesBar(js("id" to id, "eq" to ex?.eq))) {
        val bar = barWeightFor(raw, js("id" to id, "eq" to ex?.eq))
        if (bar == null || bar < 0) {
            null
        } else {
            val next = sets.firstOrNull { it.bool("done") != true }?.num("w") ?: 0.0
            val refW = if (next > 0) next else sets.maxOfOrNull { it.num("w") ?: 0.0 } ?: 0.0
            val split = plateSplit(refW, bar)
            val perSide = split?.let { t("{0} per side", fmtNum(it) + " " + unit) }
            when {
                bar == 0.0 -> perSide
                else -> t("Bar {0}", fmtNum(bar) + " " + unit) + (perSide?.let { " · " + it } ?: "")
            }
        }
    } else {
        null
    }

    val prescribedReps = if (headOnly) {
        (entry.obj("target")?.num("reps") ?: 0.0).toInt().takeIf { it > 0 }
            ?: sets.firstOrNull { !isWarmupRow(it) }?.num("r")?.toInt()
            ?: 0
    } else {
        0
    }

    val openMore = {
        menuSheet(
            title = Catalogue.nameOf(id),
            items = listOfNotNull(
                MenuItem(
                    label = if (entry["note"].present()) t("Edit note") else t("Add note"),
                    icon = Glyph.PENCIL,
                    sub = entry.str("note"),
                    onClick = onNote,
                ),
                MenuItem(label = t("Details"), icon = Glyph.INFO, onClick = onDetails),
                MenuItem(
                    label = t("Progression settings"),
                    icon = Glyph.CHART_LINE,
                    sub = guidance?.let { t(it.policyLabel) },
                    onClick = onProgression,
                ),
                barInfo?.let {
                    MenuItem(label = t("Bar weight"), icon = Glyph.BARBELL, sub = it, onClick = onBarWeight)
                },
                MenuItem(label = t("Add warm-up set"), icon = Glyph.FLAME, onClick = onAddWarmup),
                onPairPrev?.let { MenuItem(label = t("Make complex with previous"), icon = Glyph.LINK, onClick = it) },
                onPairNext?.let { MenuItem(label = t("Make complex with next"), icon = Glyph.LINK, onClick = it) },
                MenuItem(
                    label = t("Swap exercise"),
                    icon = Glyph.SHUFFLE,
                    disabled = busy,
                    onClick = onSwap,
                ),
                onMoveUp?.let {
                    MenuItem(label = t("Move up"), icon = Glyph.CHEVRON_UP, disabled = busy || !canMoveUp, onClick = it)
                },
                onMoveDown?.let {
                    MenuItem(label = t("Move down"), icon = Glyph.CHEVRON_DOWN, disabled = busy || !canMoveDown, onClick = it)
                },
                MenuItem(
                    label = t("Remove exercise"),
                    icon = Glyph.TRASH,
                    danger = true,
                    disabled = busy,
                    onClick = onRemoveExercise,
                ),
            ),
        )
    }

    val openSetMenu = { set: JsonObject, index: Int ->
        val warm = isWarmupRow(set)
        val phaseNumber = sets.take(index + 1).count { isWarmupRow(it) == warm }
        menuSheet(
            title = if (warm) t("Warm-up") else t("Set {0}", phaseNumber),
            subtitle = setLabel(id, set, entry.obj("target")),
            items = listOf(
                MenuItem(
                    label = t("Remove this set"),
                    icon = Glyph.TRASH,
                    danger = true,
                    disabled = sets.size <= 1,
                    onClick = { onRemoveSetAt(index) },
                ),
            ),
        )
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step != null) {
                        Text(
                            text = step.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Catalogue.nameOf(id),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (compact || dense) 17.sp else 20.sp,
                            fontWeight = FontWeight.W600,
                            letterSpacing = (-0.02).em,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (prescribedReps > 0) {
                        Text(
                            text = "×$prescribedReps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!dense) {
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val muscle = ex?.tg?.let { MUSCLE_NAME[it] ?: it }
                        if (muscle != null) Tag(t(muscle))
                        ex?.eq?.let { Tag(t(it)) }
                        if (best > 0) Tag(t("Best:") + " " + fmtNum(best) + " " + unit)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry["note"].present()) {
                    IconButton(Glyph.PENCIL, onClick = onNote, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(Glyph.MORE, onClick = openMore)
            }
        }

        if (!dense) {
            cfg.str("note")?.let { Note(it, null) }
            standingNote?.let { Note(it, Glyph.INFO) }
            pinnedNote?.let { pinned ->
                Note(
                    text = t("From {0}:", fmtDate(pinned.str("d").orEmpty(), long = true)) + " " +
                        pinned.str("note").orEmpty(),
                    icon = Glyph.FLAG,
                    color = MaterialTheme.extraColors.yellow,
                )
            }
            entry.str("note")?.let { Note(it, null) }
            last?.let { previous ->
                val setsText = previous.arr("sets").joinToString(", ") { setLabel(id, it, previous.obj("target")) }
                Text(
                    text = t("Last time") + " (" + fmtDate(previous.str("d").orEmpty(), long = false) + "): " + setsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            barInfo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            guidance?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlyphIcon(Glyph.LIGHTBULB, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = t(it.policyLabel) + " · " + tMessage(it.why),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!headOnly) {
            SectionCard(modifier = Modifier.padding(top = 10.dp)) {
                SetHeader(col1, col2, col3)
                sets.forEachIndexed { index, set ->
                    val warm = isWarmupRow(set)
                    val warmBefore = index > 0 && isWarmupRow(sets[index - 1])
                    val done = set.bool("done") == true
                    if (warm && !warmBefore) {
                        Overline(t("Warm-up"), Modifier.padding(top = 6.dp, bottom = 2.dp))
                    }
                    if (!warm && warmBefore) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.extraColors.hairline,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).alpha(if (done) 0.55f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val phaseNumber = sets.take(index + 1).count { isWarmupRow(it) == warm }
                        Box(
                            modifier = Modifier.size(28.dp).clickable { openSetMenu(set, index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = phaseNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        CellStepper(
                            value = set.num(col1.f),
                            onChange = { onField(index, col1.f, it) },
                            decimal = col1.decimal,
                            nullable = col1.opt,
                            buttons = controls.steppers,
                            step = col1.step,
                            modifier = Modifier.weight(1f),
                        )
                        if (col2 != null) {
                            CellStepper(
                                value = set.num(col2.f),
                                onChange = { onField(index, col2.f, it) },
                                decimal = col2.decimal,
                                nullable = col2.opt,
                                buttons = controls.steppers,
                                step = col2.step,
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                        if (col3 != null) {
                            EffortCell(
                                value = set.num(col3.f),
                                kind = col3.eff ?: "rir",
                                hd = col3.hd,
                                buttons = controls.steppers,
                                onOpen = {
                                    olygym.app.ui.sheet.effortPickerSheet(col3.eff ?: "rir", set.num(col3.f)) { picked ->
                                        onField(index, col3.f, picked)
                                        if (picked != null && set.bool("done") != true) onToggle(index)
                                    }
                                },
                                onStep = { dir ->
                                    val next = stepEffort(col3.eff ?: "rir", set.num(col3.f), dir.toInt())
                                    onField(index, col3.f, next)
                                },
                                modifier = Modifier.weight(0.9f),
                            )
                        }
                        if (timed) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(enabled = !done && !busy) { onStartTimed(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphIcon(
                                    Glyph.PLAY,
                                    Modifier.size(18.dp),
                                    tint = if (done || busy) {
                                        MaterialTheme.extraColors.onSurfaceDisabled
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                        Check(checked = done, onChange = { onToggle(index) })
                    }
                }
                Button(
                    text = t("Add set"),
                    onClick = onAddSet,
                    variant = ButtonVariant.PLAIN,
                    size = ButtonSize.SM,
                    icon = Glyph.PLUS,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SetHeader(col1: SetColumn, col2: SetColumn?, col3: SetColumn?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(Modifier.width(28.dp))
        HeaderLabel(col1.hd, Modifier.weight(1f))
        if (col2 != null) HeaderLabel(col2.hd, Modifier.weight(0.8f))
        if (col3 != null) HeaderLabel(col3.hd, Modifier.weight(0.9f))
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(FullShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Note(text: String, icon: Glyph?, color: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            GlyphIcon(icon, Modifier.size(13.dp), tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

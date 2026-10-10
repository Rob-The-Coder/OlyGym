package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.JsonArray
import olygym.app.data.append
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.editAt
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.lastBW
import olygym.app.lib.todayISO
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.NumberField
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/** The weight range the slider offers, fixed so the thumb never moves under a growing window. */
private const val W_LO = 1.0
private const val W_HI = 300.0

private fun clampWeight(v: Double) = maxOf(W_LO, minOf(W_HI, Math.round(v * 10) / 10.0))

/**
 * The weight read-out with its two 0.1 buttons and the slider under it. A tap on the slider snaps to
 * half a kilo; the buttons and the field take the exact value the web control takes.
 */
@Composable
internal fun WeightInput(value: Double, setValue: (Double) -> Unit, unit: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton(Glyph.MINUS, t("Decrease")) { setValue(clampWeight(value - 0.1)) }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                NumberField(
                    value = value,
                    onChange = { if (it != null) setValue(it) },
                    decimal = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.W600,
                        letterSpacing = (-0.02).em,
                    ),
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            StepButton(Glyph.PLUS, t("Increase")) { setValue(clampWeight(value + 0.1)) }
        }
        Slider(
            value = value.toFloat().coerceIn(W_LO.toFloat(), W_HI.toFloat()),
            onValueChange = { setValue(clampWeight(Math.round(it * 2) / 2.0)) },
            valueRange = W_LO.toFloat()..W_HI.toFloat(),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun StepButton(glyph: Glyph, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(44.dp).height(44.dp).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(glyph, label = description, onClick = onClick, filled = true)
    }
}

/** The colour a weight change is written in: toward the goal, or away from it. */
@Composable
fun bwDeltaColor(delta: Double?, targetW: Double?, currentW: Double): Color {
    if (delta == null || delta == 0.0) return MaterialTheme.colorScheme.onSurfaceVariant
    if (targetW == null) return MaterialTheme.colorScheme.onSurface
    val up = targetW > currentW
    return if ((delta > 0) == up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
}

/** Write today's weigh-in: replace today's row or add one, then keep the list in date order. */
fun saveWeighIn(weight: Double) {
    val row = { existing: kotlinx.serialization.json.JsonObject ->
        existing.with("w", weight).with("t", System.currentTimeMillis())
    }
    editProfile { raw ->
        val iso = todayISO()
        val list = raw.arr("bodyweight")
        val at = list.indexOfFirst { it.asObj()?.str("d") == iso }
        val next = if (at >= 0) {
            list.editAt(at) { row(it) }
        } else {
            list.append(js("d" to iso, "w" to weight, "t" to System.currentTimeMillis()))
        }
        val sorted = next.sortedBy { it.asObj()?.str("d").orEmpty() }
        raw.with("bodyweight", JsonArray(sorted))
    }
}

/**
 * The weigh-in. Opened before a session it is locked — an accidental tap on Start must not be walked
 * back by reflex — and it offers the two deliberate ways out instead.
 */
fun weighInSheet(required: Boolean = false, onDone: ((Double?) -> Unit)? = null) {
    ui.openSheet(locked = required) { close -> WeighInSheet(required, onDone, close) }
}

@Composable
private fun WeighInSheet(required: Boolean, onDone: ((Double?) -> Unit)?, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val last = lastBW(profile.raw)?.asObj()
    var value by remember { mutableStateOf(last?.num("w") ?: 70.0) }
    val recent = profile.bodyweight.reversed().take(3)

    Column(Modifier.fillMaxWidth()) {
        if (required) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("Quick check-in"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(Glyph.XMARK, t("Close"), onClick = close, filled = false)
            }
        } else {
            SheetTitle(t("Log body weight"))
        }
        SheetNote(
            if (required) {
                t("Slide or tap to set your weight — tracked before every workout so your curve stays honest.")
            } else {
                t("Today") + ", " + fmtDate(todayISO(), long = true)
            }
        )
        WeightInput(value, { value = it }, unit)

        if (!required) {
            val previous = profile.bodyweight.lastOrNull { it.str("d") != todayISO() }
            val since = previous?.num("w")?.let { Math.round((value - it) * 10) / 10.0 }
            if (since != null) {
                if (Math.abs(since) >= 0.05) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GlyphIcon(
                            if (since > 0) Glyph.ARROW_UP else Glyph.ARROW_DOWN,
                            Modifier.width(12.dp).height(12.dp),
                            tint = bwDeltaColor(since, profile.settings.targetW, value),
                        )
                        Text(
                            text = fmtNum(Math.abs(since)) + " " + unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = bwDeltaColor(since, profile.settings.targetW, value),
                        )
                        Text(
                            text = t("since {0}", fmtDate(previous.str("d").orEmpty(), long = true)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = t("Unchanged since {0}", fmtDate(previous.str("d").orEmpty(), long = true)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        Button(
            text = if (required) t("Save & start workout") else t("Save"),
            onClick = {
                val n = Math.round(value * 10) / 10.0
                if (n <= 0) {
                    ui.toast(t("Enter a valid weight"))
                    return@Button
                }
                saveWeighIn(n)
                close()
                if (onDone != null) onDone(n) else ui.toast(t("Weight saved"))
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 14.dp),
        )

        if (required) {
            Button(
                text = t("Start without weighing in"),
                onClick = { close(); onDone?.invoke(null) },
                variant = ButtonVariant.GHOST,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                text = t("Choose a different workout"),
                // Deliberately not onDone: this does not start a session, it opens the chooser.
                onClick = { close(); olygym.app.ui.Nav.to(olygym.app.ui.workout.WorkoutScreen) },
                variant = ButtonVariant.GHOST,
                icon = Glyph.RESET,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!required && recent.isNotEmpty()) {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    text = t("Recent weigh-ins"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Section {
                    recent.forEachIndexed { index, row ->
                        if (index > 0) RowDivider()
                        val iso = row.str("d").orEmpty()
                        ListRow(
                            title = fmtDate(iso, long = true),
                            icon = Glyph.SCALE,
                            value = fmtNum(row.num("w") ?: 0.0) + " " + unit,
                            trailing = {
                                IconButton(Glyph.TRASH, t("Remove"), onClick = {
                                    editProfile { raw ->
                                        raw.with(
                                            "bodyweight",
                                            JsonArray(raw.arr("bodyweight").filterNot { it.asObj()?.str("d") == iso }),
                                        )
                                    }
                                    ui.toast(t("Weigh-in removed"))
                                })
                            },
                        )
                    }
                }
            }
        }
    }
}

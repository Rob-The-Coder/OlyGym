package olygym.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import olygym.app.data.js
import olygym.app.lib.EFFORT
import olygym.app.lib.EFFORT_PRESETS
import olygym.app.lib.I18nCore
import olygym.app.lib.capEffort
import olygym.app.lib.effortColor
import olygym.app.lib.fmtNum
import olygym.app.lib.rirOf
import olygym.app.lib.stepEffort
import olygym.app.lib.toScale
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.NumberField
import olygym.app.ui.components.Section
import olygym.app.ui.components.RowDivider
import olygym.app.ui.t
import olygym.app.ui.ui

/** A colour from the app's own table ("#e5484d"), or null when it is not one. */
internal fun colorOf(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val hex = value.removePrefix("#")
    val argb = if (hex.length == 6) "FF$hex" else hex
    return argb.toLongOrNull(16)?.let { Color(it) }
}

/**
 * The effort picker: how hard a set was, in the profile's own scale. One tap sets a rating and
 * concludes the set (the caller ticks it), which is what spares the second confirmation.
 */
fun effortPickerSheet(kind: String, value: Double?, onPick: (Double?) -> Unit) {
    ui.openSheet { close -> EffortPicker(kind, value, onPick, close) }
}

@Composable
private fun EffortPicker(kind: String, value: Double?, onPick: (Double?) -> Unit, close: () -> Unit) {
    val scale = EFFORT[kind] ?: EFFORT.getValue("rir")
    var current by remember { mutableStateOf(value) }
    // Compared in RIR, so a typed RPE lands the tick on the preset it equals and colours the same.
    val currentRir = rirOf(js(if (kind == "rpe") "rpe" to current else "rir" to current))
    val currentColor = colorOf(effortColor(currentRir))
    val commit = { nv: Double? -> close(); onPick(nv) }
    val set = { nv: Double? -> current = capEffort(kind, nv) }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("How hard was that set?"))
        SheetNote(t("Tap how many reps you had left, or type an exact {0}.", scale.hd))

        Button(
            text = t("What are RIR and RPE?"),
            onClick = { effortHelpSheet() },
            variant = ButtonVariant.GHOST,
            size = ButtonSize.SM,
            icon = Glyph.INFO,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        Section {
            EFFORT_PRESETS.forEachIndexed { index, preset ->
                if (index > 0) RowDivider()
                val label = fmtNum(toScale(kind, preset.rir) ?: 0.0) + if (preset.tail) "+" else ""
                val on = currentRir != null && currentRir == preset.rir
                val tint = colorOf(preset.color) ?: MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { commit(toScale(kind, preset.rir)) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(29.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                            color = tint,
                        )
                    }
                    Text(
                        text = t(preset.feel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) {
                        GlyphIcon(
                            Glyph.CHECK,
                            Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary,
                            stroke = 2.4f,
                        )
                    }
                }
            }

            // The exact value: the app's own stepper, tinted like the cell in the set row.
            RowDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = t("Exact {0}", scale.hd),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(olygym.app.ui.theme.FullShape)
                        .background(currentColor?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp, 44.dp).clickable { set(stepEffort(kind, current, -1)) },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.MINUS, Modifier.size(20.dp), tint = currentColor ?: MaterialTheme.colorScheme.onSurface) }
                    NumberField(
                        value = current,
                        onChange = { set(it) },
                        decimal = true,
                        nullable = true,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.size(40.dp, 44.dp).clickable { set(stepEffort(kind, current, 1)) },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.PLUS, Modifier.size(20.dp), tint = currentColor ?: MaterialTheme.colorScheme.onSurface) }
                }
            }
        }

        if (value != null) {
            Button(
                text = t("Clear rating"),
                onClick = { commit(null) },
                variant = ButtonVariant.GHOST,
                icon = Glyph.XMARK,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The two scales are one judgement counted from opposite ends, and the table says it in a look. */
private val EFFORT_ROWS = listOf(
    Triple("0", "10", "Nothing left — went to failure"),
    Triple("1", "9", "One more rep in the tank"),
    Triple("2", "8", "Two more reps"),
    Triple("3", "7", "Three more reps"),
    Triple("4+", "≤6", "Easy — warm-up territory"),
)

/** RIR 2 / RPE 8: the row a working set usually lands on. */
private const val EFFORT_TYPICAL = 2

fun effortHelpSheet() {
    ui.openSheet { _ ->
        Column(Modifier.fillMaxWidth()) {
            SheetTitle(t("Effort per set"))
            SheetNote(
                t("How hard a set was, logged next to weight and reps. Two scales for the same judgement, counted from opposite ends."),
            )
            Section {
                EFFORT_ROWS.forEachIndexed { index, row ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == EFFORT_TYPICAL) {
                                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                } else Modifier
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(38.dp))
                        Text(row.second, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(38.dp))
                        Text(row.third, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    text = t("RIR counts the reps you left; RPE reads the same effort off a 10-point scale — so RPE ≈ 10 − RIR. Pick the one you already think in."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = t("The highlighted row is where most working sets land. Sets you have already logged keep their own scale, and nothing else reads the value — progression is unaffected."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.js
import olygym.app.data.obj
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.barWeightFor
import olygym.app.lib.defaultBarWeight
import olygym.app.lib.fmtNum
import olygym.app.lib.hasBarOverride
import olygym.app.lib.isNoBar
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Section
import olygym.app.ui.components.Stepper
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * One editor for every place the bar weight shows up: a stepper over the effective value, and the
 * "No bar" switch. What it saves lives in S.barWeights, per exercise, in the profile's unit. What
 * "No bar" stores is a zero — a different thing from no entry at all: a counterbalanced carriage
 * weighs nothing in your hands, so the plate maths has to start from what was logged.
 */

fun barWeightSheet(exId: String) {
    ui.openSheet { close -> BarWeightSheet(exId, close) }
}

/** Stepping or typing down to zero drops the override, the same shape a cleared set field has. */
private fun setBar(id: String, value: Double) {
    val n = maxOf(0.0, Math.round(value * 100) / 100.0)
    editProfile { raw ->
        val weights = raw.obj("barWeights") ?: JsonObject(emptyMap())
        raw.with("barWeights", if (n > 0) weights.with(id, n) else weights.without(id))
    }
}

private fun setNoBar(id: String, on: Boolean) {
    editProfile { raw ->
        val weights = raw.obj("barWeights") ?: JsonObject(emptyMap())
        raw.with("barWeights", if (on) weights.with(id, 0) else weights.without(id))
    }
}

@Composable
private fun BarWeightSheet(exId: String, close: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Bar weight"))
        SheetNote(Catalogue.nameOf(exId))
        BarWeightEditor(exId, t("Applies to this exercise everywhere, not just this plan."))
        Button(
            text = t("Done"),
            onClick = close,
            variant = ButtonVariant.PRIMARY,
        )
    }
}

/**
 * The editor itself, shared with the exercise config sheet: a stepper over the effective value, the
 * "No bar" switch, and the line that says what the current state means. Everything below reads the
 * profile afresh, because the config sheet holds it open while the value changes.
 */
@Composable
fun BarWeightEditor(exId: String, extra: String? = null) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val ex = Catalogue[exId]
    val raw = profile.raw
    val explicit = hasBarOverride(raw, exId)
    val default = defaultBarWeight(ex?.eq)
    val noBar = isNoBar(raw, exId)
    val current = barWeightFor(raw, js("id" to exId)) ?: 0.0

    Column(Modifier.fillMaxWidth()) {
        if (!noBar) {
            Stepper(
                value = current,
                onChange = { setBar(exId, it) },
                step = 2.5,
                label = t("Bar ({0})", unit),
                unit = unit,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        Section(modifier = Modifier.padding(bottom = 10.dp)) {
            ListRow(
                title = t("No bar"),
                icon = Glyph.BARBELL,
                subtitle = t("The weight you log is all plates."),
                trailing = { Switch(checked = noBar, onCheckedChange = { setNoBar(exId, it) }) },
            )
        }

        val defaultText = default?.let { fmtNum(it) + " " + unit } ?: "—"
        Text(
            text = when {
                noBar -> t("Plates are counted from 0 — turn this off for the default ({0}).", defaultText)
                explicit -> t("Set to 0 to go back to the default ({0}).", defaultText)
                else -> t("Default for this bar type.")
            } + (extra?.let { " " + it } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

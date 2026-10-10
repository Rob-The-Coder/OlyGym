package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.with
import olygym.app.lib.fmtNum
import olygym.app.lib.lastBW
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.profileNow
import olygym.app.ui.t
import olygym.app.ui.theme.extraColors
import olygym.app.ui.ui

/** The weight goal: a number, what is left to it, and the way to take it away again. */
fun goalSheet() {
    ui.openSheet { close -> GoalSheet(close) }
}

@Composable
private fun GoalSheet(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val current = lastBW(profile.raw)?.asObj()?.num("w")
    val saved = profile.settings.targetW
    var value by remember { mutableStateOf(saved ?: current ?: 70.0) }

    val gap = current?.let { Math.round((value - it) * 10) / 10.0 }
    // With no goal saved the field opens on today's weight, so a gap of zero means "the number you
    // already are", not "reached" — which would congratulate you for setting nothing.
    val gapText = when {
        gap == null -> ""
        gap == 0.0 -> if (saved == null) t("same as today") else t("reached!")
        gap > 0 -> t("{0} to gain", fmtNum(Math.abs(gap)) + " " + unit)
        else -> t("{0} to lose", fmtNum(Math.abs(gap)) + " " + unit)
    }

    val help: () -> Unit = {
        ui.openSheet { _ ->
            Column(Modifier.fillMaxWidth()) {
                SheetTitle(t("Target weight"))
                SheetNote(
                    t("Your goal is drawn as a line through the weight charts, and gains/losses are colored by whether they move toward it."),
                )
            }
        }
    }

    val remove = {
        confirmSheet(
            title = t("Remove the goal?"),
            message = t("The weight charts stop drawing the line. Your weigh-ins are not touched."),
            confirmText = t("Remove goal"),
            danger = true,
            onConfirm = {
                editProfile { it.with("targetW", null) }
                close()
                ui.toast(t("Goal removed"))
            },
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = t("Target weight"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (saved != null) {
                IconButton(Glyph.MORE, t("More"), onClick = {
                    menuSheet(
                        title = t("Target weight"),
                        items = listOf(
                            MenuItem(label = t("Remove goal"), icon = Glyph.TRASH, danger = true, onClick = remove),
                        ),
                    )
                })
            }
        }

        if (current != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Tile(
                    label = t("Today"),
                    value = fmtNum(current),
                    suffix = unit,
                    icon = Glyph.SCALE,
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = t("Goal"),
                    value = fmtNum(value),
                    suffix = gapText,
                    icon = Glyph.TARGET,
                    valueColor = MaterialTheme.extraColors.yellow,
                    labelTrailing = { IconButton(Glyph.INFO, t("Help"), onClick = help, stroke = 1.7f) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        WeightInput(value, { value = it }, unit)

        Button(
            text = t("Save goal"),
            onClick = {
                val n = Math.round(value * 10) / 10.0
                if (n <= 0) {
                    ui.toast(t("Enter a valid weight"))
                    return@Button
                }
                editProfile { it.with("targetW", n) }
                close()
                val b = lastBW(profileNow()?.raw)?.asObj()?.num("w")
                val toGo = if (b == null) "" else " (" + t("{0} to go", fmtNum(Math.abs(n - b))) + ")"
                ui.toast(t("Goal set: {0}", fmtNum(n) + " " + unit) + toGo)
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

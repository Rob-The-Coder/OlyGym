package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * The app's replacement for a native <select>: a row that shows the current value and opens the
 * app's own sheet of choices. Built here rather than taken from Material3 because the two sheets
 * the app already uses for this — the menu and the grouped list — are what a reader recognises.
 */
data class SelectOption(val value: String, val label: String, val subtitle: String? = null)

@Composable
fun SelectRow(
    title: String,
    value: String,
    options: List<SelectOption>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    sheetTitle: String? = null,
) {
    val current = options.firstOrNull { it.value == value }
    ListRow(
        title = title,
        icon = icon,
        value = current?.label ?: value.takeIf { it.isNotEmpty() },
        accessory = Accessory.CHEVRON,
        modifier = modifier,
        onClick = { selectSheet(sheetTitle ?: title, options, value, onChange) },
    )
}

fun selectSheet(title: String, options: List<SelectOption>, value: String, onPick: (String) -> Unit) {
    ui.openSheet { close -> SelectSheetContent(close, title, options, value, onPick) }
}

@Composable
private fun SelectSheetContent(
    close: () -> Unit,
    title: String,
    options: List<SelectOption>,
    value: String,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(title)
        Section {
            options.forEachIndexed { index, option ->
                if (index > 0) RowDivider()
                ListRow(
                    title = option.label,
                    subtitle = option.subtitle,
                    accessory = if (option.value == value) Accessory.CHECK else Accessory.NONE,
                    onClick = {
                        close()
                        onPick(option.value)
                    },
                )
            }
        }
    }
}

/**
 * The multi-select row: a summary of what is on, and a sheet that ticks as you tap. The sheet
 * mirrors the selection locally so each tap updates the checkmark immediately while the caller
 * persists the value.
 */
@Composable
fun MultiSelectRow(
    title: String,
    values: List<String>,
    options: List<SelectOption>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String = "",
) {
    val summary = options.filter { values.contains(it.value) }.joinToString(", ") { it.label }
    ListRow(
        title = title,
        value = summary.ifEmpty { noneLabel.takeIf { it.isNotEmpty() } },
        accessory = Accessory.CHEVRON,
        modifier = modifier,
        onClick = { multiSelectSheet(title, values, options, onToggle) },
    )
}

fun multiSelectSheet(
    title: String,
    values: List<String>,
    options: List<SelectOption>,
    onToggle: (String) -> Unit,
    doneLabel: String = "Done",
) {
    ui.openSheet { close -> MultiSelectSheetContent(close, title, values, options, onToggle, doneLabel) }
}

@Composable
private fun MultiSelectSheetContent(
    close: () -> Unit,
    title: String,
    values: List<String>,
    options: List<SelectOption>,
    onToggle: (String) -> Unit,
    doneLabel: String,
) {
    val selected = remember { mutableStateListOf(*values.toTypedArray()) }
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(title)
        Section {
            options.forEachIndexed { index, option ->
                if (index > 0) RowDivider()
                val on = selected.contains(option.value)
                ListRow(
                    title = option.label,
                    subtitle = option.subtitle,
                    accessory = if (on) Accessory.CHECK else Accessory.NONE,
                    onClick = {
                        if (on) selected.remove(option.value) else selected.add(option.value)
                        onToggle(option.value)
                    },
                )
            }
        }
        Button(
            text = t(doneLabel),
            onClick = close,
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

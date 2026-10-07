package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import olygym.app.ui.SheetKind
import olygym.app.ui.UiState
import olygym.app.lib.capWords
import olygym.app.ui.theme.SheetShape
import olygym.app.ui.theme.emphasizedWeight

/**
 * Everything the UI holder has stacked, newest on top.
 *
 * Each sheet is its own M3 bottom sheet (or centred dialog), which is what makes a second one opened
 * from inside the first — the effort picker's own help — land above it rather than replace it. A
 * locked sheet ignores the backdrop, the swipe and back, which is the one thing the weigh-in and
 * the finish summary insist on: a mis-tap on Start must not be walked back by reflex.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(state: UiState) {
    val snapshot by state.state.collectAsState()
    snapshot.sheets.forEach { sheet ->
        key(sheet.id) {
            when (sheet.kind) {
                SheetKind.SHEET -> ModalBottomSheet(
                    onDismissRequest = { if (!sheet.locked) state.closeSheet(sheet.id) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = SheetShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !sheet.locked),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
                            .imePadding(),
                    ) {
                        sheet.content { state.closeSheet(sheet.id) }
                    }
                }

                SheetKind.CENTER -> BasicAlertDialog(
                    onDismissRequest = { if (!sheet.locked) state.closeSheet(sheet.id) },
                    properties = DialogProperties(
                        dismissOnBackPress = !sheet.locked,
                        dismissOnClickOutside = !sheet.locked,
                    ),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            sheet.content { state.closeSheet(sheet.id) }
                        }
                    }
                }
            }
        }
    }
}

/** The heading every sheet opens with, which is also what names the dialog. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier, capitalize: Boolean = false) {
    Text(
        text = if (capitalize) capWords(text) else text,
        // The web's .sheet h3: the title role at its Emphasized weight.
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = emphasizedWeight(MaterialTheme.typography.titleLarge.fontWeight),
            letterSpacing = (-0.02).em,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** The line under a sheet's heading: what this sheet is for, in one sentence. */
@Composable
fun SheetNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** A label above an input, with the sentence that explains it underneath. */
@Composable
fun FieldLabel(label: String, hint: String? = null) {
    Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A multi-line text input, capped like the web's maxLength. BasicTextField rather than Material3's
 * outlined one: the app's fields are flat surfaces with no floating label, and the label is a
 * FieldLabel above it.
 */
@Composable
fun NoteField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    max: Int? = null,
    singleLine: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { typed -> onChange(if (max != null) typed.take(max) else typed) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
        )
    }
}

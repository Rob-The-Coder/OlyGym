package olygym.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.t
import olygym.app.ui.ui

/** One row of a menu sheet: an action, its optional sub-line, and how it is painted. */
data class MenuItem(
    val label: String,
    val icon: Glyph? = null,
    val sub: String? = null,
    val onClick: (() -> Unit)? = null,
    val danger: Boolean = false,
    val disabled: Boolean = false,
    /** A toggle row: true draws the tick, null draws nothing. */
    val on: Boolean? = null,
)

/**
 * A list of actions, one per row, closing on tap. The port of menuSheet: where the workout screen
 * parks everything that is not a set you are about to log, so the ten things you do once a session
 * stop competing with the two you do every set.
 */
@Composable
fun MenuSheetContent(
    close: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    items: List<MenuItem>,
) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) SheetTitle(title, capitalize = true)
        if (subtitle != null) SheetNote(subtitle)
        Section {
            val visible = items
            visible.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                ListRow(
                    title = item.label,
                    icon = item.icon,
                    subtitle = item.sub,
                    danger = item.danger,
                    enabled = !item.disabled,
                    onClick = if (item.disabled) null else {
                        { close(); item.onClick?.invoke() }
                    },
                    trailing = if (item.on == true) {
                        {
                            GlyphIcon(
                                Glyph.CHECK,
                                Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary,
                                stroke = 2.4f,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

fun menuSheet(title: String? = null, subtitle: String? = null, items: List<MenuItem>) {
    ui.openSheet { close -> MenuSheetContent(close, title, subtitle, items) }
}

/**
 * The app's own confirm: an M3 basic dialog, with the body on the left and the two actions at the
 * end, the destructive one painted as such. Replaces window.confirm, which ignores the theme and
 * blocks.
 */
@Composable
fun ConfirmSheetContent(
    close: () -> Unit,
    title: String?,
    message: String?,
    confirmText: String?,
    cancelText: String?,
    danger: Boolean,
    onConfirm: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    val go = { fn: (() -> Unit)? -> close(); fn?.invoke() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (danger) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (danger) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.WARNING, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (danger) TextAlign.Center else TextAlign.Start,
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (danger) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                text = cancelText ?: t("Cancel"),
                onClick = { go(onCancel) },
                variant = ButtonVariant.GHOST,
                size = ButtonSize.SM,
            )
            Button(
                text = confirmText ?: t("Confirm"),
                onClick = { go(onConfirm) },
                variant = if (danger) ButtonVariant.DANGER else ButtonVariant.GHOST,
                size = ButtonSize.SM,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

fun confirmSheet(
    title: String? = null,
    message: String? = null,
    confirmText: String? = null,
    cancelText: String? = null,
    danger: Boolean = false,
    locked: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    ui.openSheet(kind = olygym.app.ui.SheetKind.CENTER, locked = locked) { close ->
        ConfirmSheetContent(close, title, message, confirmText, cancelText, danger, onConfirm, onCancel)
    }
}

/** A question with a list of answers: which exercise of a complex to remove, or what to replace. */
@Composable
fun ChooseSheetContent(
    close: () -> Unit,
    title: String,
    message: String? = null,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(title)
        if (message != null) SheetNote(message)
        Section {
            options.forEachIndexed { index, label ->
                if (index > 0) RowDivider()
                ListRow(
                    title = label,
                    accessory = olygym.app.ui.components.Accessory.CHEVRON,
                    onClick = { close(); onPick(index) },
                )
            }
        }
    }
}

fun chooseSheet(title: String, message: String? = null, options: List<String>, onPick: (Int) -> Unit) {
    ui.openSheet { close -> ChooseSheetContent(close, title, message, options, onPick) }
}

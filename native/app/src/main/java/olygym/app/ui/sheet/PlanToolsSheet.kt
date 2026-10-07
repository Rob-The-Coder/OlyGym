package olygym.app.ui.sheet

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import olygym.app.lib.planPrintHTML
import olygym.app.platform.printHtml
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * "Share your plan": the print door, on the plan itself.
 *
 * The web's sheet carries the coach's spreadsheet and the Drive import too; here the coach's file
 * already has a door of its own in Settings, so print is the one action this sheet has -- and the
 * place the Drive one joins it.
 */
fun planToolsSheet() {
    ui.openSheet { close -> PlanTools(close) }
}

@Composable
private fun PlanTools(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val context = LocalContext.current
    val hasRoutines = profile.weeks.any { week -> week.days.any { it.ex.isNotEmpty() } }

    SheetTitle(t("Share your plan"))
    SheetNote(t("Put your week on paper."))
    Button(
        text = t("Print / Save as PDF"),
        icon = Glyph.DOWNLOAD,
        variant = ButtonVariant.TINTED,
        enabled = hasRoutines,
        onClick = {
            close()
            printHtml(context, planPrintHTML(profile.raw, ""), t("Weekly Training Plan"))
        },
    )
    Text(
        text = if (hasRoutines) {
            t("A clean one-page-per-plan printout — no exercise ever splits across a page.")
        } else {
            t("Add an exercise to a routine first — an empty plan has nothing to share.")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
    )
}

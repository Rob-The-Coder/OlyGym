package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import olygym.app.lib.BODYPARTS
import olygym.app.lib.LibraryResult
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Chip
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * The catalogue filters a reader is trying, before they are committed back to a screen.
 *
 * The sheet keeps its own copy and commits on "Show N exercises": trying a body part and backing
 * out changes nothing. describeFor is the calling screen own filter function, so the count on that
 * button is the count you get — the sheet cannot drift from the list it is filtering. A port of
 * frontend/src/components/LibraryFilters.jsx.
 */
data class FilterChoice(val bp: String, val eq: String, val showAll: Boolean)

fun libraryFilterSheet(
    bp: String,
    eq: String,
    showAll: Boolean,
    profileName: String?,
    describeFor: (FilterChoice) -> LibraryResult,
    onApply: (FilterChoice) -> Unit,
): Long = ui.openSheet { close ->
    LibraryFilters(bp, eq, showAll, profileName, describeFor, onApply, close)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilters(
    bp: String,
    eq: String,
    showAll: Boolean,
    profileName: String?,
    describeFor: (FilterChoice) -> LibraryResult,
    onApply: (FilterChoice) -> Unit,
    close: () -> Unit,
) {
    var part by remember { mutableStateOf(bp) }
    var kit by remember { mutableStateOf(eq) }
    var all by remember { mutableStateOf(showAll) }
    val preview = describeFor(FilterChoice(part, kit, all))
    // Same guard as the screens: a body part with no dumbbell work cannot leave "Dumbbell" chosen.
    val on = if (preview.eqOpts.contains(kit)) kit else ""
    val clean = part.isEmpty() && on.isEmpty()

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Filters"))

        Overline(t("Body part"), Modifier.padding(bottom = 6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(t("All"), onClick = { part = "" }, on = part.isEmpty())
            BODYPARTS.forEach { x ->
                Chip(t(x), onClick = { part = if (part == x) "" else x }, on = part == x)
            }
        }

        if (preview.eqOpts.size > 1) {
            Overline(t("Equipment"), Modifier.padding(top = 14.dp, bottom = 6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(t("Any equipment"), onClick = { kit = "" }, on = on.isEmpty())
                preview.eqOpts.forEach { x ->
                    Chip(t(x), onClick = { kit = if (on == x) "" else x }, on = on == x)
                }
            }
        }

        // The active equipment profile is a filter like any other rather than a banner with its own
        // toggle, which is what the web settled on.
        if (profileName != null) {
            ListRow(
                title = t("Only my equipment"),
                subtitle = t("Leave out anything \"{0}\" does not cover", profileName),
                modifier = Modifier.padding(top = 10.dp),
                trailing = { Switch(checked = !all, onCheckedChange = { all = !it }) },
            )
        }

        Button(
            text = t("Show {0} exercises", preview.list.size),
            onClick = {
                onApply(FilterChoice(part, on, all))
                close()
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
        if (!clean) {
            Button(
                text = t("Clear filters"),
                onClick = {
                    part = ""
                    kit = ""
                },
                variant = ButtonVariant.GHOST,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

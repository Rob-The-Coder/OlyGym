package olygym.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import olygym.app.data.Profile
import olygym.app.data.arr
import olygym.app.lib.fmtVol
import olygym.app.lib.historyMonths
import olygym.app.lib.historySearch
import olygym.app.lib.historyTotals
import olygym.app.lib.monthLabel
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.SearchField
import olygym.app.ui.components.Tile
import olygym.app.ui.components.WorkoutRow
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.currentProfile
import olygym.app.ui.sheet.logPastWorkoutSheet
import olygym.app.ui.sheet.workoutDetailSheet
import olygym.app.ui.t

/**
 * The whole log — a port of frontend/src/views/History.jsx. It opens with the four totals a list
 * cannot give at a glance, and it is the one screen where finding a particular session matters,
 * which is what the field and the month headings are for. Logging a past workout is an action on the
 * screen, in the app bar, not the first thing on it.
 */
object HistoryScreen : AppScreen() {
    @Composable
    override fun Content() {
        val profile = currentProfile() ?: return
        History(profile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun History(profile: Profile) {
    val scroll = olyAppBarScrollBehavior()
    val unit = profile.settings.unit
    var query by remember { mutableStateOf("") }
    val workouts = profile.raw.arr("workouts")
    val shown = historySearch(workouts, query)
    val totals = historyTotals(workouts)
    val months = historyMonths(shown)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("History"),
                scrollBehavior = scroll,
                subtitle = t("{0} workouts", workouts.size),
                leading = { IconButton(Glyph.CHEVRON_LEFT, t("Back"), onClick = { Nav.back() }) },
                actions = { IconButton(Glyph.PLUS, t("Log a past workout"), onClick = { logPastWorkoutSheet() }) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (workouts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(t("Workouts"), totals.workouts.toString(), Modifier.weight(1f), labelWrap = true)
                    Tile(t("Sets"), totals.sets.toString(), Modifier.weight(1f), labelWrap = true)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tile(t("Volume"), fmtVol(totals.volume, unit), Modifier.weight(1f), labelWrap = true)
                    Tile(t("PRs"), totals.prs.toString(), Modifier.weight(1f), labelWrap = true)
                }
                SearchField(
                    value = query,
                    onChange = { query = it },
                    placeholder = t("Search a workout or exercise"),
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
            }

            months.forEach { month ->
                Overline(monthLabel(month.key), Modifier.padding(top = 12.dp, bottom = 4.dp))
                month.items.forEach { w ->
                    WorkoutRow(w, unit) { workoutDetailSheet(w) }
                }
            }

            if (workouts.isEmpty()) {
                EmptyState(
                    glyph = Glyph.HISTORY,
                    title = t("No workouts yet."),
                    action = t("Log a past workout"),
                    onAction = { logPastWorkoutSheet() },
                )
            } else if (shown.isEmpty()) {
                EmptyState(glyph = Glyph.MAGNIFIER, title = t("No match"))
            }
        }
    }
}

@Composable
private fun EmptyState(
    glyph: Glyph,
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            glyph,
            Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            stroke = 1.6f,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (action != null && onAction != null) {
            Button(
                text = action,
                onClick = onAction,
                icon = Glyph.PLUS,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}


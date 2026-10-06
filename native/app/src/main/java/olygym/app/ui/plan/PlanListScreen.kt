package olygym.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.JsonObject
import olygym.app.OlyGymApp
import olygym.app.data.Catalogue
import olygym.app.data.Day
import olygym.app.data.PlanState
import olygym.app.data.Week
import olygym.app.data.int
import olygym.app.data.str
import olygym.app.lib.DAYN
import olygym.app.lib.I18nCore
import olygym.app.lib.addDays
import olygym.app.lib.capWords
import olygym.app.lib.fmtDate
import olygym.app.lib.weekDayOffset
import olygym.app.lib.weekStartOf
import olygym.app.ui.AppScreen
import olygym.app.ui.theme.OVERLINE_TRACK
import olygym.app.ui.theme.OverlineWeight
import olygym.app.ui.theme.extraColors

/**
 * Phase 0's single screen: the plan the profile holds, read-only — every week, the days it
 * schedules, and the exercises in each with their prescription. It is the proof that the state file,
 * the migration, the catalogue and the i18n pack are all wired; the real Plan screen replaces it.
 *
 * Deliberately absent: any way to change something. This phase must not be able to write a file the
 * shipping app still owns.
 */
object PlanListScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        Plan(state)
    }
}

private sealed interface PlanRow {
    data class Header(val text: String) : PlanRow
    data class Session(val day: Day, val dateIso: String) : PlanRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Plan(state: PlanState) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(I18nCore.t("Plan")) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                PlanState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PlanState.Empty -> Notice(
                    // Phase 0 has no i18n key for these two and adding one would mean editing
                    // frontend/src/locales/it.js, which this phase must not touch. The real empty
                    // states arrive with the screens they belong to.
                    title = if (state.fileExists) {
                        "No weeks in this profile yet."
                    } else {
                        "No profile on this device yet."
                    },
                    detail = state.path,
                )

                is PlanState.Failed -> Notice(title = state.reason, detail = state.path)

                is PlanState.Loaded -> WeekList(state)
            }
        }
    }
}

@Composable
private fun WeekList(state: PlanState.Loaded) {
    val weekStart = weekStartOf(state.settings.weekStart)
    val rows = remember(state.weeks, weekStart) {
        buildList {
            state.weeks.forEach { week ->
                add(PlanRow.Header(weekLabel(week)))
                week.days.forEach { day ->
                    val date = addDays(week.startIso, weekDayOffset(day.dow, weekStart).toLong())
                    add(PlanRow.Session(day, date))
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows) { row ->
            when (row) {
                is PlanRow.Header -> Overline(row.text)
                is PlanRow.Session -> SessionCard(row.day, row.dateIso)
            }
        }
    }
}

/** A group label above its cards: the web app's .sech, 12px uppercase over the group. */
@Composable
private fun Overline(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = OverlineWeight,
            letterSpacing = OVERLINE_TRACK.em,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

/**
 * One session. The surface comes from the container role rather than a shadow, which is what
 * separates it from the background — tone first, and on dark a shadow barely reads anyway.
 */
@Composable
private fun SessionCard(day: Day, dateIso: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayTitle(day),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = fmtDate(dateIso, long = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (day.ex.isEmpty()) {
                Text(
                    text = I18nCore.t("Nothing in this day."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                day.ex.forEachIndexed { index, ex ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.extraColors.hairline,
                        )
                    }
                    ExerciseLine(ex)
                }
            }
        }
    }
}

/** One prescribed exercise: the catalogue name, and sets x reps where the plan gives them. */
@Composable
private fun ExerciseLine(ex: JsonObject) {
    val id = ex.str("id").orEmpty()
    val sets = ex.int("sets")
    val reps = ex.int("reps")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Exercise names are stored lower-case and shown capitalised on the web through CSS.
            text = capWords(Catalogue.nameOf(id)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (sets != null && reps != null) {
            Text(
                text = "$sets × $reps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Notice(title: String, detail: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun dayTitle(day: Day): String =
    day.name.ifBlank { I18nCore.t(DAYN[day.dow.coerceIn(0, 6)]) }

private fun weekLabel(week: Week): String =
    week.name.ifBlank { I18nCore.t("Week of {0}", fmtDate(week.startIso, long = false)) }

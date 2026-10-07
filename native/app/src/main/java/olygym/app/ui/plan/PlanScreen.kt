package olygym.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Profile
import olygym.app.data.Week
import olygym.app.data.str
import olygym.app.lib.DAYN
import olygym.app.lib.DAYS
import olygym.app.lib.WeekStats
import olygym.app.lib.addDays
import olygym.app.lib.addWeek
import olygym.app.lib.dayCount
import olygym.app.lib.exCount
import olygym.app.lib.fmtDate
import olygym.app.lib.nextWeekStart
import olygym.app.lib.rangeLabel
import olygym.app.lib.todayISO
import olygym.app.lib.uid
import olygym.app.lib.weekDayOffset
import olygym.app.lib.weekFor
import olygym.app.lib.weekStats
import olygym.app.lib.weekStartOf
import olygym.app.lib.weeksInOrder
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.editProfile
import olygym.app.ui.sheet.planToolsSheet
import olygym.app.ui.sheet.starterPlanSheet
import olygym.app.ui.t
import olygym.app.ui.theme.CardShape
import olygym.app.ui.theme.OVERLINE_TRACK
import olygym.app.ui.theme.OverlineWeight

/**
 * The plan, as a plan rather than a log — a port of frontend/src/views/Plan.jsx.
 *
 * The week that covers today leads, its days are on it, and the rest are one line each with what
 * they planned and how much of it happened. This used to run oldest first, so the week you are in
 * sat below every week you had already trained.
 *
 * Not ported: the Competitions switch (Competitions is pushed over the tabs, like History).
 */
object PlanScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        when (val s = state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is AppState.Failed -> Notice(s.reason, s.path)

            // A missing profile file is not a dead end: no weeks is exactly what the empty state
            // and its starter plan are for, and the first write creates the file. (Phase 0 showed a
            // notice here instead, because it had nothing that could write one.)
            is AppState.Ready -> Ready(s.profile)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Ready(profile: Profile) {
    val scroll = olyAppBarScrollBehavior()
    val iso = todayISO()
    val weekStart = weekStartOf(profile.settings.weekStart)
    val ordered = weeksInOrder(profile.weeks)
    val current = weekFor(profile.weeks, iso)
    val doneDates = profile.workouts.mapNotNull { it.str("d") }.toSet()
    val open = { id: String -> Nav.to(WeekEditScreen(id)) }
    val newWeek = {
        val id = uid()
        editProfile { raw -> addWeek(raw, id, nextWeekStart(profile.weeks, iso, weekStart)) }
        open(id)
    }

    // Newest first on both sides, so the plan reads outward from today.
    val later = ordered.filter { current != null && it.startIso > current.startIso }.reversed()
    val earlier = (if (current != null) ordered.filter { it.startIso < current.startIso } else ordered).reversed()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Plan"),
                scrollBehavior = scroll,
                subtitle = t("Your weeks"),
                actions = {
                    IconButton(Glyph.UPLOAD, onClick = { planToolsSheet() })
                    IconButton(Glyph.PLUS, onClick = newWeek)
                },
            )
        },
    ) { padding ->
        if (ordered.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (current != null) {
                item {
                    Overline(t("This week"), Modifier.padding(top = 8.dp))
                    WeekHero(
                        week = current,
                        name = weekName(current),
                        stats = weekStats(current, weekStart, doneDates),
                        doneDates = doneDates,
                        weekStart = weekStart,
                        onOpen = { open(current.id) },
                    )
                }
            }
            if (later.isNotEmpty()) {
                item { Overline(t("Upcoming"), Modifier.padding(top = 12.dp)) }
                items(later) { week -> WeekRow(week, weekStart, doneDates) { open(week.id) } }
            }
            if (earlier.isNotEmpty()) {
                item {
                    Overline(
                        if (current != null) t("Earlier") else t("Your weeks"),
                        Modifier.padding(top = 12.dp),
                    )
                }
                items(earlier) { week -> WeekRow(week, weekStart, doneDates) { open(week.id) } }
            }
        }
    }
}

/** "Week of 5 Oct 2026" when the week has no name of its own. */
private fun weekName(week: Week): String =
    week.name.ifBlank { t("Week of {0}", fmtDate(week.startIso, long = false, withYear = true)) }

@Composable
private fun WeekHero(
    week: Week,
    name: String,
    stats: WeekStats,
    doneDates: Set<String>,
    weekStart: Int,
    onOpen: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = rangeLabel(week.startIso).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = OverlineWeight,
                    letterSpacing = OVERLINE_TRACK.em,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = listOf(
                    if (stats.days.isEmpty()) t("No days yet") else dayCount(stats.days.size),
                    exCount(stats.ex),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats.days.forEach { day ->
                // A week's days are dates, so "done" is per date rather than per weekday.
                val done = doneDates.contains(addDays(week.startIso, weekDayOffset(day.dow, weekStart).toLong()))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The short weekday: the full name does not fit the column, and the row already
                    // carries the day's own name.
                    Text(
                        text = t(DAYS[day.dow.coerceIn(0, 6)]),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = day.name.ifBlank { t(DAYN[day.dow.coerceIn(0, 6)]) },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = exCount(day.ex.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (done) {
                        GlyphIcon(Glyph.CHECK, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    GlyphIcon(
                        Glyph.CHEVRON_RIGHT,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                text = t("Edit this week"),
                onClick = onOpen,
                variant = ButtonVariant.TINTED,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun WeekRow(week: Week, weekStart: Int, doneDates: Set<String>, onOpen: () -> Unit) {
    val stats = weekStats(week, weekStart, doneDates)
    val planned = if (stats.days.isEmpty()) t("No days yet") else dayCount(stats.days.size)
    val done = if (stats.days.isEmpty()) "" else " · " + t("{0} of {1} done", stats.done, stats.days.size)
    ListRow(
        title = weekName(week),
        icon = Glyph.CALENDAR,
        subtitle = rangeLabel(week.startIso) + " · " + planned + done,
        accessory = Accessory.CHEVRON,
        onClick = onOpen,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            Glyph.CALENDAR,
            Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            stroke = 1.6f,
        )
        Text(
            text = t("No weeks yet."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            text = t("Load starter plan"),
            onClick = { starterPlanSheet() },
            variant = ButtonVariant.PRIMARY,
            icon = Glyph.SPARKLES,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = t("Or start from a ready-made plan."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Notice(title: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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

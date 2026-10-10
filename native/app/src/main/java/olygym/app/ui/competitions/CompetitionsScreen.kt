package olygym.app.ui.competitions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Profile
import olygym.app.data.arr
import olygym.app.data.str
import olygym.app.lib.competitionBests
import olygym.app.lib.daysUntil
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.pastMeets
import olygym.app.lib.todayISO
import olygym.app.lib.totalOf
import olygym.app.lib.upcomingMeets
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Section
import olygym.app.ui.components.Tile
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.sheet.meetDetailSheet
import olygym.app.ui.sheet.meetSheet
import olygym.app.ui.t

/**
 * The meets, kept apart from the training log. A port of frontend/src/views/Competitions.jsx: a meet
 * is an event with a total, not a session with sets, so it gets its own screen rather than a corner
 * of History. It is a second-level destination reached from Stats (and from Home while a meet is
 * ahead), which is what keeps the bottom bar at five destinations.
 *
 * The web draws a Plan/Competitions switcher at the top of both screens; here Competitions is pushed
 * over the tabs the way History is, so the bottom bar's Plan tab keeps meaning the plan.
 */
object CompetitionsScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        when (val s = state) {
            is AppState.Ready -> Competitions(s.profile)
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = t("Not ported yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Competitions(profile: Profile) {
    val S = profile.raw
    val unit = profile.settings.unit
    val scroll = olyAppBarScrollBehavior()
    val today = todayISO()
    val list = S.arr("competitions")
    val upcoming = upcomingMeets(list, today)
    val past = pastMeets(list, today)
    val bests = competitionBests(list)

    val subtitle = when {
        upcoming.isNotEmpty() -> {
            val next = upcoming[0]
            t("Next: {0}", next.str("name")?.takeIf { it.isNotEmpty() } ?: fmtDate(next.str("d").orEmpty(), long = true, withYear = true))
        }
        list.isNotEmpty() -> t(if (list.size == 1) "{0} competition" else "{0} competitions", list.size)
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Competitions"),
                subtitle = subtitle,
                scrollBehavior = scroll,
                leading = { IconButton(Glyph.CHEVRON_LEFT, t("Back"), onClick = { Nav.back() }) },
                actions = { IconButton(Glyph.PLUS, t("Add a competition"), onClick = { meetSheet(null) }) },
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
            if (list.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = t("No competitions yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = t("Add the meet you are training for, then log the attempts when it is done."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(
                        text = t("Add a competition"),
                        onClick = { meetSheet(null) },
                        variant = ButtonVariant.PRIMARY,
                        icon = Glyph.PLUS,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BestTile(t("Snatch"), bests.snatch, unit, Modifier.weight(1f))
                BestTile(t("Clean & jerk"), bests.cj, unit, Modifier.weight(1f))
                BestTile(t("Best total"), bests.total, unit, Modifier.weight(1f))
            }

            if (upcoming.isNotEmpty()) {
                MeetGroup(t("Upcoming"), upcoming, unit, today)
            }
            if (past.isNotEmpty()) {
                MeetGroup(t("Past"), past, unit, today)
            }
            Box(Modifier.padding(bottom = 18.dp))
        }
    }
}

@Composable
private fun BestTile(label: String, value: Double?, unit: String, modifier: Modifier = Modifier) {
    Tile(
        label = label,
        value = if (value == null) "—" else fmtNum(value),
        suffix = if (value == null) t("not done") else unit,
        modifier = modifier,
    )
}

@Composable
private fun MeetGroup(title: String, meets: List<JsonObject>, unit: String, today: String) {
    Overline(title, Modifier.padding(top = 14.dp, bottom = 7.dp))
    Section {
        meets.forEach { meet ->
            MeetRow(meet, unit, today) { meetDetailSheet(meet) }
        }
    }
}

@Composable
private fun MeetRow(meet: JsonObject, unit: String, today: String, onClick: () -> Unit) {
    val total = totalOf(meet)
    val up = (meet.str("d") ?: "") >= today
    val days = daysUntil(meet, today)
    val value = when {
        up -> when (days) {
            0 -> t("Today")
            1 -> t("Tomorrow")
            null -> ""
            else -> t("in {0} days", days)
        }
        total != null -> fmtNum(total) + " " + unit
        else -> t("No result")
    }
    ListRow(
        title = meet.str("name")?.takeIf { it.isNotEmpty() } ?: t("Competition"),
        icon = if (total != null) Glyph.MEDAL else Glyph.TROPHY,
        subtitle = listOfNotNull(
            fmtDate(meet.str("d").orEmpty(), long = true, withYear = true),
            meet.str("place")?.takeIf { it.isNotEmpty() },
        ).joinToString(" · "),
        value = value,
        accessory = Accessory.CHEVRON,
        onClick = onClick,
    )
}

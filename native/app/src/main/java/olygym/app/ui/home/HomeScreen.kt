package olygym.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.lib.DAYS
import olygym.app.lib.DAYN
import olygym.app.lib.addDays
import olygym.app.lib.bestWeightFor
import olygym.app.lib.effectiveDay
import olygym.app.lib.exCount
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtLongDate
import olygym.app.lib.fmtNum
import olygym.app.lib.lastBW
import olygym.app.lib.daysUntil
import olygym.app.lib.nextMeet
import olygym.app.lib.nextTrainingDay
import olygym.app.lib.streakWeeks
import olygym.app.lib.todayISO
import olygym.app.lib.weekFor
import olygym.app.lib.weekKey
import olygym.app.lib.weekStartOf
import olygym.app.ui.Nav
import olygym.app.ui.competitions.CompetitionsScreen
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Section
import olygym.app.ui.components.Tile
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.currentProfile
import olygym.app.ui.profileNow
import olygym.app.ui.sheet.bwDeltaColor
import olygym.app.ui.sheet.calendarSheet
import olygym.app.ui.sheet.goalSheet
import olygym.app.ui.sheet.weighInSheet
import olygym.app.ui.t
import olygym.app.ui.theme.CardShape
import olygym.app.ui.theme.emphasizedWeight
import olygym.app.ui.theme.extraColors
import olygym.app.ui.settings.SettingsScreen
import olygym.app.ui.workout.WorkoutScreen
import olygym.app.ui.workout.startFlow
import java.time.LocalDate

/**
 * What to do now, and a glance at how it is going: the hero answers the one question the screen is
 * for, the three tiles are the numbers, and the body-weight card is the only curve Home has.
 *
 * The port of frontend/src/views/Home.jsx, without the four things that open a screen this phase
 * does not have yet: the calendar sheet behind the tiles, the competition row, the welcome card's
 * starter-plan chooser, and the body-weight chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val profile = currentProfile() ?: return
    val scroll = olyAppBarScrollBehavior()
    val iso = todayISO()
    val today = LocalDate.now()
    val weekStart = weekStartOf(profile.settings.weekStart)
    var weekOffset by remember { mutableStateOf(0) }

    val todayDay = effectiveDay(profile.raw, iso)
    val todaySession = todayDay?.takeIf { it.ex.isNotEmpty() }
    val active = profile.active
    val doneToday = profile.workouts.lastOrNull { it.str("d") == iso }
    val next = if (active == null && todaySession == null) nextTrainingDay(profile.raw, iso) else null
    val weight = lastBW(profile.raw)?.asObj()
    val previousWeight = profile.bodyweight.dropLast(1).lastOrNull()?.num("w")
    val delta = if (weight != null && previousWeight != null) (weight.num("w") ?: 0.0) - previousWeight else null
    val unit = profile.settings.unit

    // The week the shown rail belongs to. Named for the role, not for Monday — which day that is is
    // the setting.
    val thisWeekStart = LocalDate.parse(weekKey(iso, weekStart))
    val shownStart = thisWeekStart.plusDays((weekOffset * 7).toLong())
    val shownEnd = shownStart.plusDays(6)
    val weekLabel = if (weekOffset == 0) {
        t("This week")
    } else {
        fmtDate(shownStart.toString(), long = false) + " – " + fmtDate(shownEnd.toString(), long = false)
    }

    val doneDays = profile.workouts.mapNotNull { it.str("d") }.toSet()
    val thisWeek = profile.workouts.count { weekKey(it.str("d") ?: "", weekStart) == weekKey(iso, weekStart) }
    val plannedThisWeek = weekFor(profile.weeks, iso)?.days?.size ?: 0

    val heroSub = when {
        todaySession != null -> exCount(todaySession.ex.size)
        next != null -> t("Next session: {0}, {1}", t(DAYN[next.weekday]), next.day.name)
        active != null || doneToday != null -> ""
        else -> weekLabel
    }
    val heroTitle = when {
        active != null -> t("{0} — in progress", active.str("name").orEmpty())
        doneToday != null -> if (doneToday.str("name").isNullOrBlank()) {
            t("Workout done")
        } else {
            t("{0} — done", doneToday.str("name").orEmpty())
        }
        todayDay != null -> todayDay.name
        else -> t("Rest day")
    }
    val heroGlyph = when {
        active != null -> Glyph.TIMER
        doneToday != null -> Glyph.CHECK_CIRCLE
        todaySession != null -> Glyph.DUMBBELL
        else -> Glyph.MOON
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = "OlyGym",
                scrollBehavior = scroll,
                subtitle = fmtLongDate(iso),
                actions = { IconButton(Glyph.GEAR, onClick = { Nav.to(SettingsScreen) }) },
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
            // The hero: one card, one action, and the week it belongs to inside it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t("Today") + " · " + fmtLongDate(iso),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.W700,
                                letterSpacing = 0.08.em,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(Glyph.CHEVRON_LEFT, onClick = { weekOffset -= 1 }, enabled = true)
                        IconButton(Glyph.CHEVRON_RIGHT, onClick = { weekOffset += 1 }, enabled = true)
                    }
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        GlyphIcon(
                            heroGlyph,
                            Modifier.size(26.dp),
                            tint = if (active != null || doneToday != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            stroke = 1.8f,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = heroTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (heroSub.isNotEmpty()) {
                                Text(
                                    text = heroSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val action = when {
                            active != null -> t("Resume")
                            todaySession != null -> t("Start workout")
                            else -> t("Open the plan")
                        }
                        Button(
                            text = action,
                            onClick = {
                                when {
                                    active != null -> Nav.to(WorkoutScreen)
                                    todaySession != null -> startFlow(todaySession)
                                    else -> Nav.tabHost?.invoke(1)
                                }
                            },
                            variant = ButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f),
                        )
                        if (active == null) {
                            IconButton(Glyph.RESET, onClick = { Nav.to(WorkoutScreen) })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (offset in 0..6) {
                            val date = shownStart.plusDays(offset.toLong())
                            val dateIso = date.toString()
                            val planned = effectiveDay(profile.raw, dateIso) != null
                            val done = doneDays.contains(dateIso)
                            val isToday = dateIso == iso
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { Nav.tabHost?.invoke(1) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = t(DAYS[date.dayOfWeek.value % 7]),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.W500,
                                        letterSpacing = 0.03.em,
                                    ),
                                    color = MaterialTheme.extraColors.onSurfaceDisabled,
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .size(27.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isToday) FontWeight.W600 else FontWeight.W400,
                                        ),
                                        color = if (isToday) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                done -> MaterialTheme.colorScheme.primary
                                                planned -> MaterialTheme.extraColors.onSurfaceDisabled
                                                else -> Color.Transparent
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Tile(
                    label = t("Streak"),
                    value = streakWeeks(profile.raw).toString(),
                    suffix = t("weeks"),
                    icon = Glyph.FLAME,
                    onClick = { calendarSheet() },
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = weekLabel,
                    value = thisWeek.toString() + if (plannedThisWeek > 0) "/" + plannedThisWeek else "",
                    suffix = t("sessions"),
                    icon = Glyph.CALENDAR,
                    onClick = { calendarSheet() },
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = t("Weight"),
                    value = weight?.num("w")?.let { fmtNum(it) } ?: "—",
                    suffix = if (weight != null) unit else t("not logged"),
                    icon = Glyph.SCALE,
                    valueColor = if (weight != null && delta != null) {
                        bwDeltaColor(delta, profile.settings.targetW, weight.num("w") ?: 0.0)
                    } else {
                        null
                    },
                    onClick = { weighInSheet() },
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t("Body weight"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = profile.settings.targetW?.let { fmtNum(it) } ?: t("Goal"),
                        onClick = { goalSheet() },
                        variant = ButtonVariant.PLAIN,
                        size = ButtonSize.SM,
                        icon = Glyph.TARGET,
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        text = t("Log"),
                        onClick = { weighInSheet() },
                        variant = ButtonVariant.TINTED,
                        size = ButtonSize.SM,
                        icon = Glyph.PLUS,
                    )
                }
                if (weight == null) {
                    Text(
                        text = if (profile.settings.weighIn) {
                            t("No entries yet — log your weight to start the curve. It's also asked before every workout.")
                        } else {
                            t("No entries yet — log your weight to start the curve.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = fmtNum(weight.num("w") ?: 0.0),
                            // The web's .card .big: the body weight at the display role's Emphasized weight.
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = emphasizedWeight(MaterialTheme.typography.headlineMedium.fontWeight),
                                letterSpacing = (-0.026).em,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        if (delta != null && delta != 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp),
                            ) {
                                GlyphIcon(
                                    if (delta > 0) Glyph.ARROW_UP else Glyph.ARROW_DOWN,
                                    Modifier.size(12.dp),
                                    tint = bwDeltaColor(delta, profile.settings.targetW, weight.num("w") ?: 0.0),
                                )
                                Text(
                                    text = fmtNum(Math.abs(delta)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bwDeltaColor(delta, profile.settings.targetW, weight.num("w") ?: 0.0),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = fmtDate(weight.str("d").orEmpty(), long = true),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    val goal = profile.settings.targetW
                    if (goal != null) {
                        val gap = Math.abs(goal - (weight.num("w") ?: 0.0))
                        Text(
                            text = t("Goal") + " " + fmtNum(goal) + " " + unit + " · " + if (gap < 0.05) {
                                t("reached!")
                            } else {
                                t(
                                    if (goal > (weight.num("w") ?: 0.0)) "{0} to gain" else "{0} to lose",
                                    fmtNum(gap) + " " + unit,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extraColors.yellow,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // A meet ahead is the only dated event the week strip cannot show yet, so it gets a row
            // of its own under the numbers — a glance, and the door to the competitions screen.
            val meet = nextMeet(profile.raw["competitions"], todayISO())
            if (meet != null) {
                val meetDays = daysUntil(meet, todayISO())
                Section(modifier = Modifier.padding(top = 14.dp)) {
                    ListRow(
                        title = meet.str("name")?.takeIf { it.isNotEmpty() } ?: t("Competition"),
                        icon = Glyph.TROPHY,
                        subtitle = listOfNotNull(
                            fmtDate(meet.str("d").orEmpty(), long = true, withYear = true),
                            meet.str("place")?.takeIf { it.isNotEmpty() },
                        ).joinToString(" · "),
                        value = when (meetDays) {
                            0 -> t("Today")
                            1 -> t("Tomorrow")
                            null -> null
                            else -> t("in {0} days", meetDays)
                        },
                        accessory = Accessory.CHEVRON,
                        onClick = { Nav.to(CompetitionsScreen) },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

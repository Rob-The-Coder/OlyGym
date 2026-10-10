package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.str
import olygym.app.lib.MONTHS_LONG
import olygym.app.lib.effectiveDay
import olygym.app.lib.fmtDate
import olygym.app.lib.monthSummary
import olygym.app.lib.todayISO
import olygym.app.lib.weekStartOf
import olygym.app.lib.workoutDates
import olygym.app.ui.Nav
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.components.WorkoutRow
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.ui
import java.time.LocalDate

/*
 * The month at a glance, and the list of a day that holds more than one session — a port of Calendar
 * and DaySessions in frontend/src/sheets.jsx.
 *
 * The grid is the one the backfill's date picker already draws (DateAndTimeSheets.kt), so a day's dots
 * mean the same thing in both; what the calendar adds is what the month came to, and what a tap on a
 * day means. That depends on which side of today the day is: one session opens it, several open the
 * list of them, a day already gone opens the backfill dated to it, and a day still to come goes to the
 * plan — only this screen knows which date was meant, so it hands the date over rather than sending the
 * reader to the plan to find it.
 */

/** `start` is an ISO date when the Stats heatmap opens the calendar on one, and null for today's month. */
fun calendarSheet(start: String? = null): Long = ui.openSheet { close -> Calendar(start, close) }

@Composable
private fun Calendar(start: String?, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val weekStart = weekStartOf(profile.settings.weekStart)
    val workouts = profile.raw.arr("workouts").mapNotNull { it.asObj() }
    val byDay = workouts.groupBy { it.str("d").orEmpty() }
    val today = todayISO()
    val thisMonth = LocalDate.now().withDayOfMonth(1)
    var cursor by remember {
        // The web parses the ISO form at noon so a day near the 1st cannot land in the previous month
        // west of UTC; LocalDate has no clock in it, so the plain parse is enough here.
        mutableStateOf((start?.let { LocalDate.parse(it) } ?: LocalDate.now()).withDayOfMonth(1))
    }
    val monthKey = cursor.year.toString() + "-" + cursor.monthValue.toString().padStart(2, '0')
    val inMonth = workouts.filter { it.str("d").orEmpty().startsWith(monthKey) }
    val onNowMonth = cursor.year == thisMonth.year && cursor.monthValue == thisMonth.monthValue

    val openDay: (String) -> Unit = { iso ->
        val onDay = byDay[iso].orEmpty()
        when {
            onDay.size == 1 -> {
                close()
                workoutDetailSheet(onDay[0])
            }
            onDay.size > 1 -> {
                close()
                daySessionsSheet(iso, onDay)
            }
            iso < today -> {
                close()
                logPastWorkoutSheet(iso)
            }
            else -> {
                close()
                Nav.tabHost?.invoke(1)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(Glyph.CHEVRON_LEFT, t("Previous month"), onClick = { cursor = cursor.minusMonths(1) })
            Text(
                text = t(MONTHS_LONG[cursor.monthValue - 1]) + " " + cursor.year.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(Glyph.CHEVRON_RIGHT, t("Next month"), onClick = { cursor = cursor.plusMonths(1) })
        }
        Text(
            text = if (inMonth.isEmpty()) t("No workouts this month") else monthSummary(inMonth, unit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Six months back there was no way forward again but six taps on the same arrow.
        if (!onNowMonth) {
            Button(
                text = t("Back to this month"),
                onClick = { cursor = thisMonth },
                variant = ButtonVariant.GHOST,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        MonthGrid(
            year = cursor.year,
            month = cursor.monthValue,
            weekStart = weekStart,
            withWorkouts = workoutDates(profile.raw.arr("workouts")),
            planned = { iso -> effectiveDay(profile.raw, iso) != null },
            onPick = openDay,
        )
        GridLegend()
        Text(
            text = t("Tap a day you trained for it · a past day to log it · a day to come to plan it"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** A day holding more than one session: the web built this inline in the grid cell. */
fun daySessionsSheet(iso: String, dayws: List<JsonObject>): Long =
    ui.openSheet { close -> DaySessions(iso, dayws, close) }

@Composable
private fun DaySessions(iso: String, dayws: List<JsonObject>, close: () -> Unit) {
    val unit = currentProfile()?.settings?.unit ?: return
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(fmtDate(iso, long = true))
        SheetNote(t(if (dayws.size == 1) "{0} workout" else "{0} workouts", dayws.size))
        Section {
            dayws.forEachIndexed { index, w ->
                if (index > 0) RowDivider()
                WorkoutRow(w, unit, onClick = {
                    close()
                    workoutDetailSheet(w)
                })
            }
        }
    }
}

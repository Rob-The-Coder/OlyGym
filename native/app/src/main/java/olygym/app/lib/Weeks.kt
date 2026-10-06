package olygym.app.lib

import java.time.LocalDate
import olygym.app.data.Day
import olygym.app.data.Week

/*
 * The dated weeks model — a port of frontend/src/lib/weeks.js. A week is one concrete calendar
 * week that never repeats, so the week you are in is the week whose startIso covers today.
 */

/** A date `n` days away. */
fun addDays(iso: String, n: Long): String = LocalDate.parse(iso).plusDays(n).toString()

/** A copy of the weeks, oldest first. YYYY-MM-DD string compare is chronological. */
fun weeksInOrder(weeks: List<Week>): List<Week> = weeks.sortedBy { it.startIso }

/**
 * The week `iso` falls in, or null — covering from its startIso up to, but not including, the day
 * seven later. Only the date on the week matters, so a gap between weeks is a real gap.
 *
 * Should two weeks ever overlap, the one starting latest wins: it is the more recently written
 * plan, and "which week am I in" has to have one answer either way.
 */
fun weekFor(weeks: List<Week>, iso: String): Week? {
    var found: Week? = null
    for (w in weeksInOrder(weeks)) {
        if (w.startIso <= iso && iso < addDays(w.startIso, 7)) found = w
    }
    return found
}

/**
 * The day planned for a date — the day itself, or null for a rest day, a gap between weeks, or a
 * weekday this week leaves out. When a weekday carries several days the first is the day.
 */
fun dayFor(weeks: List<Week>, iso: String): Day? {
    val w = weekFor(weeks, iso) ?: return null
    val dow = LocalDate.parse(iso).dayOfWeek.value % 7
    return w.days.firstOrNull { it.dow == dow }
}

/** getDay() indices in display order for the profile's first weekday. */
fun weekDates(week: Week, weekStart: Int): List<Pair<Day, String>> = week.days.map { day ->
    day to addDays(week.startIso, weekDayOffset(day.dow, weekStart).toLong())
}

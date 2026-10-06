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

/* ------------------------------------------------------------------ the plan list --
 * The reads Plan and WeekEdit do on top of the model above, a port of the same inline helpers in
 * frontend/src/views/Plan.jsx and WeekEdit.jsx.
 */

/**
 * Where the next week begins: seven days after the last one we have, or this week's first day when
 * there are none. A week is one concrete calendar week, so a new one follows the plan instead of
 * repeating it.
 */
fun nextWeekStart(weeks: List<Week>, todayIso: String, weekStart: Int): String {
    val latest = weeksInOrder(weeks).lastOrNull()
    return if (latest != null) addDays(latest.startIso, 7)
    else isoOf(startOfWeek(todayIso, weekStartOf(weekStart)))
}

/**
 * The week's days in the profile's weekday order — [1..6, 0] for a Monday start. Several days may
 * share a weekday (the migration emits one per scheduled routine) and they keep their own order.
 */
fun daysInOrder(week: Week, weekStart: Int): List<Day> =
    weekOrder(weekStart).flatMap { d -> week.days.filter { it.dow == d } }

/**
 * The same list paired with each day's index in `week.days`, so an edit still finds the right day
 * after the display order changes.
 */
fun dayEntries(week: Week, weekStart: Int): List<Pair<Day, Int>> =
    weekOrder(weekStart).flatMap { d ->
        week.days.mapIndexedNotNull { index, day -> if (day.dow == d) day to index else null }
    }

/** The first weekday this week leaves free — where "Add day" lands. */
fun firstFreeDow(week: Week, weekStart: Int): Int {
    val taken = week.days.map { it.dow }.toSet()
    return weekOrder(weekStart).firstOrNull { it !in taken } ?: 0
}

/** What the Plan list says about one week, before it is opened. */
data class WeekStats(val days: List<Day>, val ex: Int, val done: Int)

/**
 * A week's days and their counts. A week's days are dates, so "done" is per date rather than per
 * weekday: the same Wednesday in two weeks is two different sessions.
 */
fun weekStats(week: Week, weekStart: Int, doneDates: Set<String>): WeekStats {
    val days = daysInOrder(week, weekStart)
    return WeekStats(
        days = days,
        ex = days.sumOf { it.ex.size },
        done = days.count { doneDates.contains(addDays(week.startIso, weekDayOffset(it.dow, weekStart).toLong())) },
    )
}

/**
 * "5 Oct – 11 Oct". Two weeks can both be unnamed, so the dates are what tells them apart. The
 * web builds it from two Date objects and a short month name, which is two fmtDate calls here.
 */
fun rangeLabel(startIso: String): String =
    fmtDate(startIso, long = false) + " – " + fmtDate(addDays(startIso, 6), long = false)

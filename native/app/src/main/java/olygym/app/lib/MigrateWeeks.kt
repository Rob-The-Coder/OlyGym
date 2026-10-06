package olygym.app.lib

import java.time.LocalDate
import olygym.app.data.Day
import olygym.app.data.Routine
import olygym.app.data.Week

/*
 * One-way, additive migration from the repeating plan (routines + week) to the dated weeks model.
 * A port of frontend/src/lib/migrate-weeks.js: it reads the old fields and returns only the new
 * weeks array, nothing on the input is mutated, and running it twice over the same state gives the
 * same shape bar the fresh ids.
 */

/**
 * Which weekday the i-th day of an imported week lands on: Mon/Wed/Fri first — the days the coach
 * import schedules a fresh week on — then the remaining weekdays in order. Wraps past seven, which
 * only matters for a week with more than seven routines.
 */
private val WEEK_ORDER = intArrayOf(1, 3, 5, 2, 4, 6, 0)

fun weekDayForPosition(i: Int): Int = WEEK_ORDER[i % 7]

/** How reviewWeek names a coached day: "Giorno 3 · 23-29 marzo". A hand-edited name may use a pipe. */
private val COACH_DAY = Regex("^Giorno\\s+(\\d+)\\s*[·|]\\s*(.+)$", RegexOption.IGNORE_CASE)

private fun dayOf(r: Routine, dow: Int): Day = Day(
    dow = dow,
    name = r.name,
    ex = r.ex,
    excludeFromProgression = if (r.excludeFromProgression == true) true else null,
)

/**
 * Whether a profile still needs its repeating plan read into dated weeks: it has a plan of the old
 * shape and no weeks yet. The store asks this on load, so the old-field read lives here.
 */
fun needsWeekMigration(weeks: List<Week>, routines: List<Routine>): Boolean =
    weeks.isEmpty() && routines.isNotEmpty()

/**
 * The old plan -> the weeks array. `now` is injected so a test can pin "today"; production passes
 * the clock. Three groups, in the order they are looked for:
 *
 *   1. the routines scheduled in the old `week` — one week starting on the current week's first
 *      day, each scheduled routine as a day on its own weekday.
 *   2. the coach's imported routines (named "Giorno N · label", not scheduled) — one week per
 *      label, on consecutive past weeks ending with the one before the current week. The routine
 *      carries a day number, not a date, so the label order is all there is.
 *   3. everything left over — one week after the current one, so nothing is silently dropped.
 *
 * The result is sorted by startIso. Week ids are fresh, so re-running this cannot collide.
 */
fun migrateToWeeks(
    routines: List<Routine>,
    week: Map<String, List<String>>,
    weekStart: Int?,
    now: LocalDate,
    newId: () -> String = { uid() },
): List<Week> {
    val byId = routines.associateBy { it.id }
    val currentMonday = isoOf(startOfWeek(isoOf(now), weekStartOf(weekStart)))
    val out = mutableListOf<Week>()

    // 1. The schedule the old week field holds: weekday -> routine ids, in the order they were put
    //    on the day.
    val scheduled = mutableSetOf<String>()
    val currentDays = mutableListOf<Day>()
    for ((key, ids) in week) {
        val dow = key.trim().toIntOrNull() ?: continue
        if (dow !in 0..6) continue
        for (id in ids) {
            val r = byId[id] ?: continue
            scheduled += id
            currentDays += dayOf(r, dow)
        }
    }
    if (currentDays.isNotEmpty()) out += Week(id = newId(), startIso = currentMonday, days = currentDays)

    // 2. The coach's weeks. Grouped by the label he wrote ("23-29 marzo"), each label one week.
    val groups = linkedMapOf<String, MutableList<Pair<Int, Routine>>>()
    for (r in routines) {
        if (r.id in scheduled) continue
        val m = COACH_DAY.matchEntire(r.name.trim()) ?: continue
        val label = m.groupValues[2].trim()
        groups.getOrPut(label) { mutableListOf() } += (m.groupValues[1].toInt() to r)
    }
    // A sorted array's index is the group's position, which is what paces the past weeks below.
    val labels = groups.keys.sorted()
    labels.forEachIndexed { gi, label ->
        val days = groups.getValue(label)
            .sortedBy { it.first }
            .mapIndexed { i, (_, r) -> dayOf(r, weekDayForPosition(i)) }
        out += Week(
            id = newId(),
            startIso = addDays(currentMonday, -7L * (labels.size - gi).toLong()),
            name = label,
            days = days,
        )
    }

    // 3. Anything not scheduled and not the coach's: one week, so it is still reachable.
    val leftovers = routines.filter { it.id !in scheduled && !COACH_DAY.containsMatchIn(it.name.trim()) }
    if (leftovers.isNotEmpty()) {
        out += Week(
            id = newId(),
            startIso = addDays(currentMonday, 7),
            days = leftovers.mapIndexed { i, r -> dayOf(r, weekDayForPosition(i)) },
        )
    }

    return out.sortedBy { it.startIso }
}

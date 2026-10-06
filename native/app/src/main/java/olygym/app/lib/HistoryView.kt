package olygym.app.lib

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.str

/*
 * The History screen's readings, lifted out of frontend/src/views/History.jsx and MonthGrid in
 * sheets.jsx so the list, its headings and the calendar's cells are values a test can pin.
 */

/** One month of the log, newest first, with the sessions it holds. */
data class HistoryMonth(val key: String, val items: List<JsonObject>)

/**
 * The workouts a query keeps: the session's own name, or any exercise it logged. An exercise the
 * catalogue no longer knows still matches by the name stored on the entry.
 */
fun historySearch(workouts: JsonArray, query: String): List<JsonObject> {
    val q = query.trim().lowercase()
    val all = workouts.mapNotNull { it.asObj() }
    if (q.isEmpty()) return all.reversed()
    return all.reversed().filter { w ->
        (w.str("name") ?: "").lowercase().contains(q) || w.arr("entries").any { element ->
            val entry = element.asObj()
            val id = entry?.str("id") ?: return@any false
            val name = if (Catalogue[id] != null) Catalogue.nameOf(id) else (entry.str("n") ?: id)
            name.lowercase().contains(q)
        }
    }
}

/**
 * Consecutive months rather than a map: the list is already newest first, so a heading per change is
 * cheaper than bucketing and sorting again.
 */
fun historyMonths(workouts: List<JsonObject>): List<HistoryMonth> {
    val out = mutableListOf<HistoryMonth>()
    for (w in workouts) {
        val key = (w.str("d") ?: "").take(7)
        val last = out.lastOrNull()
        if (last != null && last.key == key) {
            out[out.size - 1] = last.copy(items = last.items + w)
        } else {
            out.add(HistoryMonth(key, listOf(w)))
        }
    }
    return out
}

/** "October 2026", in the app's language. */
fun monthLabel(key: String): String =
    runCatching {
        LocalDate.parse(key + "-01").format(DateTimeFormatter.ofPattern("LLLL yyyy", I18nCore.dateLocale()))
    }.getOrDefault(key)

/** The four totals a list cannot give at a glance: workouts, sets, volume, PRs. */
data class HistoryTotals(val workouts: Int, val sets: Int, val volume: Double, val prs: Int)

fun historyTotals(workouts: JsonArray): HistoryTotals {
    var sets = 0
    var volume = 0.0
    var prs = 0
    workouts.forEach { element ->
        val w = element.asObj() ?: return@forEach
        sets += setsDone(w)
        // Through the same helper the workout row reads, rather than the stored vol: a workout
        // logged before that field existed still has a volume, it was just never written down.
        volume += workoutVolume(w)
        prs += w.arr("prs").size
    }
    return HistoryTotals(workouts.size, sets, volume, prs)
}
/** One cell of the month grid: a day, or null for the blanks before the 1st. */
data class MonthCell(
    val iso: String,
    val day: Int,
    val hasWorkouts: Boolean,
    val planned: Boolean,
    val today: Boolean,
    val selected: Boolean,
    val disabled: Boolean,
)

/**
 * The month laid out in the profile's own weeks, five to six rows of seven. `disabledAfter` is the
 * latest day a caller will accept — the backfill's max is today, so tomorrow is not tappable.
 */
fun monthCells(
    year: Int,
    month: Int,
    weekStart: Int,
    todayIso: String,
    selected: String? = null,
    disabledAfter: String? = null,
    withWorkouts: Set<String> = emptySet(),
    planned: (String) -> Boolean = { false },
): List<MonthCell?> {
    val first = LocalDate.of(year, month, 1)
    val offset = weekDayOffset(first.dayOfWeek.value % 7, weekStart)
    val cells = mutableListOf<MonthCell?>()
    repeat(offset) { cells.add(null) }
    for (day in 1..first.lengthOfMonth()) {
        val iso = first.withDayOfMonth(day).toString()
        cells.add(
            MonthCell(
                iso = iso,
                day = day,
                hasWorkouts = withWorkouts.contains(iso),
                planned = planned(iso),
                today = iso == todayIso,
                selected = iso == selected,
                disabled = disabledAfter != null && iso > disabledAfter,
            ),
        )
    }
    return cells
}

/** The line above the calendar: what that month added up to, or that it was empty. */
fun monthSummary(month: List<JsonObject>, unit: String): String {
    if (month.isEmpty()) return ""
    val minutes = month.sumOf { w ->
        val start = w.num("start") ?: 0.0
        val end = w.num("end") ?: start
        maxOf(0.0, (end - start) / 60000.0)
    }
    val volume = month.sumOf { workoutVolume(it) }
    val head = I18nCore.t(if (month.size == 1) "{0} workout" else "{0} workouts", month.size)
    return listOf(head, fmtDur(minutes.toLong() * 60000L), fmtVol(volume, unit)).joinToString(" · ")
}

/** The ISO dates this profile has trained on — what the grid's dots are drawn from. */
fun workoutDates(workouts: JsonArray): Set<String> =
    workouts.mapNotNull { it.asObj()?.str("d") }.toSet()

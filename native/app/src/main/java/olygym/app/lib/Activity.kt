package olygym.app.lib

import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import olygym.app.data.asArr
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.str

/*
 * The activity heatmap's readings, lifted out of frontend/src/components/Heatmap.jsx so the shading
 * and the grid are values a test can pin. The drawing is Compose (ui/chart/Heatmap.kt).
 */

/** One day of training: how many sessions, how much volume, how many minutes. */
data class ActivityDay(val n: Int, val vol: Double, val minutes: Int)

/** Per-day totals, keyed by ISO date. A workout with no clock counts zero minutes, not negative. */
fun activityByDay(workouts: JsonElement?): Map<String, ActivityDay> {
    val out = linkedMapOf<String, ActivityDay>()
    (workouts.asArr() ?: return out).forEach { element ->
        val w = element.asObj() ?: return@forEach
        val d = w.str("d") ?: return@forEach
        val previous = out[d] ?: ActivityDay(0, 0.0, 0)
        val start = w.num("start") ?: 0.0
        val end = w.num("end") ?: start
        val minutes = maxOf(0, Math.round((end - start) / 60000.0).toInt())
        out[d] = ActivityDay(previous.n + 1, previous.vol + (w.num("vol") ?: 0.0), previous.minutes + minutes)
    }
    return out
}

/**
 * The three shading thresholds: the 25th, 50th and 75th percentile of the *non-zero* day lengths,
 * so one long session does not flatten a month of short ones. All zero when nothing has a clock.
 */
fun activityThresholds(byDay: Map<String, ActivityDay>): Triple<Int, Int, Int> {
    val mins = byDay.values.map { it.minutes }.filter { it > 0 }.sorted()
    if (mins.isEmpty()) return Triple(0, 0, 0)
    val at = { p: Double -> mins[minOf(mins.size - 1, (p * mins.size).toInt())] }
    return Triple(at(0.25), at(0.5), at(0.75))
}

/**
 * The shade for one day: 0 is nothing, and a session with no clock is a 1 rather than a 0 — it
 * happened, the app just does not know how long. Then the three thresholds, hardest last.
 */
fun activityLevel(day: ActivityDay?, thresholds: Triple<Int, Int, Int>): Int {
    if (day == null) return 0
    val (t1, t2, t3) = thresholds
    return when {
        day.minutes <= 0 -> 1
        day.minutes >= t3 -> 4
        day.minutes >= t2 -> 3
        day.minutes >= t1 -> 2
        else -> 1
    }
}

/** The first day of the grid: 52 whole weeks before the week the profile is in. */
fun activityGridStart(todayIso: String, weekStart: Int): LocalDate =
    LocalDate.parse(todayIso)
        .minusDays(weekDayOffset(jsDayOfWeek(todayIso), weekStart).toLong())
        .minusDays(52L * 7)

/** Monday 0 … Sunday 6, the same getDay() index the state stores. */
private fun jsDayOfWeek(iso: String): Int = LocalDate.parse(iso).dayOfWeek.value % 7

/**
 * The label above each of the 53 columns, or null: the month's short name where a column starts a
 * new month inside its first week, and nothing for the last two columns (a label there has no room).
 */
fun activityMonthLabels(start: LocalDate, columns: Int = 53): List<String?> {
    val out = mutableListOf<String?>()
    var lastMonth = -1
    for (col in 0 until columns) {
        val colStart = start.plusDays(col.toLong() * 7)
        val month = colStart.monthValue
        val show = month != lastMonth && colStart.dayOfMonth <= 7 && col < columns - 2
        out.add(if (show) MONTHS[month - 1] else null)
        if (colStart.dayOfMonth <= 7) lastMonth = month
    }
    return out
}

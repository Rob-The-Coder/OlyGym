package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The activity heatmap's readings. Heatmap.jsx ships no test of its own; these pin the three rules
 * the shading depends on — a session with no clock still counts, the thresholds ignore the zero
 * days, and the grid is 52 whole weeks back from the profile's own week.
 */
private fun workout(d: String, start: Double = 0.0, end: Double = 0.0, vol: Double = 0.0) =
    js("d" to d, "start" to start, "end" to end, "vol" to vol)

class ActivityTest {

    @Test
    fun `days add up, and a workout with no clock counts zero minutes`() {
        val byDay = activityByDay(
            JsonArray(
                listOf(
                    workout("2026-10-05", 1000.0, 1000.0 + 45 * 60000.0, 1200.0),
                    workout("2026-10-05", 2000.0, 2000.0 + 15 * 60000.0, 300.0),
                    workout("2026-10-06", 0.0, 0.0, 500.0),
                ),
            ),
        )
        assertEquals(2, byDay["2026-10-05"]?.n)
        assertEquals(60, byDay["2026-10-05"]?.minutes)
        assertEquals(1500.0, byDay["2026-10-05"]?.vol ?: 0.0, 1e-9)
        assertEquals(0, byDay["2026-10-06"]?.minutes)
    }

    @Test
    fun `the thresholds are the quartiles of the days that have a clock`() {
        val days = listOf(10, 20, 30, 40).mapIndexed { i, m ->
            "2026-10-0" + (i + 1) to ActivityDay(1, 0.0, m)
        }.toMap() + ("2026-10-09" to ActivityDay(1, 0.0, 0))
        // The web indexes with floor(p * n) over the sorted, non-zero minutes: 1, 2 and 3 of 4.
        assertEquals(Triple(20, 30, 40), activityThresholds(days))
        assertEquals(Triple(0, 0, 0), activityThresholds(mapOf("2026-10-09" to ActivityDay(1, 0.0, 0))))
    }

    @Test
    fun `a day with no clock is a one, not a zero, and the rest take the thresholds`() {
        val t = Triple(10, 20, 30)
        assertEquals(0, activityLevel(null, t))
        assertEquals(1, activityLevel(ActivityDay(1, 0.0, 0), t))
        assertEquals(1, activityLevel(ActivityDay(1, 0.0, 5), t))
        assertEquals(2, activityLevel(ActivityDay(1, 0.0, 10), t))
        assertEquals(3, activityLevel(ActivityDay(1, 0.0, 20), t))
        assertEquals(4, activityLevel(ActivityDay(1, 0.0, 30), t))
    }

    @Test
    fun `the grid starts 52 whole weeks before the week the profile is in`() {
        // Thursday 8 October 2026, Monday-first: that week began on 5 October.
        assertEquals(java.time.LocalDate.parse("2025-10-06"), activityGridStart("2026-10-08", MONDAY))
        // Sunday-first: the week began on 4 October.
        assertEquals(java.time.LocalDate.parse("2025-10-05"), activityGridStart("2026-10-08", SUNDAY))
    }

    @Test
    fun `a month is labelled once, and never in the last two columns`() {
        val start = java.time.LocalDate.parse("2025-10-06")
        val labels = activityMonthLabels(start)
        assertEquals(53, labels.size)
        assertEquals(listOf("Oct", "Nov", "Dec"), labels.filterNotNull().take(3))
        assertEquals(null, labels[52])
        assertEquals(null, labels[51])
    }
}

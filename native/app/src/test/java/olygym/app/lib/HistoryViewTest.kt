package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The History screen's readings, which History.jsx and MonthGrid keep inline: what a query keeps,
 * how the list is headed, what the four totals add up to, and where the calendar's cells fall.
 */
private fun hw(d: String, name: String, ids: List<String>, vol: Double = 1000.0, prs: Int = 0) = js(
    "id" to d + name,
    "d" to d,
    "name" to name,
    "vol" to vol,
    "start" to 0,
    "end" to 3_600_000,
    "prs" to List(prs) { "ex" },
    "entries" to ids.map { js("id" to it) },
)

private fun hist(vararg workouts: kotlinx.serialization.json.JsonObject) = JsonArray(workouts.toList())

class HistoryViewTest {

    @Test
    fun `an empty query keeps the whole list, newest first`() {
        val list = historySearch(hist(hw("2026-10-02", "Snatch Day", listOf("wl58")), hw("2026-10-05", "Clean Day", listOf("wl59"))), "")
        assertEquals(listOf("Clean Day", "Snatch Day"), list.map { it["name"]?.toString()?.trim('"') })
        assertEquals(emptyList<String>(), historySearch(hist(), "").map { it.toString() })
    }

    @Test
    fun `a query matches the session's name or an exercise it logged`() {
        val list = hist(
            hw("2026-10-02", "Snatch Day", listOf("wl58")),
            hw("2026-10-05", "Clean & Jerk Day", listOf("wl59")),
        )
        assertEquals(1, historySearch(list, "snatch").size)
        assertEquals(1, historySearch(list, "clean").size)
        assertEquals(0, historySearch(list, "deadlift").size)
    }

    @Test
    fun `consecutive months are one heading, in order`() {
        val list = historySearch(
            hist(
                hw("2026-09-28", "A", listOf("wl58")),
                hw("2026-10-02", "B", listOf("wl58")),
                hw("2026-10-05", "C", listOf("wl58")),
            ),
            "",
        )
        val months = historyMonths(list)
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.key })
        assertEquals(2, months[0].items.size)
        assertEquals(1, months[1].items.size)
    }

    @Test
    fun `the four totals come from the helpers the rows read`() {
        val totals = historyTotals(
            hist(
                js(
                    "d" to "2026-10-05", "vol" to 5000.0, "prs" to listOf("wl58"),
                    "entries" to listOf(
                        js("id" to "wl58", "sets" to listOf(js("w" to 100.0, "r" to 5, "done" to true))),
                        js("id" to "wl59", "sets" to listOf(js("w" to 50.0, "r" to 5, "done" to true))),
                    ),
                ),
                js(
                    "d" to "2026-10-02", "vol" to 0.0,
                    "entries" to listOf(js("id" to "wl59", "sets" to listOf(js("w" to 100.0, "r" to 3, "done" to true)))),
                ),
            ),
        )
        assertEquals(2, totals.workouts)
        assertEquals(3, totals.sets)
        // Measured from the sets, not the stored vol: 100x5 + 50x5 + 100x3 is 1050, where the first
        // workout's stored 5000 is what the field happens to hold.
        assertEquals(1050.0, totals.volume, 1e-9)
        assertEquals(1, totals.prs)
    }

    @Test
    fun `the month grid starts on the profile's weekday and disables the future`() {
        // 1 October 2026 is a Thursday: three blanks before it in a Monday-first week.
        val monday = monthCells(2026, 10, MONDAY, "2026-10-05", selected = "2026-10-02", disabledAfter = "2026-10-05")
        assertNull(monday[0])
        assertNull(monday[2])
        assertEquals("2026-10-01", monday[3]?.iso)
        assertEquals(3 + 31, monday.size)
        assertEquals(true, monday.first { it?.day == 5 }?.today)
        assertEquals(true, monday.first { it?.day == 2 }?.selected)
        assertEquals(false, monday.first { it?.day == 5 }?.disabled)
        assertEquals(true, monday.first { it?.day == 6 }?.disabled)
        // Sunday-first: four blanks before the 1st.
        val sunday = monthCells(2026, 10, SUNDAY, "2026-10-05")
        assertNull(sunday[3])
        assertEquals("2026-10-01", sunday[4]?.iso)
    }

    @Test
    fun `the cells carry the days that were trained and the days that were planned`() {
        val cells = monthCells(
            2026, 10, MONDAY, "2026-10-05",
            withWorkouts = setOf("2026-10-02"),
            planned = { it == "2026-10-07" },
        )
        assertEquals(true, cells.first { it?.day == 2 }?.hasWorkouts)
        assertEquals(false, cells.first { it?.day == 3 }?.hasWorkouts)
        assertEquals(true, cells.first { it?.day == 7 }?.planned)
        assertEquals(
            setOf("2026-10-02", "2026-10-05"),
            workoutDates(hist(hw("2026-10-02", "A", listOf("wl58")), hw("2026-10-05", "B", listOf("wl58")))),
        )
    }

    @Test
    fun `the month summary names the sessions, the time and the volume, and is empty otherwise`() {
        assertEquals("", monthSummary(emptyList(), "kg"))
        val summary = monthSummary(
            listOf(hw("2026-10-02", "A", listOf("wl58")), hw("2026-10-05", "B", listOf("wl58"), vol = 2000.0)),
            "kg",
        )
        assertTrue(summary.contains("2"))
        assertTrue(summary.contains("kg"))
        // An hour each, so two hours in all.
        assertTrue(summary.contains("2h"))
        val label = monthLabel("2026-10")
        assertTrue(label.contains("2026"))
    }
}

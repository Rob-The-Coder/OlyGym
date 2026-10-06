package olygym.app.lib

import olygym.app.data.Day
import olygym.app.data.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * weeks.js ships no test of its own; its behaviour is pinned here, including the two rules that are
 * easy to get wrong: an overlap resolves to the latest week, and a weekday carrying several days
 * resolves to the first.
 */
private fun week(id: String, startIso: String, vararg dows: Int) =
    Week(id = id, startIso = startIso, days = dows.map { Day(dow = it) })

class WeeksTest {

    @Test
    fun `addDays crosses a month and a year boundary`() {
        assertEquals("2026-04-01", addDays("2026-03-25", 7))
        assertEquals("2026-02-28", addDays("2026-03-01", -1))
        assertEquals("2026-01-01", addDays("2025-12-25", 7))
        assertEquals("2026-03-25", addDays("2026-03-25", 0))
    }

    @Test
    fun `weeksInOrder is chronological, not insertion order`() {
        val weeks = listOf(week("b", "2026-03-30"), week("a", "2026-03-23"))
        assertEquals(listOf("a", "b"), weeksInOrder(weeks).map { it.id })
    }

    @Test
    fun `weekFor covers seven days from its startIso, and the day before it is not its own`() {
        val weeks = listOf(week("a", "2026-03-23"))
        assertEquals("a", weekFor(weeks, "2026-03-23")?.id)
        assertEquals("a", weekFor(weeks, "2026-03-29")?.id)
        assertNull(weekFor(weeks, "2026-03-30"))
        assertNull(weekFor(weeks, "2026-03-22"))
    }

    @Test
    fun `a gap between weeks is a real gap`() {
        val weeks = listOf(week("a", "2026-03-16"), week("b", "2026-04-06"))
        assertNull(weekFor(weeks, "2026-03-25"))
        assertEquals("a", weekFor(weeks, "2026-03-16")?.id)
        assertEquals("b", weekFor(weeks, "2026-04-06")?.id)
    }

    @Test
    fun `an overlap resolves to the more recently written plan`() {
        val weeks = listOf(week("old", "2026-03-16"), week("new", "2026-03-23"))
        assertEquals("new", weekFor(weeks, "2026-03-25")?.id)
    }

    @Test
    fun `dayFor finds the day on that weekday, and null on a rest day`() {
        val weeks = listOf(week("a", "2026-03-23", 1, 3))
        // Wednesday 25 March is dow 3, Thursday 26 is dow 4 (not in the week).
        assertEquals(3, dayFor(weeks, "2026-03-25")?.dow)
        assertEquals(1, dayFor(weeks, "2026-03-23")?.dow)
        assertNull(dayFor(weeks, "2026-03-26"))
        assertNull(dayFor(weeks, "2026-04-01"))
    }

    @Test
    fun `a weekday carrying several days gives the first`() {
        val first = Day(dow = 2, name = "A")
        val second = Day(dow = 2, name = "B")
        val w = Week(id = "a", startIso = "2026-03-23", days = listOf(first, second))
        // Tuesday 24 March.
        assertSame(first, dayFor(listOf(w), "2026-03-24"))
    }

    @Test
    fun `weekDates pairs each day with the date it falls on, from the profile's week start`() {
        val w = week("a", "2026-03-23", 1, 0)
        assertEquals(
            listOf("2026-03-23" to 1, "2026-03-29" to 0),
            weekDates(w, MONDAY).map { it.second to it.first.dow },
        )
    }
}

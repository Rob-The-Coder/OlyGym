package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.int
import olygym.app.data.num
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * starter.js ships four tests for this module; they are what is pinned here, plus the one thing a
 * Kotlin port can get wrong silently — a schedule key that no routine carries would drop a day
 * through mapNotNull and produce a plan with fewer days than the chooser promises.
 */
class StarterTest {

    @Test
    fun `the chooser reports each plan's day count from its schedule`() {
        assertEquals(
            listOf("ppl" to 3, "upper-lower" to 4, "full-body" to 3, "5x5" to 3),
            starterPlanOptions(),
        )
    }

    @Test
    fun `starterPlanDays gives the weekdays a plan claims, or null for an unknown one`() {
        assertEquals(listOf(1, 3, 5), starterPlanDays("ppl"))
        assertEquals(listOf(1, 2, 4, 5), starterPlanDays("upper-lower"))
        assertNull(starterPlanDays("nope"))
        assertNull(starterPlanDays(null))
    }

    @Test
    fun `buildStarterPlan is null for an unknown id, so a caller can never half-apply one`() {
        assertNull(buildStarterPlan("nope", "x", MONDAY, "2026-10-08"))
        assertNull(buildStarterPlan(null, "x", MONDAY, "2026-10-08"))
    }

    @Test
    fun `the built week starts on the profile's week and carries the schedule's days`() {
        val w = buildStarterPlan("ppl", "Snatch / Clean & Jerk / Squat", MONDAY, "2026-10-08")!!
        assertEquals("2026-10-05", w.str("startIso"))
        assertEquals("Snatch / Clean & Jerk / Squat", w.str("name"))
        val days = w["days"] as JsonArray
        assertEquals(listOf(1, 3, 5), days.map { (it as JsonObject).int("dow") })
        assertEquals(
            listOf("Snatch Day", "Clean & Jerk Day", "Squat & Pull Day"),
            days.map { (it as JsonObject).str("name") },
        )
        val first = (days[0] as JsonObject)["ex"] as JsonArray
        assertEquals(listOf("wl58", "wl97", "wl79", "wl78"), first.map { (it as JsonObject).str("id") })
        val row = first[0] as JsonObject
        assertEquals(5, row.int("sets"))
        assertEquals(3, row.int("reps"))
        assertEquals(0.0, row.num("weight")!!, 0.0)
        // The sharing path reads a week's own customEx (plan-share.js), so it is written empty.
        assertEquals("[]", w["customEx"].toString())
    }

    @Test
    fun `a Sunday-first profile starts the week on Sunday`() {
        val w = buildStarterPlan("5x5", weekStart = SUNDAY, todayIso = "2026-10-08")!!
        assertEquals("2026-10-04", w.str("startIso"))
    }

    @Test
    fun `every plan builds the number of days the chooser promises`() {
        for ((id, days) in starterPlanOptions()) {
            val w = buildStarterPlan(id, "x", MONDAY, "2026-10-08")!!
            assertEquals(days, (w["days"] as JsonArray).size)
            val dows = (w["days"] as JsonArray).map { (it as JsonObject).int("dow") }
            assertEquals(starterPlanDays(id), dows)
        }
    }
}

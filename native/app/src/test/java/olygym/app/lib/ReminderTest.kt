package olygym.app.lib

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import olygym.app.data.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The first entry of frontend/src/lib/mobile.js's buildReminderNotifications — the only one Android
 * needs, because its alarm is rescheduled on every fire. UTC and fixed dates keep it deterministic.
 */

private val ZONE = ZoneId.of("UTC")

private fun instant(iso: String): Long = Instant.parse(iso).toEpochMilli()

private fun pday(dow: Int, name: String): JsonObject =
    js("dow" to dow, "name" to name, "ex" to JsonArray(emptyList()))

private fun pweek(startIso: String, vararg days: JsonObject): JsonObject =
    js("id" to "w-" + startIso, "startIso" to startIso, "name" to "", "days" to JsonArray(days.toList()))

private fun pstate(vararg weeks: JsonObject): JsonObject =
    js("unit" to "kg", "weeks" to JsonArray(weeks.toList()))

/** Mon/Wed/Fri, the shape a coach's imported week has. */
private fun week(): JsonObject = pweek(
    "2026-10-05",
    pday(1, "Snatch Day"),
    pday(3, "Clean & Jerk Day"),
    pday(5, "Squat Day"),
)

private fun withReminder(S: JsonObject, on: Boolean, time: String): JsonObject =
    S.with("reminder", js("on" to on, "time" to time))

class ReminderTest {

    @Before
    fun setUp() {
        I18nCore.setLangState("en")
    }

    @Test
    fun theNextPlannedDayAtTheSetTimeIsTheReminder() {
        val S = withReminder(pstate(week()), on = true, time = "08:00")
        val next = nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE)
        assertEquals("2026-10-05", next?.iso)
        assertEquals(instant("2026-10-05T08:00:00Z"), next?.atMillis)
        assertEquals("Workout day", next?.title)
        assertTrue(next!!.body.contains("Snatch Day"))
    }

    @Test
    fun aDayAlreadyTrainedGetsNoReminder() {
        val S = withReminder(pstate(week()), on = true, time = "08:00")
            .with("workouts", JsonArray(listOf(js("d" to "2026-10-05"))))
        assertEquals("2026-10-07", nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE)?.iso)
    }

    @Test
    fun onceTheTimeHasPassedTheNextPlannedDayTakesOver() {
        val S = withReminder(pstate(week()), on = true, time = "08:00")
        assertEquals("2026-10-07", nextReminder(S, instant("2026-10-05T09:00:00Z"), ZONE)?.iso)
    }

    @Test
    fun theSetTimeItselfBelongsToTheNextDay() {
        val S = withReminder(pstate(week()), on = true, time = "08:00")
        assertEquals("2026-10-07", nextReminder(S, instant("2026-10-05T08:00:00Z"), ZONE)?.iso)
    }

    @Test
    fun aRestDayIsSkipped() {
        val S = withReminder(pstate(week()), on = true, time = "08:00")
        // Tuesday: the week has no day of its own for it, so Wednesday is next.
        assertEquals("2026-10-07", nextReminder(S, instant("2026-10-06T06:00:00Z"), ZONE)?.iso)
    }

    @Test
    fun aDayWithNoNameIsJustTheWorkout() {
        val S = withReminder(pstate(pweek("2026-10-05", pday(1, ""))), on = true, time = "08:00")
        assertTrue(nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE)!!.body.contains("Workout"))
    }

    @Test
    fun theReminderOffIsNoReminder() {
        val S = withReminder(pstate(week()), on = false, time = "08:00")
        assertNull(nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE))
    }

    @Test
    fun aTimeThatIsNotATimeIsNoReminder() {
        val S = withReminder(pstate(week()), on = true, time = "half past eight")
        assertNull(nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE))
        assertNull(nextReminder(withReminder(pstate(week()), true, "25:00"), instant("2026-10-05T06:00:00Z"), ZONE))
    }

    @Test
    fun aPlanWithNoDaysIsNoReminder() {
        assertNull(nextReminder(withReminder(pstate(), true, "08:00"), instant("2026-10-05T06:00:00Z"), ZONE))
    }

    @Test
    fun aPlannedDayBeyondTheWindowIsNotLookedFor() {
        // The week starts 63 days after the Monday this reads from, and the walk stops at 59.
        val S = withReminder(pstate(pweek("2026-12-07", pday(1, "Snatch Day"))), on = true, time = "08:00")
        assertNull(nextReminder(S, instant("2026-10-05T06:00:00Z"), ZONE))
    }
}

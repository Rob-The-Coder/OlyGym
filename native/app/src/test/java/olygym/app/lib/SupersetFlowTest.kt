package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import olygym.app.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The spec of frontend/src/lib/supersetFlow.test.js, one case each. */
class SupersetFlowTest {

    private fun arr(vararg values: Any?): JsonArray = JsonArray(values.map { it.toJson() })

    private fun entry(done: List<Boolean>): JsonObject = js("sets" to done.map { js("done" to it) })

    private fun member(done: List<Boolean> = listOf(false, false)): JsonObject =
        js(
            "target" to js("mode" to "reps", "reps" to 3),
            "sets" to done.map { js("w" to 60, "r" to 3, "done" to it) },
        )

    private fun ramp(warmupRestSec: Int?, done: List<Boolean> = listOf(false, false, false, false)): JsonObject =
        js(
            "target" to js("restSec" to 150, "warmupRestSec" to warmupRestSec),
            "sets" to listOf(
                js("phase" to "warmup", "w" to 60, "r" to 8, "done" to done[0]),
                js("phase" to "warmup", "w" to 95, "r" to 5, "done" to done[1]),
                js("phase" to "work", "w" to 125, "r" to 6, "done" to done[2]),
                js("phase" to "work", "w" to 125, "r" to 6, "done" to done[3]),
            ),
        )

    // A complex is done as one sequence without stopping, so one check has to close the round of
    // every movement. This helper only decides whether the unit can be drawn as one table of rounds;
    // when it cannot, the screen keeps the per-movement tables and their own checks.

    @Test
    fun `gives an aligned complex one round per set`() {
        assertEquals(
            arr(js("warmup" to false), js("warmup" to false)),
            complexRounds(arr(member(), member()), arr(0, 1)),
        )
    }

    @Test
    fun `keeps the warm-up phase in the shared round list`() {
        val warm = js(
            "target" to js("mode" to "reps", "reps" to 3),
            "sets" to listOf(
                js("w" to 30, "r" to 5, "done" to false, "phase" to "warmup"),
                js("w" to 60, "r" to 3, "done" to false),
                js("w" to 60, "r" to 3, "done" to false),
            ),
        )
        val other = warm
        assertEquals(
            arr(js("warmup" to true), js("warmup" to false), js("warmup" to false)),
            complexRounds(arr(warm, other), arr(0, 1)),
        )
    }

    @Test
    fun `refuses a unit whose members carry different set counts`() {
        assertNull(complexRounds(arr(member(listOf(false, false)), member(listOf(false))), arr(0, 1)))
    }

    @Test
    fun `refuses a unit where a warm-up sits at a different index`() {
        val warm = js(
            "target" to js("mode" to "reps", "reps" to 3),
            "sets" to listOf(
                js("w" to 30, "r" to 5, "done" to false, "phase" to "warmup"),
                js("w" to 60, "r" to 3, "done" to false),
            ),
        )
        assertNull(complexRounds(arr(warm, member(listOf(false, false))), arr(0, 1)))
    }

    @Test
    fun `refuses a timed member`() {
        val timed = js(
            "target" to js("mode" to "time", "sec" to 45),
            "sets" to listOf(
                js("sec" to 45, "w" to 0, "done" to false),
                js("sec" to 45, "w" to 0, "done" to false),
            ),
        )
        assertNull(complexRounds(arr(member(), timed), arr(0, 1)))
    }

    @Test
    fun `refuses a singleton, an empty unit and missing entries`() {
        assertNull(complexRounds(arr(member()), arr(0)))
        assertNull(complexRounds(arr(member(), member()), arr()))
        assertNull(complexRounds(arr(member(), member()), arr(0, 9)))
        assertNull(complexRounds(arr(js("sets" to emptyList<Any>()), member()), arr(0, 1)))
        assertNull(complexRounds(null, arr(0, 1)))
    }

    private fun bw(w: Int): JsonObject = js(
        "target" to js("mode" to "reps", "reps" to 3, "bodyweight" to true),
        "sets" to listOf(js("w" to w, "r" to 3, "done" to false), js("w" to w, "r" to 3, "done" to false)),
    )

    @Test
    fun `shares the table only with effort tracking off`() {
        val complex = arr(member(), member())
        assertEquals(arr(js("warmup" to false), js("warmup" to false)), complexRoundsFor(complex, arr(0, 1), "none"))
        assertNull(complexRoundsFor(complex, arr(0, 1), "rir"))
        assertNull(complexRoundsFor(complex, arr(0, 1), "rpe"))
    }

    @Test
    fun `refuses a bodyweight complex until every member has a load`() {
        assertNull(complexRoundsFor(arr(bw(0), bw(0)), arr(0, 1), "none"))
        assertNull(complexRoundsFor(arr(bw(20), bw(0)), arr(0, 1), "none"))
        assertEquals(arr(js("warmup" to false), js("warmup" to false)), complexRoundsFor(arr(bw(20), bw(20)), arr(0, 1), "none"))
    }

    @Test
    fun `rests between the sets of an exercise`() {
        assertEquals(true, restAfterSet(unitDone = false, lastUnit = false))
        assertEquals(true, restAfterSet(unitDone = false, lastUnit = true))
    }

    // Issue #3: a two-set exercise timed one rest instead of two, because the closing set
    // "finished quietly". The next exercise still follows it, so the rest belongs there.
    @Test
    fun `rests after the closing set when another exercise follows`() {
        assertEquals(true, restAfterSet(unitDone = true, lastUnit = false))
    }

    @Test
    fun `stays quiet only on the very last set of the session`() {
        assertEquals(false, restAfterSet(unitDone = true, lastUnit = true))
    }

    @Test
    fun `does not create navigation or rest flow for a normal singleton exercise`() {
        val entries = arr(entry(listOf(true)), entry(listOf(false)))
        assertNull(supersetFlowStep(entries, arr(0), 0))
    }

    @Test
    fun `does not count an uncheck or re-check of previously completed work as new progress`() {
        val finished = entry(listOf(true, true, true))
        assertEquals(js("isNew" to false, "highWater" to 3), setProgressHighWater(finished, 3))
        assertEquals(js("isNew" to true, "highWater" to 3), setProgressHighWater(finished, 2))
    }

    @Test
    fun `skips a spent short member and uses the last member with work as the round boundary`() {
        // A has just completed set two of three; B's only set was completed last round.
        val entries = arr(entry(listOf(true, true, false)), entry(listOf(true)))
        assertEquals(
            js("unitDone" to false, "roundDone" to true, "nextIdx" to 0),
            supersetFlowStep(entries, arr(0, 1), 0),
        )
    }

    @Test
    fun `wraps to the next member with work at a normal round boundary`() {
        val entries = arr(entry(listOf(true, false, false)), entry(listOf(true)))
        assertEquals(
            js("unitDone" to false, "roundDone" to true, "nextIdx" to 0),
            supersetFlowStep(entries, arr(0, 1), 1),
        )
    }

    @Test
    fun `inserts after the whole current unit`() {
        assertEquals(2, insertionIndexAfterCurrentUnit(arr(arr(0, 1), arr(2)), 0, 3))
        assertEquals(2, insertionIndexAfterCurrentUnit(arr(arr(0), arr(1)), 1, 2))
        assertEquals(0, insertionIndexAfterCurrentUnit(arr(), 0, 0))
    }

    @Test
    fun `finds the next unfinished unit, skips completed units, and wraps once`() {
        val entries = arr(entry(listOf(false)), entry(listOf(true)), entry(listOf(false)), entry(listOf(true)))
        val units = arr(arr(0), arr(1), arr(2, 3))
        assertEquals(arr(2, 3), nextUnfinishedUnit(entries, units, 0))
        assertEquals(arr(0), nextUnfinishedUnit(entries, units, 2))
    }

    @Test
    fun `returns null only when every unit is complete`() {
        val entries = arr(entry(listOf(true)), entry(listOf(true)))
        assertNull(nextUnfinishedUnit(entries, arr(arr(0), arr(1)), 1))
    }

    // Issue #3 has two halves. restAfterSet covers "no break after the LAST set of an exercise";
    // this covers "after the first set, sometimes a break doesn't appear" — the uncheck/re-check
    // that the high-water rule swallows.

    @Test
    fun `starts the rest a swallowed re-check would otherwise cost you`() {
        assertEquals(true, restOnRecheck(timerRunning = false, unitDone = false, lastUnit = false))
    }

    @Test
    fun `leaves a rest that is already counting alone`() {
        assertEquals(false, restOnRecheck(timerRunning = true, unitDone = false, lastUnit = false))
    }

    @Test
    fun `still stays quiet on the last set of the last exercise`() {
        assertEquals(false, restOnRecheck(timerRunning = false, unitDone = true, lastUnit = true))
    }

    @Test
    fun `rests after closing an exercise that is not the last one`() {
        assertEquals(true, restOnRecheck(timerRunning = false, unitDone = true, lastUnit = false))
    }

    // Issue #10: a routine can give an exercise its own rest, and the global timer stops being the
    // only answer. The resolution is the whole feature — the UI just writes the number down.
    // 0: no rest of its own, 1: 180 s, 2: 45 s — a heavy pull, a light accessory, a plain one.
    private val restEntries = arr(
        js("id" to "a", "target" to js("mode" to "reps")),
        js("id" to "b", "target" to js("mode" to "reps", "restSec" to 180)),
        js("id" to "c", "target" to js("mode" to "reps", "restSec" to 45)),
    )

    @Test
    fun `prefers the exercise's own rest over the global default`() {
        assertEquals(180.0, restSecFor(restEntries, arr(1), 90), 0.0)
        assertEquals(45.0, restSecFor(restEntries, arr(2), 90), 0.0)
    }

    @Test
    fun `falls back to the global default when the exercise sets none`() {
        assertEquals(90.0, restSecFor(restEntries, arr(0), 90), 0.0)
    }

    @Test
    fun `gives a superset the longest rest its members asked for`() {
        // Not the member that closed the round, and not the shortest: the group rests once, and
        // the 180 s exercise is the one still recovering when the 45 s one is ready to go again.
        assertEquals(180.0, restSecFor(restEntries, arr(1, 2), 90), 0.0)
        // A member with no rest of its own pulls in the global, which can be the longest of all.
        assertEquals(90.0, restSecFor(restEntries, arr(0, 2), 90), 0.0)
    }

    @Test
    fun `honours an explicit rest even with the global timer switched off`() {
        assertEquals(180.0, restSecFor(restEntries, arr(1), 0), 0.0)
        assertEquals(180.0, restSecFor(restEntries, arr(0, 1), 0), 0.0)
    }

    @Test
    fun `stays off when the timer is off and nothing asked for a rest`() {
        assertEquals(0.0, restSecFor(restEntries, arr(0), 0), 0.0)
        assertEquals(0.0, restSecFor(restEntries, arr(0, 0), 0), 0.0)
    }

    @Test
    fun `survives a missing unit or entry rather than timing NaN`() {
        assertEquals(90.0, restSecFor(restEntries, null, 90), 0.0)
        assertEquals(90.0, restSecFor(restEntries, arr(), 90), 0.0)
        assertEquals(90.0, restSecFor(restEntries, arr(7), 90), 0.0)
        assertEquals(0.0, restSecFor(null, arr(0), null), 0.0)
    }

    @Test
    fun `rests the warm-up rest between ramp sets`() {
        assertEquals(45.0, warmupRestSecFor(ramp(45), 0, 150), 0.0)
    }

    @Test
    fun `rests the working rest after the last ramp set, into the first work set`() {
        assertEquals(150.0, warmupRestSecFor(ramp(45), 1, 150), 0.0)
    }

    @Test
    fun `rests the working rest after a work set`() {
        assertEquals(150.0, warmupRestSecFor(ramp(45), 2, 150), 0.0)
        assertEquals(150.0, warmupRestSecFor(ramp(45), 3, 150), 0.0)
    }

    @Test
    fun `without the field a ramp set rests like a work set (the pre-field behaviour)`() {
        assertEquals(150.0, warmupRestSecFor(ramp(null), 0, 150), 0.0)
        val noField = js(
            "target" to js("warmupRestSec" to 0),
            "sets" to listOf(
                js("phase" to "warmup", "w" to 60, "r" to 8, "done" to false),
                js("phase" to "warmup", "w" to 95, "r" to 5, "done" to false),
                js("phase" to "work", "w" to 125, "r" to 6, "done" to false),
                js("phase" to "work", "w" to 125, "r" to 6, "done" to false),
            ),
        )
        assertEquals(90.0, warmupRestSecFor(noField, 0, 90), 0.0)
    }

    @Test
    fun `looks at the next UNFINISHED set — a ramp set re-checked after the work began rests the working rest`() {
        assertEquals(150.0, warmupRestSecFor(ramp(45, listOf(true, true, false, false)), 0, 150), 0.0)
    }

    @Test
    fun `is safe on a legacy warmup boolean and on missing entries`() {
        val legacy = js(
            "target" to js("warmupRestSec" to 45),
            "sets" to listOf(
                js("warmup" to true, "done" to false),
                js("warmup" to true, "done" to false),
                js("done" to false),
            ),
        )
        assertEquals(45.0, warmupRestSecFor(legacy, 0, 120), 0.0)
        assertEquals(120.0, warmupRestSecFor(null, 0, 120), 0.0)
        assertEquals(120.0, warmupRestSecFor(js("target" to js(), "sets" to emptyList<Any>()), 0, 120), 0.0)
    }
}

package olygym.app.lib

import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The spec of frontend/src/lib/workout-model.test.js, one case each. */
class WorkoutModelTest {

    @Test
    fun `reads both the explicit phase and the legacy boolean`() {
        assertEquals(true, isWarmupRow(js("phase" to "warmup")))
        assertEquals(true, isWarmupRow(js("warmup" to true)))
        assertEquals(false, isWarmupRow(js("phase" to "work")))
        assertEquals(false, isWarmupRow(js()))
    }

    // A stored row may still carry the retired drop-set/rest-pause type; mode resolution has to
    // keep reading it as ordinary reps work rather than failing on it.
    @Test
    fun `infers reps mode from the row's own r field regardless of type`() {
        assertEquals("reps", modeForSet(js("type" to "dropset", "w" to 100, "r" to 5)))
        assertEquals("reps", modeForSet(js("type" to "restpause", "w" to 60, "r" to 8)))
    }

    @Test
    fun `an entry mixing straight and legacy rows still reads as one reps-mode entry`() {
        val entry = js(
            "sets" to listOf(
                js("w" to 100, "r" to 5),
                js("type" to "dropset", "w" to 100, "r" to 5, "drops" to listOf(js("w" to 80, "r" to 5))),
            ),
        )
        assertEquals("reps", modeForEntry(entry))
    }

    // The cases the web spec does not spell out but the JS does, kept here because they are what
    // the loose reads are for.
    @Test
    fun `an explicit phase wins, and a non-string phase reads as the fallback`() {
        assertEquals("warmup", phaseForSet(js("phase" to "warmup", "warmup" to false)))
        // phase != null && phase !== '' passes for a number, and a number is not a known token.
        assertEquals("work", phaseForSet(js("phase" to 3, "warmup" to true)))
        assertEquals("warmup", phaseForSet(js("phase" to 3), fallback = "warmup"))
        assertEquals("warmup", phaseForSet(null, fallback = "warmup"))
        assertEquals("work", phaseForSet(js("phase" to "")))
    }

    @Test
    fun `mode resolution walks row, then target, then the legacy result fields`() {
        assertEquals("time", modeForSet(js("mode" to "time")))
        assertEquals("time", modeForSet(null, js("sec" to 45)))
        assertEquals("time", modeForSet(js("seconds" to 30)))
        assertEquals("reps", modeForSet(js("mode" to "amrap")))
        assertEquals("time", modeForSet(js("unit" to "seconds")))
        assertEquals("reps", modeForSet(js("unit" to "reps")))
        // A row that says nothing and a target that says nothing is a rep set.
        assertEquals("reps", modeForSet(null, null))
    }

    @Test
    fun `a mixed entry has no single mode, and a fallback is normalized`() {
        val mixed = js(
            "sets" to listOf(js("w" to 100, "r" to 5), js("sec" to 45, "mode" to "time")),
        )
        assertNull(modeForEntry(mixed))
        assertEquals("reps", modeForEntry(js("sets" to emptyList<Any>()), fallback = "nonsense"))
    }

    @Test
    fun `completed volume counts only checked-off work, and coerces its numbers`() {
        assertEquals(500.0, completedVolumeOf(js("done" to true, "w" to 100, "r" to 5))!!, 0.0)
        assertEquals(0.0, completedVolumeOf(js("w" to 100, "r" to 5))!!, 0.0)
        // An imported weight may be a string; Number("62.5") is 62.5.
        assertEquals(125.0, completedVolumeOf(js("done" to true, "w" to "62.5", "r" to 2))!!, 0.0)
        assertEquals(0.0, completedVolumeOf(js("done" to true))!!, 0.0)
        assertEquals(false, hasCompletedWork(js("done" to "true")))
    }
}

package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The spec of frontend/src/lib/session-start.test.js, one case each. */
class SessionStartTest {

    private val st = js(
        "unit" to "kg",
        "workouts" to emptyList<Any>(),
        "exWeights" to emptyMap<String, Any>(),
        "routines" to emptyList<Any>(),
    )

    @Before
    fun setUp() {
        // The catalogue is global state; a test that resolves an increment must not depend on what
        // the previous test class installed. An unknown id is the light increment, which is what
        // these fixtures rely on.
        Catalogue.install(emptyList())
    }

    private fun sets(entry: JsonObject) = entry.arr("sets")

    private fun weights(entry: JsonObject) =
        sets(entry).filter { !isWarmupRow(it) }.map { (it as? JsonObject)?.num("w") ?: 0.0 }

    @Test
    fun `ramps the warm-ups on the exercise's own increment, not the unit default`() {
        val r = js(
            "id" to "r",
            "prog" to "off",
            "ex" to listOf(js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 60, "inc" to 1.25, "warmupSets" to 2)),
        )
        val entry = buildSessionEntries(st, r)[0]
        val warm = sets(entry).filter { isWarmupRow(it) }.map { (it as? JsonObject)?.num("w") ?: 0.0 }
        assertEquals(2, warm.size)
        // A multiple of 1.25, not of the unit's 2.5.
        warm.forEach { w -> assertEquals(0.0, Math.round(w / 1.25 * 1000) / 1000.0 % 1.0, 0.0) }
        assertTrue(weights(entry).all { it == 60.0 })
    }

    @Test
    fun `keeps the unit default for timed exercises, whose inc is seconds`() {
        val r = js(
            "id" to "r",
            "prog" to "off",
            "ex" to listOf(js("id" to "0025", "mode" to "time", "sets" to 2, "sec" to 30, "inc" to 10, "weight" to 0)),
        )
        val entry = buildSessionEntries(st, r)[0]
        assertTrue(sets(entry).all { (it as? JsonObject)?.num("sec") == 30.0 })
    }

    @Test
    fun `returns a bare list, with no wrapper object`() {
        val r = js("id" to "r", "prog" to "off", "ex" to listOf(js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 60)))
        val out = buildSessionEntries(st, r)
        assertEquals(1, out.size)
        assertEquals("0025", out[0].str("id"))
    }

    @Test
    fun `stamps noProg and an off policy on an excluded day, and neither on a normal one`() {
        val ex = listOf(
            js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 60),
            js("id" to "0031", "sets" to 3, "reps" to 8, "weight" to 40),
        )
        val excluded = buildSessionEntries(st, js("id" to "rehab", "excludeFromProgression" to true, "ex" to ex))
        assertTrue(excluded.all { it.bool("noProg") == true })
        assertTrue(excluded.all { it.obj("plan")?.str("kind") == "off" })

        val normal = buildSessionEntries(st, js("id" to "r", "prog" to "off", "ex" to ex))
        // The JS asserts undefined, which is a key that was never written.
        assertTrue(normal.all { !it.containsKey("noProg") })
    }

    @Test
    fun `does not stamp a routine id, because a day is atomic`() {
        val r = js("id" to "r", "prog" to "off", "ex" to listOf(js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 60)))
        assertFalse(buildSessionEntries(st, r)[0].containsKey("rid"))
    }

    // Settings -> During a workout -> Automatic progression (lib/progression.js defaultPolicy). A
    // day's weight is the weight you lift; only the setting asks for it to climb.
    private fun stWithHistory(autoProg: Boolean) = js(
        "unit" to "kg",
        "exWeights" to emptyMap<String, Any>(),
        "routines" to emptyList<Any>(),
        "autoProg" to autoProg,
        "workouts" to listOf(
            js(
                "d" to "2026-01-01",
                "entries" to listOf(
                    js(
                        "id" to "0025",
                        "target" to js("sets" to 3, "reps" to 5),
                        "sets" to listOf(
                            js("w" to 60, "r" to 5, "done" to true),
                            js("w" to 60, "r" to 5, "done" to true),
                            js("w" to 60, "r" to 5, "done" to true),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun plannedDay(prog: String? = null) = js(
        "id" to "r",
        "ex" to listOf(js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 40, "prog" to prog)),
    )

    @Test
    fun `opens at the day's own weight with automatic progression off, which is the default`() {
        val entries = buildSessionEntries(stWithHistory(false), plannedDay())
        assertEquals(js("policy" to "off", "kind" to "off"), entries[0].obj("plan"))
        assertEquals(listOf(40.0, 40.0, 40.0), weights(entries[0]))
    }

    @Test
    fun `adds a step once the profile turns automatic progression on`() {
        val entries = buildSessionEntries(stWithHistory(true), plannedDay())
        assertEquals("up", entries[0].obj("plan")?.str("kind"))
        assertEquals(listOf(62.5, 62.5, 62.5), weights(entries[0]))
    }

    @Test
    fun `lets the exercise's own rule beat the setting, both ways`() {
        assertEquals("up", buildSessionEntries(stWithHistory(false), plannedDay("linear"))[0].obj("plan")?.str("kind"))
        assertEquals("off", buildSessionEntries(stWithHistory(true), plannedDay("off"))[0].obj("plan")?.str("kind"))
    }
}

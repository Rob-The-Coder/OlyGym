package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The spec of frontend/src/lib/session-merge.test.js, one case each. */
class SessionMergeTest {

    private val st = js(
        "unit" to "kg",
        "workouts" to emptyList<Any>(),
        "exWeights" to emptyMap<String, Any>(),
        "routines" to emptyList<Any>(),
    )

    @Before
    fun setUp() {
        Catalogue.install(emptyList())
    }

    private fun day(
        name: String = "Strength",
        ex: List<JsonObject> = listOf(
            js("id" to "0025", "sets" to 3, "reps" to 5, "weight" to 60),
            js("id" to "0031", "sets" to 3, "reps" to 8, "weight" to 40),
        ),
        excludeFromProgression: Boolean? = null,
    ) = js(
        "dow" to 1,
        "name" to name,
        "ex" to ex,
        "excludeFromProgression" to excludeFromProgression,
    )

    @Test
    fun `builds the day's entries and names the session after the day`() {
        val built = buildDayEntries(st, day())
        assertEquals(listOf("0025", "0031"), built.entries.map { it.str("id") })
        assertEquals("Strength", built.name)
    }

    @Test
    fun `a null day is a freestyle session with no entries`() {
        assertEquals(DayEntries(emptyList(), "Freestyle"), buildDayEntries(st, null))
    }

    @Test
    fun `an unnamed day still gets a session name`() {
        assertEquals("Freestyle", buildDayEntries(st, day(name = "")).name)
    }

    @Test
    fun `an empty day builds nothing but keeps its name`() {
        val built = buildDayEntries(st, day(name = "Mobility", ex = emptyList()))
        assertEquals(emptyList<JsonObject>(), built.entries)
        assertEquals("Mobility", built.name)
    }

    @Test
    fun `is exactly the plain buildSessionEntries path, with no per-entry routine stamp`() {
        val d = day()
        val built = buildDayEntries(st, d)
        assertEquals(buildSessionEntries(st, d), built.entries)
        assertTrue(built.entries.all { !it.containsKey("rid") })
    }

    @Test
    fun `carries the day's per-entry noProg through`() {
        val excluded = buildDayEntries(st, day(name = "Rehab", excludeFromProgression = true)).entries
        assertTrue(excluded.all { it.bool("noProg") == true })
        assertFalse(buildDayEntries(st, day()).entries.any { it.containsKey("noProg") })
    }

    @Test
    fun `deriveSessionName is null for an empty list`() {
        assertNull(deriveSessionName(emptyList()))
    }

    @Test
    fun `deriveSessionName joins one to three names with a plus`() {
        assertEquals("Rehab", deriveSessionName(listOf("Rehab")))
        assertEquals("Rehab + Core", deriveSessionName(listOf("Rehab", "Core")))
        assertEquals("A + B + C", deriveSessionName(listOf("A", "B", "C")))
    }

    @Test
    fun `deriveSessionName collapses four or more to the first two and a count`() {
        assertEquals("A + B + 2 more", deriveSessionName(listOf("A", "B", "C", "D")))
        assertEquals("A + B + 3 more", deriveSessionName(listOf("A", "B", "C", "D", "E")))
    }
}

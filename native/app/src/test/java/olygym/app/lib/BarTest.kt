package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The spec of frontend/src/lib/bar.test.js, one case each, in the same order. The catalogue is the
 * committed asset the app installs at startup, read here where a JVM test can reach it.
 */
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val barCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is ${File(".").absolutePath})")
    json.decodeFromString<List<Exercise>>(file.readText())
}

class BarTest {

    private val barbell = idOf("barbell")
    private val ezId = idOf("ez barbell")
    private val ez = js("id" to ezId, "eq" to "ez barbell")

    @Before
    fun installCatalogue() {
        Catalogue.install(barCatalogue)
    }

    private fun idOf(eq: String): String = barCatalogue.first { it.eq == eq }.id

    @Test
    fun `covers the five bar types, and every catalogue exercise that carries one`() {
        assertEquals(
            listOf("barbell", "ez barbell", "olympic barbell", "smith machine", "trap bar"),
            BAR_EQ.sorted(),
        )
        // Only `barbell` and the EZ-bar movements occur today, so this is a canary: retagging the
        // Smith or trap-bar movements (the sidecar in scripts/oly-catalogue) should move it, and
        // nothing else.
        val tagged = barCatalogue.filter { it.eq != null && BAR_EQ.contains(it.eq) }
        assertTrue(tagged.isNotEmpty())
        assertEquals(listOf("barbell", "ez barbell"), tagged.mapNotNull { it.eq }.toSet().sorted())
    }

    @Test
    fun `usesBar answers for ids and exercise objects alike`() {
        assertTrue(usesBar(JsonPrimitive(idOf("barbell"))))
        for (eq in BAR_EQ) {
            assertTrue(eq, usesBar(js("eq" to eq)))
        }
        assertFalse(usesBar(js("eq" to "dumbbell")))
        assertFalse(usesBar(js("eq" to "body weight")))
        assertFalse(usesBar(JsonPrimitive("no-such-id")))
        assertFalse(usesBar(null))
    }

    @Test
    fun `every bar type has a default in kilos`() {
        for (eq in BAR_EQ) {
            assertTrue(eq, (DEFAULT_BAR_KG[eq] ?: 0.0) > 0.0)
        }
        assertEquals(10.0, defaultBarWeight("ez barbell")!!, 0.0)
        assertEquals(20.0, defaultBarWeight("olympic barbell")!!, 0.0)
        assertEquals(20.0, defaultBarWeight("barbell")!!, 0.0)
        assertNull(defaultBarWeight("dumbbell"))
    }

    @Test
    fun `falls back to the equipment default`() {
        assertEquals(20.0, barWeightFor(js("unit" to "kg", "barWeights" to emptyMap<String, Any?>()), JsonPrimitive(barbell))!!, 0.0)
        assertEquals(10.0, barWeightFor(js("unit" to "kg"), ez)!!, 0.0)
    }

    @Test
    fun `an explicit override wins over the default`() {
        val S = js("unit" to "kg", "barWeights" to mapOf(ezId to 7.5))
        assertEquals(7.5, barWeightFor(S, ez)!!, 0.0)
        assertTrue(hasBarOverride(S, ezId))
        assertEquals(20.0, barWeightFor(S, JsonPrimitive(barbell))!!, 0.0)   // other exercises keep their default
        assertFalse(hasBarOverride(S, barbell))
    }

    @Test
    fun `a deleted override falls back to the default, but a stored 0 is "no bar"`() {
        // JS deletes S.barWeights[EZ.id]; the absent key is what means "use the default".
        val S = js("unit" to "kg", "barWeights" to emptyMap<String, Any?>())
        assertEquals(10.0, barWeightFor(S, ez)!!, 0.0)
        // Changed with issue #138: 0 used to mean "clear this override". It now means the exercise
        // has no bar (a counterbalanced Smith carriage), which is a value of its own — the editor
        // deletes the key to ask for the default back.
        assertEquals(0.0, barWeightFor(js("unit" to "kg", "barWeights" to mapOf(ezId to 0)), ez)!!, 0.0)
        assertTrue(hasBarOverride(js("unit" to "kg", "barWeights" to mapOf(ezId to 0)), ezId))
    }

    @Test
    fun `is null for anything without a bar`() {
        val dumbbell = barCatalogue.first { it.eq == "dumbbell" }
        assertNull(barWeightFor(js("unit" to "kg", "barWeights" to emptyMap<String, Any?>()), JsonPrimitive(dumbbell.id)))
        assertNull(barWeightFor(js("unit" to "kg", "barWeights" to emptyMap<String, Any?>()), JsonPrimitive("no-such-id")))
    }

    @Test
    fun `splits what is beyond the bar evenly per side`() {
        assertEquals(21.25, plateSplit(62.5, 20.0)!!, 0.0)
        assertEquals(40.0, plateSplit(100.0, 20.0)!!, 0.0)
        assertEquals(10.0, plateSplit(30.0, 10.0)!!, 0.0)
    }

    @Test
    fun `rounds to 2 decimals`() {
        assertEquals(22.78, plateSplit(65.55, 20.0)!!, 0.0)
        assertEquals(0.6, plateSplit(21.2, 20.0)!!, 0.0)
    }

    @Test
    fun `is null when there is nothing sensible to show`() {
        assertNull(plateSplit(20.0, 20.0))    // bar only
        assertNull(plateSplit(15.0, 20.0))    // below the bar
        assertNull(plateSplit(0.0, 20.0))
        // 100 with no bar is 50 a side, not "nothing to show" (issue #138) — see bar-nobar.test.js
        assertNull(plateSplit(null, 20.0))
        assertNull(plateSplit(100.0, null))
        assertNull(plateSplit(null, null))
    }
}

/** The spec of frontend/src/lib/bar-nobar.test.js (issue #138), one case each. */
class BarNoBarTest {

    private val smith = js("id" to "sm", "eq" to "smith machine")
    private val bb = js("id" to "bb", "eq" to "barbell")

    private fun S(over: Map<String, Any?> = emptyMap()): JsonObject = js("unit" to "kg", "barWeights" to over)

    @Test
    fun `keeps a stored 0 instead of falling back to the bar type default`() {
        assertEquals(0.0, barWeightFor(S(mapOf("sm" to 0)), smith)!!, 0.0)
        assertEquals(9.0, defaultBarWeight("smith machine")!!, 0.0)
        assertEquals(9.0, barWeightFor(S(), smith)!!, 0.0)          // no entry still means "use the default"
    }

    @Test
    fun `tells "no bar" apart from "nothing set"`() {
        assertTrue(isNoBar(S(mapOf("sm" to 0)), "sm"))
        assertFalse(isNoBar(S(mapOf("sm" to 15)), "sm"))
        assertFalse(isNoBar(S(), "sm"))
        assertTrue(hasBarOverride(S(mapOf("sm" to 0)), "sm"))
        assertFalse(hasBarOverride(S(), "sm"))
    }

    @Test
    fun `counts every plate when there is no bar`() {
        assertEquals(50.0, plateSplit(100.0, 0.0)!!, 0.0)
        assertEquals(40.0, plateSplit(100.0, 20.0)!!, 0.0)
        assertNull(plateSplit(0.0, 0.0))
        assertNull(plateSplit(100.0, null))            // not a bar exercise at all
    }

    @Test
    fun `leaves an ordinary barbell alone`() {
        assertEquals(25.0, barWeightFor(S(mapOf("bb" to 25)), bb)!!, 0.0)
        assertEquals(20.0, barWeightFor(S(), bb)!!, 0.0)
    }
}

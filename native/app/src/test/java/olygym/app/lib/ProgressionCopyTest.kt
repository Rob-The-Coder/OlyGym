package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.js
import olygym.app.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The progression line's copy, from frontend/src/lib/progression-copy.js: a result with nothing to
 * explain stays off the screen instead of printing an empty policy.
 */
class ProgressionCopyTest {

    @Test
    fun `no plan, no line`() {
        assertNull(progressionGuidance(null))
        assertNull(progressionGuidance(JsonPrimitive("not an object")))
    }

    @Test
    fun `a plan with no reason has nothing to say`() {
        assertNull(progressionGuidance(js("policy" to "linear", "kind" to "up")))
        // An empty array is truthy in JS, so a why that is present but empty does produce a
        // guidance with nothing in it. Faithful, if odd: the engine never writes one.
        val empty = progressionGuidance(js("policy" to "linear", "why" to JsonArray(emptyList()), "kind" to "up"))
        assertEquals("Linear progression", empty?.policyLabel)
        assertEquals(0, empty?.why?.size)
    }

    @Test
    fun `progression that is off is not explained`() {
        val why = listOf("Nothing logged yet — this session sets the baseline.")
        assertNull(progressionGuidance(js("policy" to "linear", "kind" to "off", "why" to why)))
        assertNull(progressionGuidance(js("policy" to "off", "kind" to "hold", "why" to why)))
    }

    @Test
    fun `a policy this build does not know is dropped rather than shown raw`() {
        assertNull(progressionGuidance(js("policy" to "undulating", "kind" to "up", "why" to listOf("Because."))))
        assertNull(progressionGuidance(js("kind" to "up", "why" to listOf("Because."))))
    }

    @Test
    fun `a live plan carries its label and the reason to translate`() {
        val why = listOf("Every rep last time — {0} {1} more.", 2.5, "kg")
        val guidance = progressionGuidance(js("policy" to "linear", "kind" to "up", "why" to why))
        assertEquals("Linear progression", guidance?.policyLabel)
        assertEquals(JsonArray(why.map { it.toJson() }), guidance?.why)
        // and the reason is what the screen shows, args and all
        assertEquals("Every rep last time — 2.5 kg more.", I18nCore.tMessage(guidance!!.why))
    }

    @Test
    fun `the message reader drops a whole number's decimal`() {
        val why = JsonArray(listOf(JsonPrimitive("Set {0} of {1}"), JsonPrimitive(2.0), JsonPrimitive(5.0)))
        assertEquals("Set 2 of 5", I18nCore.tMessage(why))
    }
}

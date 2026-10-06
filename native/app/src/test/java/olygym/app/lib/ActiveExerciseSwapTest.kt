package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spec of frontend/src/lib/active-exercise-swap.test.js, one case each, in the same order. */
class ActiveExerciseSwapTest {

    // JSON equality would lie about a computed Double that prints "32.5" against a typed 32.5, so
    // numbers compare by value and everything else structurally.
    private fun canonical(value: JsonElement?): JsonElement? = when (value) {
        null -> null
        is JsonArray -> JsonArray(value.map { canonical(it)!! })
        is JsonObject -> JsonObject(value.mapValues { canonical(it.value)!! })
        is JsonPrimitive -> {
            val b = value.booleanOrNull
            val d = value.doubleOrNull
            if (!value.isString && b == null && d != null) JsonPrimitive(d) else value
        }
    }

    private fun assertJson(expected: JsonElement?, actual: JsonElement?) {
        assertEquals(canonical(expected), canonical(actual))
    }

    private fun entry(
        id: String,
        sg: String? = null,
        done: Boolean = false,
        target: JsonObject? = null,
        sets: JsonArray? = null,
        extra: JsonObject = js(),
    ): JsonObject {
        var e = js(
            "id" to id,
            "sg" to sg,
            "target" to (target ?: js("mode" to "reps", "sets" to 1, "reps" to 5, "weight" to 40)),
            "sets" to (sets ?: JsonArray(listOf(js("w" to 40, "r" to 5, "done" to done)))),
        )
        extra.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
        return e
    }

    private fun replacement(): JsonObject = entry(
        "incline",
        target = js("mode" to "reps", "sets" to 2, "reps" to 8, "weight" to 32.5, "note" to "Keep elbows tucked."),
        sets = JsonArray(
            listOf(
                js("w" to 32.5, "r" to 8, "done" to false),
                js("w" to 32.5, "r" to 8, "done" to false),
            ),
        ),
    )

    private fun entries(session: JsonObject): List<JsonObject> = session.arr("entries").map { it.asObj()!! }

    @Test
    fun `replaces only the selected duplicate occurrence and preserves its group and replacement metadata`() {
        val first = entry("bench", sg = "pair")
        val selected = entry(
            "bench",
            sg = "pair",
            extra = js("occurrenceId" to "bench#2", "provenance" to js("routineIndex" to 4)),
        )
        val unrelated = entry("row")
        val active = js("cur" to 1, "entries" to listOf(first, selected, unrelated))
        val next = replacement()

        val result = swapActiveExercise(active, 1, next)
        assertTrue(result is SwapEvent.Replaced)
        val replaced = result as SwapEvent.Replaced
        assertEquals(1, replaced.index)
        val out = entries(replaced.active)
        assertEquals(listOf("bench", "incline", "row"), out.map { it.str("id") })
        assertSame(first, out[0])
        assertSame(unrelated, out[2])
        val expected = JsonObject(
            next +
                ("occurrenceId" to JsonPrimitive("bench#2")) +
                ("provenance" to js("routineIndex" to 4)) +
                ("sg" to JsonPrimitive("pair")),
        )
        assertJson(expected, out[1])
        assertEquals(1, replaced.active.int("cur")!!)
    }

    @Test
    fun `fails closed before a logged standalone occurrence is explicitly confirmed`() {
        val logged = entry("bench", sets = JsonArray(listOf(js("w" to 42.5, "r" to 7, "done" to true, "rir" to 1))))
        val other = entry("row")
        val active = js("cur" to 0, "entries" to listOf(logged, other))

        val result = swapActiveExercise(active, 0, replacement())
        assertTrue(result is SwapEvent.NeedsConfirmation)
        val needs = result as SwapEvent.NeedsConfirmation
        assertEquals(0, needs.index)
        assertFalse(needs.grouped)
        assertEquals(2, active.arr("entries").size)
        assertSame(logged, active.arr("entries")[0])
        assertSame(other, active.arr("entries")[1])
        assertEquals(0, active.int("cur")!!)
    }

    @Test
    fun `preserves a confirmed logged occurrence and inserts the replacement after it`() {
        val logged = entry("bench", sets = JsonArray(listOf(js("w" to 42.5, "r" to 7, "done" to true, "rir" to 1))))
        val unrelated = entry("row")
        val active = js("cur" to 0, "entries" to listOf(logged, unrelated))

        val result = swapActiveExercise(active, 0, replacement(), loggedConfirmed = true)
        assertTrue(result is SwapEvent.Inserted)
        val inserted = result as SwapEvent.Inserted
        assertEquals(1, inserted.index)
        val out = entries(inserted.active)
        assertSame(logged, out[0])
        assertJson(js("w" to 42.5, "r" to 7, "done" to true, "rir" to 1), out[0].arr("sets")[0])
        assertEquals("incline", out[1].str("id"))
        assertSame(unrelated, out[2])
        assertEquals(1, inserted.active.int("cur")!!)
    }

    @Test
    fun `requires an explicit grouped disposition before changing a logged group member`() {
        val active = js(
            "cur" to 0,
            "entries" to listOf(
                entry("bench", sg = "pair", done = true),
                entry("row", sg = "pair"),
                entry("curl"),
            ),
        )
        val before = active.arr("entries")

        val result = swapActiveExercise(active, 0, replacement(), loggedConfirmed = true)
        assertTrue(result is SwapEvent.NeedsConfirmation)
        val needs = result as SwapEvent.NeedsConfirmation
        assertEquals(0, needs.index)
        assertTrue(needs.grouped)
        assertEquals(before, active.arr("entries"))
    }

    // The vitest spec reaches this case through it.each, whose two rows run in order: "keep" then
    // "detach". Both rows are exercised here so that the single it() stays a single JUnit test.
    @Test
    fun `keep and detach confirmation preserve logged group data and unrelated entries`() {
        data class SwapCase(val disposition: String, val ids: List<String>, val groups: List<String?>, val cursor: Int)

        val cases = listOf(
            SwapCase("keep", listOf("bench", "incline", "row", "curl"), listOf("pair", "pair", "pair", null), 1),
            SwapCase("detach", listOf("bench", "row", "incline", "curl"), listOf("pair", "pair", null, null), 2),
        )

        for (case in cases) {
            val logged = entry("bench", sg = "pair", sets = JsonArray(listOf(js("w" to 45, "r" to 6, "done" to true))))
            val partner = entry("row", sg = "pair")
            val unrelated = entry("curl")
            val active = js("cur" to 0, "entries" to listOf(logged, partner, unrelated))

            val result = swapActiveExercise(
                active,
                0,
                replacement(),
                loggedConfirmed = true,
                groupDisposition = case.disposition,
            )
            assertTrue("expected an insert for ${case.disposition}", result is SwapEvent.Inserted)
            val inserted = result as SwapEvent.Inserted
            assertEquals(case.cursor, inserted.index)
            val out = entries(inserted.active)
            assertEquals(case.ids, out.map { it.str("id") })
            assertEquals(case.groups, out.map { it.str("sg") })
            assertSame(logged, out[0])
            assertTrue(out.any { it === partner })
            assertSame(unrelated, out.last())
            assertEquals(case.cursor, inserted.active.int("cur")!!)
        }
    }
}

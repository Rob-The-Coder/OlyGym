package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spec of frontend/src/lib/finish-workout.test.js, one case each, in the same order. */
class FinishWorkoutTest {

    // JSON equality would lie about a computed Double that prints "85.0" where the spec writes 85,
    // so numbers compare by value and everything else structurally.
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

    private fun entriesOf(workout: JsonObject): List<JsonObject> = workout.arr("entries").map { it.asObj()!! }

    @Test
    fun `builds the record doFinishWorkout stores, with the day it came from and no routine id`() {
        val active = js(
            "id" to "active-1", "d" to "2026-08-08", "start" to 1000, "weekId" to "w1", "dow" to 5,
            "name" to "Push", "bw" to 80,
            "entries" to listOf(
                js(
                    "id" to "0025",
                    "sets" to listOf(js("done" to true, "w" to 60, "r" to 8)),
                    "topW" to 60,
                    "target" to js("sets" to 1, "reps" to 8),
                ),
            ),
        )
        val completed = buildCompletedWorkout(active, end = 2000, prs = JsonArray(emptyList()))
        assertJson(
            js(
                "id" to "active-1", "d" to "2026-08-08", "start" to 1000, "end" to 2000,
                "weekId" to "w1", "dow" to 5, "name" to "Push", "bw" to 80,
                "entries" to listOf(
                    js(
                        "id" to "0025",
                        "sets" to listOf(js("done" to true, "w" to 60, "r" to 8)),
                        "topW" to 60,
                        "target" to js("sets" to 1, "reps" to 8),
                    ),
                ),
                "prs" to emptyList<Any>(),
            ),
            completed,
        )
        // The legacy routine fields are never written.
        assertFalse(completed.containsKey("routineIds"))
        assertFalse(completed.containsKey("routineId"))
    }

    @Test
    fun `a freestyle session has no week and no weekday`() {
        val base = js(
            "id" to "w", "d" to "2026-08-08", "start" to 1,
            "entries" to listOf(
                js("id" to "0025", "sets" to listOf(js("done" to true, "w" to 60, "r" to 8)), "target" to js("sets" to 1, "reps" to 8)),
            ),
        )
        val freestyle = buildCompletedWorkout(base)
        assertNull(freestyle["weekId"])
        assertNull(freestyle["dow"])
    }

    @Test
    fun `carries per-entry noProg onto the saved entry, written only when set`() {
        val active = js(
            "id" to "w", "d" to "2026-08-08", "start" to 1, "weekId" to "w1", "dow" to 1,
            "entries" to listOf(
                js("id" to "0025", "sets" to listOf(js("done" to true, "w" to 60, "r" to 8)), "target" to js("sets" to 1, "reps" to 8)),
                js("id" to "0031", "noProg" to true, "sets" to listOf(js("done" to true, "w" to 10, "r" to 12)), "target" to js("sets" to 1, "reps" to 12)),
            ),
        )
        val (a, b) = entriesOf(buildCompletedWorkout(active))
        assertFalse(a.containsKey("noProg"))
        assertFalse(a.containsKey("rid"))
        assertEquals(true, b.bool("noProg"))
        assertFalse(b.containsKey("rid"))
    }

    @Test
    fun `derives topW from the highest completed non-warm-up work set, not stale entry data`() {
        val active = js(
            "id" to "active-1", "d" to "2026-08-08", "start" to 1000,
            "entries" to listOf(
                js(
                    "id" to "0025", "topW" to 80, "target" to js("mode" to "reps", "sets" to 2, "reps" to 8),
                    "sets" to listOf(
                        js("phase" to "warmup", "done" to true, "w" to 120, "r" to 8),
                        js("done" to true, "w" to 75, "r" to 8),
                        js("done" to true, "w" to 85, "r" to 7),
                        js("done" to false, "w" to 100, "r" to 8),
                    ),
                ),
            ),
        )

        assertEquals(85.0, buildCompletedWorkout(active).arr("entries")[0].asObj()!!.num("topW")!!, 0.0)
        val entry = active.arr("entries")[0].asObj()!!
        val stale = JsonObject(active + ("entries" to JsonArray(listOf(JsonObject(entry + ("topW" to JsonPrimitive(120)))))))
        assertEquals(85.0, buildCompletedWorkout(stale).arr("entries")[0].asObj()!!.num("topW")!!, 0.0)
    }

    @Test
    fun `keeps a legacy topW when old completed rows have no usable weight`() {
        val active = js(
            "id" to "active-1", "d" to "2026-08-08", "start" to 1000,
            "entries" to listOf(js("id" to "0025", "topW" to 60, "sets" to listOf(js("done" to true, "r" to 8)))),
        )

        assertEquals(60.0, buildCompletedWorkout(active).arr("entries")[0].asObj()!!.num("topW")!!, 0.0)
    }

    @Test
    fun `writes the legacy excludeFromProgression mirror iff every completed entry is noProg`() {
        fun mk(entries: List<JsonObject>): JsonObject =
            js("id" to "w", "d" to "2026-08-08", "start" to 1, "weekId" to "w1", "dow" to 1, "entries" to entries)

        fun done(extra: JsonObject = js()): JsonObject {
            var e = js("id" to "0025", "sets" to listOf(js("done" to true, "w" to 30, "r" to 8)), "target" to js("sets" to 1, "reps" to 8))
            extra.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
            return e
        }

        // rehab-only combined session -> present
        assertTrue(
            buildCompletedWorkout(mk(listOf(done(js("noProg" to true)), done(js("id" to "0031", "noProg" to true)))))
                .bool("excludeFromProgression") == true,
        )
        // rehab + strength -> absent
        assertFalse(
            buildCompletedWorkout(mk(listOf(done(js("noProg" to true)), done(js("id" to "0031")))))
                .containsKey("excludeFromProgression"),
        )
        // all-normal -> absent
        assertFalse(
            buildCompletedWorkout(mk(listOf(done(), done(js("id" to "0031")))))
                .containsKey("excludeFromProgression"),
        )
    }

    @Test
    fun `persists a muscle snapshot only when the caller supplies one`() {
        val active = js(
            "id" to "active-1", "d" to "2026-08-08", "start" to 1000,
            "entries" to listOf(
                js("id" to "catalogue", "sets" to listOf(js("done" to true))),
                js("id" to "custom", "sets" to listOf(js("done" to true))),
            ),
        )
        val completed = buildCompletedWorkout(
            active,
            end = 2000,
            snapshotFor = { entry ->
                if (entry.str("id") == "custom") js("n" to "Custom lift", "muscleWeights" to js("chest" to 1)) else null
            },
        )

        assertFalse(entriesOf(completed)[0].containsKey("muscleSnapshot"))
        assertJson(js("n" to "Custom lift", "muscleWeights" to js("chest" to 1)), entriesOf(completed)[1].obj("muscleSnapshot"))
    }

    // ---------------------------------------------------------------- session notes

    private fun noteActive(extra: JsonObject = js()): JsonObject {
        var entry = js("id" to "0025", "sets" to listOf(js("w" to 100, "r" to 5, "done" to true)))
        extra.forEach { (k, v) -> entry = JsonObject(entry + (k to v)) }
        return js(
            "id" to "w1", "d" to "2026-08-25", "start" to 1, "weekId" to "w1", "dow" to 2, "name" to "Push",
            "entries" to listOf(entry),
        )
    }

    @Test
    fun `keeps a per-exercise note and its pin`() {
        val w = buildCompletedWorkout(noteActive(js("note" to "  narrower grip next time  ", "notePin" to true)))
        assertEquals("narrower grip next time", entriesOf(w)[0].str("note"))
        assertEquals(true, entriesOf(w)[0].bool("notePin"))
    }

    @Test
    fun `keeps an unpinned note without inventing a pin`() {
        val w = buildCompletedWorkout(noteActive(js("note" to "shoulder twinged")))
        assertEquals("shoulder twinged", entriesOf(w)[0].str("note"))
        assertFalse(entriesOf(w)[0].containsKey("notePin"))
    }

    @Test
    fun `writes no note fields at all when nothing was typed`() {
        val w = buildCompletedWorkout(noteActive(js("note" to "   ", "notePin" to true)))
        assertFalse(entriesOf(w)[0].containsKey("note"))
        assertFalse(entriesOf(w)[0].containsKey("notePin"))
    }

    @Test
    fun `keeps a whole-session note on the workout`() {
        val a = noteActive()
        val withNote = buildCompletedWorkout(JsonObject(a + ("note" to JsonPrimitive("slept badly"))))
        assertEquals("slept badly", withNote.str("note"))
        assertFalse(buildCompletedWorkout(a).containsKey("note"))
    }
}

package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.obj
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The spec of frontend/src/lib/active-workout-order.test.js, one case each, in the same order. */
class ActiveWorkoutOrderTest {

    private fun entry(id: String, extra: JsonObject = js()): JsonObject {
        var e = js(
            "id" to id,
            "target" to js("sets" to 1, "reps" to 5),
            "sets" to listOf(js("w" to 0, "r" to 5, "done" to false)),
        )
        extra.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
        return e
    }

    private fun entries(session: JsonObject): List<JsonObject> = session.arr("entries").map { it.asObj()!! }

    @Test
    fun `moves one standalone occurrence without conflating duplicate exercise ids`() {
        val duplicateA = entry("duplicate", js("occurrenceId" to "duplicate#1"))
        val selected = entry(
            "duplicate",
            js(
                "occurrenceId" to "duplicate#2",
                "target" to js("sets" to 2, "reps" to 7, "weight" to 82.5, "notes" to "Keep this target"),
                "sets" to listOf(js("w" to 77.5, "r" to 6, "done" to true, "rir" to 2)),
            ),
        )
        val active = js("cur" to 2, "entries" to listOf(duplicateA, entry("middle"), selected))

        val moved = moveActiveWorkoutUnit(active, active.int("cur")!!, -1)!!
        assertEquals(listOf(0, 2, 1), moved.indices)
        val reordered = entries(moved.active)
        assertEquals(listOf("duplicate#1", "duplicate#2", "middle"), reordered.map { it.str("occurrenceId") ?: it.str("id") })
        assertSame(duplicateA, reordered[0])
        assertSame(selected, reordered[1])
        assertSame(selected.obj("target"), reordered[1].obj("target"))
        assertSame(selected.arr("sets"), reordered[1].arr("sets"))
        assertEquals(1, moved.active.int("cur")!!)
    }

    @Test
    fun `moves a complete contiguous group one unit and preserves the selected member identity`() {
        val first = entry("group-a", js("sg" to "pair", "occurrenceId" to "group-a#1"))
        val selected = entry("group-b", js("sg" to "pair", "occurrenceId" to "group-b#1"))
        val groupMeta = js("pair" to js("kind" to "complex", "label" to "Carry pair", "cues" to "Stay braced."))
        val active = js(
            "cur" to 2,
            "entries" to listOf(entry("before"), first, selected, entry("after")),
            "groupMeta" to groupMeta,
        )

        val moved = moveActiveWorkoutUnit(active, active.int("cur")!!, -1)!!
        assertEquals(listOf(1, 2, 0, 3), moved.indices)
        val reordered = entries(moved.active)
        assertEquals(listOf("group-a", "group-b", "before", "after"), reordered.map { it.str("id") })
        assertSame(first, reordered[0])
        assertSame(selected, reordered[1])
        assertEquals(listOf("pair", "pair", null, null), reordered.map { it.str("sg") })
        assertSame(groupMeta, moved.active.obj("groupMeta"))
        assertSame(selected, reordered[moved.active.int("cur")!!])
    }

    @Test
    fun `moves a group down by exactly one neighbouring unit`() {
        val first = entry("group-a", js("sg" to "pair"))
        val selected = entry("group-b", js("sg" to "pair"))
        val active = js("cur" to 1, "entries" to listOf(first, selected, entry("middle"), entry("last")))

        val moved = moveActiveWorkoutUnit(active, active.int("cur")!!, 1)!!
        assertEquals(listOf(2, 0, 1, 3), moved.indices)
        assertEquals(listOf("middle", "group-a", "group-b", "last"), entries(moved.active).map { it.str("id") })
        assertSame(selected, entries(moved.active)[moved.active.int("cur")!!])
    }

    @Test
    fun `rejects boundaries and invalid directions without mutating the active workout`() {
        val active = js("cur" to 0, "entries" to listOf(entry("first"), entry("last")))
        val before = active.arr("entries")

        assertFalse(canMoveActiveWorkoutUnit(active, 0, -1))
        assertFalse(canMoveActiveWorkoutUnit(active, 1, 1))
        assertNull(moveActiveWorkoutUnit(active, 0, 0))
        assertNull(moveActiveWorkoutUnit(active, 0, -1))
        assertEquals(before, active.arr("entries"))
        assertEquals(0, active.int("cur")!!)
    }
}

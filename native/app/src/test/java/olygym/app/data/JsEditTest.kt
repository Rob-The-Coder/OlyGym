package olygym.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit combinators: how a screen writes into the state tree now that JSON is immutable.
 *
 * These replace the web's mutable clone. What they have to get right is that a write touches the
 * one branch it names, that the document it was given is untouched, and that an index which is
 * gone (a removal landing between a render and a tap) changes nothing at all.
 */
class JsEditTest {

    private fun doc(): JsonObject = js(
        "unit" to "kg",
        "active" to js(
            "name" to "Snatch Day",
            "entries" to listOf(
                js("id" to "wl58", "sets" to listOf(js("w" to 60.0, "done" to false), js("w" to 65.0))),
                js("id" to "wl59", "sets" to listOf(js("w" to 80.0))),
            ),
        ),
    )

    @Test
    fun `an edit reaches the branch it names and leaves its siblings identical`() {
        val before = doc()
        val after = before.editObject("active") { active ->
            active.with("name", "Clean Day")
        }
        assertEquals("Clean Day", after.obj("active")?.str("name"))
        assertEquals("kg", after.str("unit"))
        // and the document it was given is untouched
        assertEquals("Snatch Day", before.obj("active")?.str("name"))
    }

    @Test
    fun `a set field is written in place, three levels down`() {
        val after = doc()
            .editObject("active") { active ->
                active.editArray("entries") { entries ->
                    entries.editAt(0) { entry ->
                        entry.editArray("sets") { sets ->
                            sets.editAt(1) { it.with("w", 62.5) }
                        }
                    }
                }
            }
        val entries = after.obj("active")!!.arr("entries")
        val sets = entries.objectAt(0)!!.arr("sets")
        assertEquals(60.0, sets.objectAt(0)?.num("w"))
        assertEquals(62.5, sets.objectAt(1)?.num("w"))
        // the other exercise is where it was
        assertEquals(80.0, entries.objectAt(1)!!.arr("sets").objectAt(0)?.num("w"))
    }

    @Test
    fun `editing a key that is not an object changes nothing`() {
        val before = doc()
        assertEquals(before, before.editObject("unit") { it })
        assertEquals(before, before.editObject("nothing") { it })
        assertEquals(before, before.editArray("unit") { it })
    }

    @Test
    fun `an index that is gone is a no-op, not a crash`() {
        val before = doc()
        val entries = before.obj("active")!!.arr("entries")
        assertEquals(entries, entries.editAt(7) { it.with("id", "gone") })
        assertEquals(entries, entries.editAt(-1) { it.with("id", "gone") })
        assertEquals(entries, entries.removeObjectAt(7))
        assertEquals(entries, entries.removeObjectAt(-1))
    }

    @Test
    fun `append, insert and remove keep the order the caller asked for`() {
        val entries = doc().obj("active")!!.arr("entries")
        val third = js("id" to "wl60")
        assertEquals(listOf("wl58", "wl59", "wl60"), entries.append(third).map { it.asObj()?.str("id") })
        assertEquals(listOf("wl60", "wl58", "wl59"), entries.insertAt(0, third).map { it.asObj()?.str("id") })
        assertEquals(listOf("wl58", "wl60", "wl59"), entries.insertAt(1, third).map { it.asObj()?.str("id") })
        // out of range collapses to an end rather than throwing, like the JS splice
        assertEquals(listOf("wl58", "wl59", "wl60"), entries.insertAt(9, third).map { it.asObj()?.str("id") })
        assertEquals(listOf("wl58", "wl60"), entries.insertAt(9, third).removeObjectAt(1).map { it.asObj()?.str("id") })
    }

    @Test
    fun `a null drops the key rather than storing it`() {
        // The rule for a cleared set field: a row only carries what was actually logged.
        val set = js("w" to 60.0, "done" to true)
        val cleared = set.with("done", null)
        assertFalse(cleared.containsKey("done"))
        assertEquals(60.0, cleared.num("w"))
        assertEquals(set.without("done"), cleared)
    }

    @Test
    fun `mapObjects changes the objects and leaves everything else alone`() {
        val mixed = JsonArray(listOf(js("n" to 1.0), JsonPrimitive("x")))
        val mapped = mixed.mapObjects { it.with("n", 2.0) }
        assertEquals(2.0, mapped.objectAt(0)?.num("n"))
        assertEquals("x", mapped[1].asStr())
    }

    @Test
    fun `truthy is the JS rule, not a boolean cast`() {
        fun t(v: kotlinx.serialization.json.JsonElement?) = truthy(v)
        assertFalse(t(null))
        assertFalse(t(JsonPrimitive(false)))
        assertFalse(t(JsonPrimitive(0.0)))
        assertFalse(t(JsonPrimitive("")))
        assertTrue(t(JsonPrimitive(true)))
        assertTrue(t(JsonPrimitive("false")))     // a non-empty string, so true
        assertTrue(t(JsonPrimitive(1.0)))
        assertTrue(t(JsonArray(emptyList())))     // an empty array is truthy in JS
        assertTrue(t(JsonObject(emptyMap())))
    }

    @Test
    fun `jsText prints an integral number the way a template would`() {
        assertEquals("", (null as kotlinx.serialization.json.JsonElement?).jsText())
        assertEquals("30", JsonPrimitive(30.0).jsText())
        assertEquals("30.5", JsonPrimitive(30.5).jsText())
        assertEquals("true", JsonPrimitive(true).jsText())
        assertEquals("kg", JsonPrimitive("kg").jsText())
    }
}

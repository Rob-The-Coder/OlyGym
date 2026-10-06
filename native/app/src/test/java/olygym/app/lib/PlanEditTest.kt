package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The plan editor's writes, which the web keeps inline in Plan.jsx and WeekEdit.jsx. They are pure
 * functions over the whole state object here, so every rule the screens depend on — a dead week
 * changes nothing, a row keeps its id and superset id, a key this app does not model survives — is
 * pinned without a device.
 */
private fun ex(id: String, sets: Int = 3, reps: Int = 5, weight: Double = 0.0, sg: String? = null) =
    js("id" to id, "sets" to sets, "reps" to reps, "weight" to weight, "sg" to sg)

private fun day(dow: Int, name: String = "", exes: List<JsonObject> = emptyList()) =
    js("dow" to dow, "name" to name, "ex" to exes)

private fun week(id: String, startIso: String = "2026-03-23", days: List<JsonObject> = emptyList()) =
    js("id" to id, "startIso" to startIso, "name" to "", "days" to days)

private fun state(vararg weeks: JsonObject): JsonObject = js("weeks" to weeks.toList())

private fun weekAt(s: JsonObject, index: Int = 0): JsonObject = (s["weeks"] as JsonArray)[index] as JsonObject

private fun daysOf(s: JsonObject): JsonArray = weekAt(s)["days"] as JsonArray

private fun exesOf(s: JsonObject): JsonArray = ((daysOf(s)[0] as JsonObject)["ex"]) as JsonArray

private fun idsOf(s: JsonObject): List<String> = exesOf(s).map { (it as JsonObject).str("id")!! }

class PlanEditTest {

    @Test
    fun `addWeek appends an empty week and leaves the others alone`() {
        val before = state(week("a", "2026-03-23"))
        val after = addWeek(before, "b", "2026-03-30")
        assertEquals(2, (after["weeks"] as JsonArray).size)
        assertEquals("b", weekAt(after, 1).str("id"))
        assertEquals("2026-03-30", weekAt(after, 1).str("startIso"))
        assertEquals("", weekAt(after, 1).str("name"))
        assertEquals(0, (weekAt(after, 1)["days"] as JsonArray).size)
        assertEquals(weekAt(before), weekAt(after))
    }

    @Test
    fun `deleteWeek removes that week only, and an unknown id changes nothing`() {
        val before = state(week("a"), week("b"))
        val after = deleteWeek(before, "a")
        assertEquals(listOf("b"), (after["weeks"] as JsonArray).map { (it as JsonObject).str("id") })
        assertSame(before, deleteWeek(before, "nope"))
    }

    @Test
    fun `setWeekName keeps the keys this app does not model`() {
        val before = state(
            js("id" to "a", "startIso" to "2026-03-23", "name" to "", "days" to emptyList<JsonObject>(), "customEx" to emptyList<JsonObject>()),
        )
        val after = setWeekName(before, "a", "October block")
        assertEquals("October block", weekAt(after).str("name"))
        assertEquals("[]", weekAt(after)["customEx"].toString())
        assertSame(before, setWeekName(before, "nope", "x"))
    }

    @Test
    fun `addDay lands on the given weekday with no exercises`() {
        val before = state(week("a"))
        val after = addDay(before, "a", 3, "New day")
        val d = daysOf(after)[0] as JsonObject
        assertEquals(3, d["dow"].toString().toInt())
        assertEquals("New day", d.str("name"))
        assertEquals(0, (d["ex"] as JsonArray).size)
        assertSame(before, addDay(before, "nope", 3, "New day"))
    }

    @Test
    fun `deleteDay drops the day at that index`() {
        val before = state(week("a", days = listOf(day(1), day(3))))
        val after = deleteDay(before, "a", 0)
        assertEquals(listOf(3), daysOf(after).map { (it as JsonObject)["dow"].toString().toInt() })
        assertEquals(before, deleteDay(before, "a", 9))
    }

    @Test
    fun `setDayDow and setDayName keep the day's other keys`() {
        val flagged = js(
            "dow" to 1, "name" to "Squat", "ex" to emptyList<JsonObject>(),
            "excludeFromProgression" to true, "future" to "x",
        )
        val before = state(week("a", days = listOf(flagged)))
        val after = setDayName(setDayDow(before, "a", 0, 5), "a", 0, "Pull")
        val d = daysOf(after)[0] as JsonObject
        assertEquals(5, d["dow"].toString().toInt())
        assertEquals("Pull", d.str("name"))
        assertEquals("true", d["excludeFromProgression"].toString())
        assertEquals("x", d.str("future"))
    }

    @Test
    fun `addExToDay appends the config as written`() {
        val after = addExToDay(state(week("a", days = listOf(day(1)))), "a", 0, ex("wl58", sets = 5, reps = 3))
        assertEquals(listOf("wl58"), idsOf(after))
        assertEquals("5", (exesOf(after)[0] as JsonObject)["sets"].toString())
    }

    @Test
    fun `setExAt keeps the row's id and superset id`() {
        val before = state(week("a", days = listOf(day(1, exes = listOf(ex("wl58", weight = 60.0, sg = "sg1"))))))
        val after = setExAt(before, "a", 0, 0, js("sets" to 4, "reps" to 2, "weight" to 65.0))
        val row = exesOf(after)[0] as JsonObject
        assertEquals("wl58", row.str("id"))
        assertEquals("sg1", row.str("sg"))
        assertEquals("4", row["sets"].toString())
        assertEquals("65.0", row["weight"].toString())
    }

    @Test
    fun `removeExAt drops a now-single-member group's id`() {
        val before = state(week("a", days = listOf(day(1, exes = listOf(ex("a", sg = "sg1"), ex("b", sg = "sg1"))))))
        val after = removeExAt(before, "a", 0, 0)
        assertEquals(listOf("b"), idsOf(after))
        assertNull((exesOf(after)[0] as JsonObject)["sg"])
        assertEquals(before, removeExAt(before, "a", 0, 9))
    }

    @Test
    fun `toggleLinkAt links the pair above, then unlinks it`() {
        val before = state(week("a", days = listOf(day(1, exes = listOf(ex("a"), ex("b"))))))
        val linked = toggleLinkAt(before, "a", 0, 1)
        val sg = (exesOf(linked)[0] as JsonObject).str("sg")
        assertEquals(sg, (exesOf(linked)[1] as JsonObject).str("sg"))
        assertEquals(true, !sg.isNullOrEmpty())
        val unlinked = toggleLinkAt(linked, "a", 0, 1)
        assertNull((exesOf(unlinked)[0] as JsonObject)["sg"])
        assertNull((exesOf(unlinked)[1] as JsonObject)["sg"])
    }

    @Test
    fun `toggleLinkAt does nothing on the first row`() {
        val before = state(week("a", days = listOf(day(1, exes = listOf(ex("a"))))))
        assertEquals(before, toggleLinkAt(before, "a", 0, 0))
        assertSame(before, toggleLinkAt(before, "nope", 0, 1))
    }

    @Test
    fun `moveUnitAt moves a whole complex past its neighbour`() {
        val before = state(
            week("a", days = listOf(day(1, exes = listOf(ex("a", sg = "g"), ex("b", sg = "g"), ex("c"))))),
        )
        val after = moveUnitAt(before, "a", 0, 0, 1)
        assertEquals(listOf("c", "a", "b"), idsOf(after))
    }

    @Test
    fun `moveUnitAt at a boundary, or on an unknown week, changes nothing`() {
        val before = state(week("a", days = listOf(day(1, exes = listOf(ex("a"), ex("b"))))))
        assertSame(before, moveUnitAt(before, "a", 0, 0, -1))
        assertSame(before, moveUnitAt(before, "a", 0, 1, 1))
        assertSame(before, moveUnitAt(before, "nope", 0, 0, 1))
    }

    @Test
    fun `patchUnit writes the shared numbers to every member of the unit only`() {
        val before = state(
            week(
                "a",
                days = listOf(day(1, exes = listOf(ex("a", sets = 1, reps = 3, sg = "g"), ex("b", sets = 1, reps = 3, sg = "g"), ex("c", sets = 2)))),
            ),
        )
        val after = patchUnit(before, "a", 0, 0, js("sets" to 3, "weight" to 30.0))
        val rows = exesOf(after).map { it as JsonObject }
        assertEquals("3", rows[0]["sets"].toString())
        assertEquals("30.0", rows[0]["weight"].toString())
        assertEquals("3", rows[1]["sets"].toString())
        assertEquals("3", rows[1]["reps"].toString())
        assertEquals("2", rows[2]["sets"].toString())
        assertEquals(before, patchUnit(before, "a", 0, 7, js("sets" to 9)))
    }

    @Test
    fun `a day keeps excludeFromProgression and an unknown key through an edit`() {
        val flagged = js(
            "dow" to 1, "name" to "Squat", "ex" to listOf(ex("wl77")),
            "excludeFromProgression" to true, "future" to "x",
        )
        val before = state(week("a", days = listOf(flagged)))
        val after = setDayName(addExToDay(before, "a", 0, ex("wl78")), "a", 0, "Squat +")
        val d = daysOf(after)[0] as JsonObject
        assertEquals("true", d["excludeFromProgression"].toString())
        assertEquals("x", d.str("future"))
        assertEquals(2, (d["ex"] as JsonArray).size)
    }
}

package olygym.app.lib

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import olygym.app.data.asObj
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Test

/** The spec of frontend/src/lib/backfill.test.js, one case each. */
class BackfillTest {

    private fun w(id: String, d: String, start: Int = 0): JsonObject =
        js("id" to id, "d" to d, "start" to start)

    private fun chronoList(): JsonArray = JsonArray(
        listOf(
            w("a", "2026-01-01", 10),
            w("b", "2026-01-05", 10),
            w("c", "2026-01-05", 20),
            w("d", "2026-01-09", 10),
        ),
    )

    private fun completeList(): JsonArray = JsonArray(
        listOf(
            w("a", "2026-01-01", 10),
            w("b", "2026-01-05", 10),
            w("d", "2026-01-09", 10),
        ),
    )

    @Test
    fun `returns every workout of that day and nothing else`() {
        val S = js("workouts" to listOf(w("a", "2026-01-01"), w("b", "2026-01-02"), w("c", "2026-01-02")))
        assertEquals(listOf("b", "c"), workoutsOn(S, "2026-01-02").map { it.asObj()?.str("id") })
        assertEquals(emptyList<String>(), workoutsOn(S, "2026-01-03").map { it.asObj()?.str("id") })
        assertEquals(emptyList<String>(), workoutsOn(js(), "2026-01-03").map { it.asObj()?.str("id") })
    }

    @Test
    fun `lands on the chosen day at the chosen time in local zone`() {
        val t = Instant.ofEpochMilli(backfillStart("2026-03-10", "07:45")).atZone(ZoneId.systemDefault())
        assertEquals(listOf(2026, 3, 10, 7, 45), listOf(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute))
    }

    @Test
    fun `defaults to 18-00 and ends after the given duration`() {
        val start = backfillStart("2026-03-10")
        assertEquals(18, Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).hour)
        assertEquals(start + 45L * 60000L, backfillEnd(js("start" to start, "backfill" to js("durationMin" to 45))))
        assertEquals(start + 60L * 60000L, backfillEnd(js("start" to start, "backfill" to js())))
        assertEquals(start + 60L * 60000L, backfillEnd(js("start" to start, "backfill" to js("durationMin" to 0))))
    }

    @Test
    fun `places by date, then by start time, after equal keys`() {
        val list = chronoList()
        assertEquals(
            listOf("a", "x", "b", "c", "d"),
            insertChronological(list, w("x", "2026-01-03")).map { it.asObj()?.str("id") },
        )
        assertEquals(
            listOf("a", "b", "x", "c", "d"),
            insertChronological(list, w("x", "2026-01-05", 15)).map { it.asObj()?.str("id") },
        )
        assertEquals(
            listOf("a", "b", "c", "x", "d"),
            insertChronological(list, w("x", "2026-01-05", 20)).map { it.asObj()?.str("id") },
        )
    }

    @Test
    fun `appends at the end and inserts at the front`() {
        val list = chronoList()
        assertEquals("x", insertChronological(list, w("x", "2026-02-01")).last().asObj()?.str("id"))
        assertEquals("x", insertChronological(list, w("x", "2025-12-31")).first().asObj()?.str("id"))
        assertEquals(
            listOf("x"),
            insertChronological(JsonArray(emptyList()), w("x", "2026-01-01")).map { it.asObj()?.str("id") },
        )
    }

    @Test
    fun `does not mutate the input`() {
        val list = chronoList()
        val copy = chronoList()
        insertChronological(list, w("x", "2026-01-03"))
        assertEquals(copy, list)
    }

    @Test
    fun `adds a second workout on a day in order`() {
        val out = completeBackfill(
            completeList(),
            js("backfill" to js("durationMin" to 60, "replaceId" to JsonNull)),
            w("x", "2026-01-05", 5),
        )
        assertEquals(listOf("a", "x", "b", "d"), out.map { it.asObj()?.str("id") })
    }

    @Test
    fun `replaces the chosen workout`() {
        val list = completeList()
        val out = completeBackfill(
            list,
            js("backfill" to js("durationMin" to 60, "replaceId" to "b")),
            w("x", "2026-01-05", 30),
        )
        assertEquals(listOf("a", "x", "d"), out.map { it.asObj()?.str("id") })
        assertEquals(3, list.size)
    }
}

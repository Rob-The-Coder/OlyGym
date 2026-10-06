package olygym.app.lib

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import olygym.app.data.Routine
import olygym.app.data.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The specification of frontend/src/lib/migrate-weeks.test.js, one case each. Wednesday 25 March
 * 2026 — the week that contains it starts Monday 2026-03-23, so the coach weeks land on 2026-03-16
 * and the leftover week on 2026-03-30. Pinned, never the clock.
 */
private val NOW = LocalDate.parse("2026-03-25")

private fun ex(id: String): List<JsonObject> =
    listOf(Json.parseToJsonElement("""{"id":"$id","sets":3,"reps":5}""").jsonObject)

private fun r(id: String, name: String, exclude: Boolean? = null) =
    Routine(id = id, name = name, ex = ex(id), excludeFromProgression = exclude)

/** Fresh, predictable week ids, so a case can be read without eyeballing a timestamp. */
private fun ids(): () -> String {
    var n = 0
    return { "w" + n++ }
}

class MigrateWeeksTest {

    @Test
    fun `puts Monday, Wednesday and Friday first, like the import does`() {
        assertEquals(listOf(1, 3, 5, 2, 4, 6, 0), (0..6).map { weekDayForPosition(it) })
    }

    @Test
    fun `turns the scheduled week into one week starting on the current Monday`() {
        val r1 = r("r1", "Push")
        val r2 = r("r2", "Legs")
        val weeks = migrateToWeeks(
            routines = listOf(r1, r2),
            week = mapOf("1" to listOf("r1"), "5" to listOf("r2")),
            weekStart = 1,
            now = NOW,
            newId = ids(),
        )
        assertEquals(1, weeks.size)
        assertEquals("2026-03-23", weeks[0].startIso)
        assertEquals(listOf(1, 5), weeks[0].days.map { it.dow })
        assertEquals(listOf("Push", "Legs"), weeks[0].days.map { it.name })
        assertSame(r1.ex, weeks[0].days[0].ex)
    }

    @Test
    fun `keeps every routine of a multi-routine weekday as its own day`() {
        val weeks = migrateToWeeks(
            routines = listOf(r("a", "A"), r("b", "B")),
            week = mapOf("2" to listOf("a", "b")),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )
        assertEquals(listOf(2 to "A", 2 to "B"), weeks[0].days.map { it.dow to it.name })
    }

    @Test
    fun `groups the coach-named routines into one week per label, on Monday, Wednesday and Friday`() {
        val weeks = migrateToWeeks(
            routines = listOf(
                r("g2", "Giorno 2 · 23-29 marzo"),
                r("g1", "Giorno 1 · 23-29 marzo"),
                r("g3", "Giorno 3 · 23-29 marzo"),
            ),
            week = emptyMap(),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )
        assertEquals(1, weeks.size)
        assertEquals("23-29 marzo", weeks[0].name)
        assertEquals("2026-03-16", weeks[0].startIso)
        assertEquals(listOf(1, 3, 5), weeks[0].days.map { it.dow })
        // Ordered by the number the coach wrote, not by where the routine sits in the array.
        assertEquals(
            listOf("Giorno 1 · 23-29 marzo", "Giorno 2 · 23-29 marzo", "Giorno 3 · 23-29 marzo"),
            weeks[0].days.map { it.name },
        )
    }

    @Test
    fun `puts two labels on consecutive past Mondays`() {
        val weeks = migrateToWeeks(
            routines = listOf(r("a", "Giorno 1 · 16-22 marzo"), r("b", "Giorno 1 · 23-29 marzo")),
            week = emptyMap(),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )
        assertEquals(
            listOf("16-22 marzo" to "2026-03-09", "23-29 marzo" to "2026-03-16"),
            weeks.map { it.name to it.startIso },
        )
    }

    @Test
    fun `collects the leftovers into one unnamed week after the current one`() {
        val weeks = migrateToWeeks(
            routines = listOf(r("x", "Freestyle"), r("y", "Mobility")),
            week = emptyMap(),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )
        assertEquals(1, weeks.size)
        assertEquals("2026-03-30", weeks[0].startIso)
        assertEquals("", weeks[0].name)
        assertEquals(listOf(1 to "Freestyle", 3 to "Mobility"), weeks[0].days.map { it.dow to it.name })
    }

    @Test
    fun `returns the three groups sorted by start date, and nothing when there is nothing to keep`() {
        val weeks = migrateToWeeks(
            routines = listOf(
                r("n", "Giorno 1 · 23-29 marzo"),
                r("s", "Scheduled"),
                r("l", "Leftover"),
            ),
            week = mapOf("4" to listOf("s")),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )
        assertEquals(listOf("2026-03-16", "2026-03-23", "2026-03-30"), weeks.map { it.startIso })
        assertEquals(emptyList<Any>(), migrateToWeeks(emptyList(), emptyMap(), null, NOW, ids()))
        // A schedule slot pointing at a routine that no longer exists is not a day.
        assertEquals(
            emptyList<Any>(),
            migrateToWeeks(emptyList(), mapOf("1" to listOf("gone")), null, NOW, ids()),
        )
    }

    @Test
    fun `carries exercises and excludeFromProgression over, and leaves the old plan untouched`() {
        val bad = r("bad", "Rehab", exclude = true)
        val good = r("good", "Push")
        val routines = listOf(bad, good)
        val days = migrateToWeeks(
            routines = routines,
            week = mapOf("1" to listOf("bad", "good")),
            weekStart = null,
            now = NOW,
            newId = ids(),
        )[0].days

        assertSame(bad.ex, days[0].ex)
        assertEquals(true, days[0].excludeFromProgression)
        // The web test asserts the *key* is absent; null is how this model says the same thing, and
        // the serializer writes nothing for it.
        assertNull(days[1].excludeFromProgression)
        assertEquals(2, days.size)
        // The input is immutable, so "untouched" is that the same instances still carry the same
        // values, including the flag the old plan set.
        assertEquals(true, routines[0].excludeFromProgression)
        assertEquals("Rehab", routines[0].name)
        assertEquals(2, routines.size)
    }

    @Test
    fun `gives every week its own id`() {
        val weeks = migrateToWeeks(
            routines = listOf(
                r("n", "Giorno 1 · 23-29 marzo"),
                r("s", "Scheduled"),
                r("l", "Leftover"),
            ),
            week = mapOf("1" to listOf("s")),
            weekStart = null,
            now = NOW,
        )
        val ids = weeks.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotEmpty() })
    }

    @Test
    fun `needs the migration only for an old plan with no weeks yet`() {
        assertTrue(needsWeekMigration(emptyList(), listOf(r("a", "A"))))
        assertEquals(false, needsWeekMigration(listOf(Week(id = "w")), listOf(r("a", "A"))))
        assertEquals(false, needsWeekMigration(emptyList(), emptyList()))
    }
}

package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.asBool
import olygym.app.data.asNum
import olygym.app.data.asStr
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The spec of frontend/src/lib/competition.test.js, one case each, in the same order.
 */

private fun made(w: Double): JsonObject = js("w" to w, "made" to true)
private fun miss(w: Double): JsonObject = js("w" to w, "made" to false)

private fun meet(
    d: String,
    snatch: List<JsonElement> = emptyList(),
    cj: List<JsonElement> = emptyList(),
    extra: Map<String, JsonElement> = emptyMap(),
): JsonObject = JsonObject(
    linkedMapOf<String, JsonElement>(
        "id" to JsonPrimitive("m-$d"),
        "d" to JsonPrimitive(d),
        "snatch" to JsonArray(snatch),
        "cj" to JsonArray(cj),
    ) + extra,
)

private fun defaultMale(): List<String> =
    (DEFAULT_CLASSES["male"] as JsonArray).map { it.asStr()!! }

private val orderedMeets = listOf(
    meet("2026-03-10", listOf(made(100.0)), listOf(made(130.0))),
    meet("2025-12-01", listOf(made(90.0)), listOf(made(120.0))),
    meet("2026-06-20", listOf(made(105.0)), listOf(made(135.0))),
)

class CompetitionTest {

    @Test
    fun `only a made attempt with a weight counts`() {
        assertTrue(attemptMade(made(100.0)))
        assertFalse(attemptMade(miss(100.0)))
        assertFalse(attemptMade(made(0.0)))
        assertFalse(attemptMade(null))
    }

    @Test
    fun `best skips no-lifts even when they were heavier`() {
        assertEquals(115.0, bestAttempt(JsonArray(listOf(miss(120.0), made(115.0), made(110.0))))!!, 0.0)
        assertNull(bestAttempt(JsonArray(listOf(miss(120.0), miss(115.0)))))
        assertNull(bestAttempt(JsonArray(emptyList())))
        assertNull(bestAttempt(null))
    }

    @Test
    fun `needs something made in both lifts`() {
        assertEquals(230.0, totalOf(meet("2026-01-01", listOf(made(100.0)), listOf(made(130.0))))!!, 0.0)
        assertNull(totalOf(meet("2026-01-01", listOf(miss(100.0)), listOf(made(130.0)))))
        assertNull(totalOf(meet("2026-01-01", listOf(made(100.0)), emptyList())))
        assertNull(totalOf(null))
    }

    @Test
    fun `is the best made attempt of each lift, not the opener`() {
        val total = totalOf(
            meet("2026-01-01", listOf(miss(110.0), made(105.0)), listOf(miss(140.0), made(135.0))),
        )
        assertEquals(240.0, total!!, 0.0)
    }

    @Test
    fun `hasResult and hasAttempts`() {
        assertTrue(hasResult(meet("2026-01-01", listOf(made(100.0)), listOf(made(130.0)))))
        assertFalse(hasResult(meet("2026-01-01", listOf(made(100.0)), emptyList())))
        assertTrue(hasAttempts(meet("2026-01-01", listOf(miss(100.0)), emptyList())))
        assertFalse(hasAttempts(meet("2026-01-01", emptyList(), emptyList())))
    }

    @Test
    fun `sorted oldest first`() {
        assertEquals(
            listOf("2025-12-01", "2026-03-10", "2026-06-20"),
            sortedMeets(JsonArray(orderedMeets)).map { it["d"].asStr() },
        )
    }

    @Test
    fun `upcoming is today or later, soonest first, past is most recent first`() {
        assertEquals(
            listOf("2026-03-10", "2026-06-20"),
            upcomingMeets(JsonArray(orderedMeets), "2026-03-10").map { it["d"].asStr() },
        )
        assertEquals(
            listOf("2025-12-01"),
            pastMeets(JsonArray(orderedMeets), "2026-03-10").map { it["d"].asStr() },
        )
    }

    @Test
    fun `nextMeet is the soonest one ahead, and null when there is none`() {
        assertEquals("2026-03-10", nextMeet(JsonArray(orderedMeets), "2026-03-10")?.get("d").asStr())
        assertNull(nextMeet(JsonArray(orderedMeets), "2026-07-01"))
    }

    @Test
    fun `daysUntil counts whole days across a month boundary`() {
        assertEquals(9, daysUntil(meet("2026-03-10"), "2026-03-01"))
        assertEquals(-9, daysUntil(meet("2026-03-01"), "2026-03-10"))
        assertEquals(0, daysUntil(meet("2026-03-01"), "2026-03-01"))
        assertNull(daysUntil(js("d" to ""), "2026-03-01"))
    }

    @Test
    fun `picks the best made lift of each kind across meets`() {
        val b = competitionBests(
            JsonArray(
                listOf(
                    meet("2026-01-01", listOf(made(100.0), miss(110.0)), listOf(made(130.0))),
                    meet("2026-02-01", listOf(made(105.0)), listOf(miss(140.0), made(138.0))),
                ),
            ),
        )
        // The total is the best single-meet total (243), not best snatch + best cj, which could
        // have come from two different meets and never happened on one platform.
        assertEquals(CompetitionBests(105.0, 138.0, 243.0), b)
    }

    @Test
    fun `nulls when nothing was ever made`() {
        assertEquals(CompetitionBests(null, null, null), competitionBests(JsonArray(emptyList())))
        assertEquals(
            CompetitionBests(null, null, null),
            competitionBests(JsonArray(listOf(meet("2026-01-01", listOf(miss(100.0)), emptyList())))),
        )
    }

    @Test
    fun `attemptRows always pads to three`() {
        assertEquals(
            listOf(
                js("w" to 100.0, "made" to true),
                js("w" to 0.0, "made" to true),
                js("w" to 0.0, "made" to true),
            ),
            attemptRows(meet("2026-01-01", listOf(made(100.0)), emptyList()), "snatch"),
        )
        assertEquals(MAX_ATTEMPTS, attemptRows(meet("2026-01-01", listOf(made(100.0)), emptyList()), "cj").size)
        assertEquals(MAX_ATTEMPTS, attemptRows(null, "snatch").size)
    }

    @Test
    fun `cleanAttempts drops weightless rows and rounds to a tenth`() {
        // The JS list holds a null row; the declared element type is non-null, so it arrives
        // through the erasure the same way the looser JS shape would.
        @Suppress("UNCHECKED_CAST")
        val attempts = listOf(
            js("w" to 100.04, "made" to true),
            js("w" to 0.0, "made" to true),
            null,
        ) as List<JsonObject>
        assertEquals(listOf(js("w" to 100.0, "made" to true)), cleanAttempts(attempts))
    }

    @Test
    fun `upsert replaces by id and keeps date order`() {
        val a = meet("2026-01-01")
        val b = meet("2026-03-01")
        val withB = upsertMeet(JsonArray(listOf(a)), b)
        assertEquals(listOf("m-2026-01-01", "m-2026-03-01"), withB.map { it["id"].asStr() })
        val edited = upsertMeet(JsonArray(withB), JsonObject(a + ("name" to JsonPrimitive("Regionale"))))
        assertEquals(2, edited.size)
        assertEquals("Regionale", edited.first { it["id"].asStr() == "m-2026-01-01" }["name"].asStr())
    }

    @Test
    fun `removeMeet drops only that id`() {
        assertEquals(
            emptyList<JsonObject>(),
            removeMeet(JsonArray(listOf(meet("2026-01-01"))), "m-2026-01-01"),
        )
    }

    @Test
    fun `blankMeet is a complete, empty record`() {
        val m = blankMeet("2026-05-05")
        assertEquals("2026-05-05", m["d"].asStr())
        assertEquals(JsonArray(emptyList()), m["snatch"])
        assertEquals(JsonArray(emptyList()), m["cj"])
        assertTrue(m["id"].asStr() != null)
        assertTrue((m["id"].asStr() ?: "").isNotEmpty())
        assertEquals(
            setOf("id", "d", "name", "place", "class", "bw", "snatch", "cj", "placing", "note"),
            m.keys,
        )
    }

    @Test
    fun `labels a list for a picker`() {
        assertEquals(
            listOf(ClassOption("60", "60 kg"), ClassOption("65", "65 kg")),
            classOptions(listOf("60", "65")),
        )
        assertEquals(listOf(ClassOption("110+", "110+ kg")), classOptions(listOf("110+")))
        assertEquals(emptyList<ClassOption>(), classOptions(null))
    }

    @Test
    fun `classLists normalises, and a pre-split flat list reads as the male one`() {
        assertEquals(ClassLists(defaultMale(), emptyList()), classLists(null))
        assertEquals(
            ClassLists(listOf("60"), listOf("55")),
            classLists(js("male" to listOf("60"), "female" to listOf("55"))),
        )
        assertEquals(
            ClassLists(listOf("60", "65"), emptyList()),
            classLists(JsonArray(listOf(JsonPrimitive("60"), JsonPrimitive(" 60 "), JsonPrimitive("65")))),
        )
    }

    @Test
    fun `listFor follows the profile body, defaulting to male`() {
        assertEquals(
            listOf("55"),
            listFor(js("body" to "female", "classes" to js("male" to listOf("60"), "female" to listOf("55")))),
        )
        assertEquals(
            listOf("60"),
            listFor(js("body" to "male", "classes" to js("male" to listOf("60"), "female" to listOf("55")))),
        )
        assertEquals(defaultMale(), listFor(JsonObject(emptyMap())))
    }

    @Test
    fun `cleanClasses trims, drops empties and de-duplicates, keeping order`() {
        assertEquals(
            listOf("60", "65", "110+"),
            cleanClasses(
                listOf(
                    JsonPrimitive(" 60 "),
                    JsonPrimitive(""),
                    JsonPrimitive("65"),
                    JsonPrimitive("60"),
                    JsonNull,
                    JsonPrimitive("110+"),
                ),
            ),
        )
        assertEquals(emptyList<String>(), cleanClasses(null))
    }
}

package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The spec of frontend/src/lib/import-plan.test.js, one case each, in the same order. The catalogue
 * is the committed asset the app installs at startup, read here where a JVM test can reach it.
 */

private val importJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val importCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    importJson.decodeFromString<List<Exercise>>(file.readText())
}

/** The catalogue id for a canonical name, exactly as the JS test looks it up. */
private fun idOf(name: String): String {
    val ex = importCatalogue.firstOrNull { it.n == name }
    checkNotNull(ex) { "\"" + name + "\" is not in the catalogue any more" }
    return ex.id
}

private fun importGrid(rows: List<Map<String, String>>): List<List<String>> =
    rows.map { cells ->
        val row = MutableList(16) { "" }
        cells.forEach { (col, value) ->
            val index = when (col) {
                "day" -> COACH_COLUMNS.day
                "name" -> COACH_COLUMNS.name
                "reps" -> COACH_COLUMNS.reps
                "sets" -> COACH_COLUMNS.sets
                "load" -> COACH_COLUMNS.load
                "cue" -> COACH_COLUMNS.cue
                "comment" -> COACH_COLUMNS.comment
                else -> error("unknown column " + col)
            }
            row[index] = value
        }
        row
    }

private fun coachRow(vararg cells: Pair<String, String>): Map<String, String> = cells.toMap()

// One week, written the way the coach writes them: a primer line, a complex, a load with an
// instruction, a timed hold and a row of his own that the parser has to keep.
private val importWeek = XlsxSheet(
    name = "Settimana 23-29 marzo 2026",
    grid = importGrid(
        listOf(
            coachRow("day" to "Giorno 1", "name" to "Snatch primer (sequenza che fai di solito)"),
            coachRow(
                "name" to "Strappo + strappo sosp alta",
                "reps" to "1+2",
                "sets" to "4.0",
                "load" to "50kg , se leggeri le ultime due a 55kg",
            ),
            coachRow("name" to "Piegamenti alle parallele", "reps" to "10.0", "sets" to "3.0"),
            coachRow(
                "day" to "Giorno 2",
                "name" to "Strappo no piedi",
                "reps" to "3.0",
                "sets" to "4.0",
                "load" to "Due a 65kg, due a 70kg",
            ),
            coachRow("name" to "Plank", "reps" to "1 minuto", "sets" to "3.0"),
            coachRow("name" to "Pogo jump"),
            coachRow("day" to "Giorno 3", "name" to "Jerk primer*"),
        ),
    ),
)

class ImportPlanTest {

    @Before
    fun installCatalogue() {
        Catalogue.install(importCatalogue)
    }

    /* ------------------------------------------------------------- sheetLabel -- */

    @Test
    fun `turns a sheet name into the dates a routine can be called`() {
        assertEquals("23-29 marzo", sheetLabel("Settimana 23-29 marzo 2026"))
        assertEquals("8 - 15 giugno", sheetLabel("Settimana 8 - 15 giugno"))
        assertEquals("21-27 settembre", sheetLabel("21-27 settembre"))
        assertEquals("2", sheetLabel("Settimana 2"))
    }

    /* ------------------------------------------------------------- loadWeight -- */

    @Test
    fun `takes the load the text opens with`() {
        assertEquals(50.0, loadWeight("50kg , se leggeri le ultime due a 55kg") ?: -1.0, 1e-9)
        assertEquals(70.0, loadWeight("70-75kg") ?: -1.0, 1e-9)
        assertEquals(40.0, loadWeight("Max 40kg") ?: -1.0, 1e-9)
        assertEquals(90.0, loadWeight("90,95 95") ?: -1.0, 1e-9)
    }

    @Test
    fun `refuses a number that is an instruction`() {
        // The whole point of the rule: "poi togli 10kg" is not a 10 kg bar.
        assertNull(loadWeight("Trova 6RM, poi togli 10kg e fai due serie a 7 rep"))
        assertNull(loadWeight("Due a 65kg, due a 70kg"))
        assertNull(loadWeight("Prima a 45, le altre tre a 50kg"))
        assertNull(loadWeight("1RM"))
        assertNull(loadWeight("5 minuti"))
        assertNull(loadWeight("bilanciere vuoto"))
        assertNull(loadWeight("Tesatura con elastico"))
        assertNull(loadWeight(""))
    }

    /* ------------------------------------------------------------- schemesFor -- */

    @Test
    fun `reads one scheme per exercise`() {
        assertEquals(listOf(Scheme(reps = 1), Scheme(reps = 2)), schemesFor("1+2", 2).schemes)
        assertEquals(listOf(Scheme(reps = 4)), schemesFor("4.0", 1).schemes)
        assertEquals(listOf(Scheme(reps = 3), Scheme(reps = 3), Scheme(reps = 3)), schemesFor("3.0", 3).schemes)
    }

    @Test
    fun `reads a hold and a per-side count`() {
        assertEquals(
            listOf(Scheme(mode = "time", sec = 30), Scheme(reps = 10, side = true)),
            schemesFor("30 secondi + 10 rep per lato", 2).schemes,
        )
        assertEquals(listOf(Scheme(mode = "time", sec = 60)), schemesFor("1 minuto", 1).schemes)
        assertEquals(listOf(Scheme(reps = 10, side = true)), schemesFor("10 per lato", 1).schemes)
    }

    @Test
    fun `reads the coach asking for a rep max or an AMRAP`() {
        assertEquals(listOf(Scheme(reps = 5, rmax = true)), schemesFor("Trova 5RM", 1).schemes)
        val amrap = schemesFor("Amrap", 1).schemes[0]
        assertEquals(10, amrap?.reps)
        assertEquals(true, amrap?.amrap)
        // Neither has a field in the plan, so the coach's words ride in the note: without this
        // "Amrap" reaches the review as a plain ten reps with nothing saying otherwise.
        assertEquals("Trova 5RM", schemesFor("Trova 5RM", 1).note)
        assertEquals("Amrap", schemesFor("Amrap", 1).note)
        assertEquals("Amrap", schemesFor("Amrap", 2).note)
        assertEquals("", schemesFor("3.0", 1).note)
    }

    @Test
    fun `keeps a scheme that does not line up with the exercises`() {
        // "Slancio 1+1" is one clean and jerk, not two exercises: the first number is the set and the
        // coach's own "1+1" goes in the note rather than disappearing.
        assertEquals(listOf(Scheme(reps = 1)), schemesFor("1+1", 1).schemes)
        assertEquals("1+1", schemesFor("1+1", 1).note)
        assertEquals(listOf(Scheme(reps = 2), Scheme(reps = 2), Scheme(reps = 1)), schemesFor("2+1", 3).schemes)
        assertEquals("2+1", schemesFor("2+1", 3).note)
    }

    /* ------------------------------------------------------------ reviewWeek -- */

    private val review: WeekReview by lazy { reviewWeek(importWeek) }

    @Test
    fun `proposes one exercise per component and groups a complex`() {
        val day1 = review.days[0]
        assertEquals("Giorno 1 · 23-29 marzo", day1.name)
        assertEquals(listOf("snatch", "hang snatch", "dip"), day1.entries.map { it.name })
        assertEquals(idOf("snatch"), day1.entries[0].id)
        assertEquals(day1.entries[1].sg, day1.entries[0].sg)
        assertNull(day1.entries[2].sg)
    }

    @Test
    fun `puts an AMRAP on the note instead of leaving ten reps unexplained`() {
        val r = reviewWeek(
            XlsxSheet("Settimana", importGrid(listOf(coachRow("day" to "Giorno 1", "name" to "Snatch", "reps" to "Amrap", "sets" to "3.0")))),
        )
        assertEquals(10, r.days[0].entries[0].reps)
        assertEquals("Amrap", r.days[0].entries[0].note)
    }

    @Test
    fun `carries the reps and the sets the coach wrote`() {
        val day1 = review.days[0]
        assertEquals(4, day1.entries[0].sets)
        assertEquals(1, day1.entries[0].reps)
        assertEquals(50.0, day1.entries[0].weight ?: -1.0, 1e-9)
        assertEquals(4, day1.entries[1].sets)
        assertEquals(2, day1.entries[1].reps)
        assertEquals(50.0, day1.entries[1].weight ?: -1.0, 1e-9)
        val plank = review.days[1].entries.first { it.name == "plank" }
        assertEquals("time", plank.mode)
        assertEquals(60, plank.sec)
        assertNull(plank.reps)
    }

    @Test
    fun `keeps what the catalogue has no word for in the note`() {
        // "no piedi" IS a Catalyst exercise ("snatch with no jump"), so it arrives as one; the load
        // that is a sentence, which has no equivalent at all, is what stays in the note.
        val noFeet = review.days[1].entries[0]
        assertEquals("snatch with no jump", noFeet.name)
        assertEquals(1, noFeet.tier)
        // A load that is a sentence stays a note too, even though the weight came out of it.
        assertNull(noFeet.weight)
        assertTrue(noFeet.note.contains("Due a 65kg, due a 70kg"))
        assertTrue(review.days[0].entries[0].note.contains("se leggeri le ultime due a 55kg"))
    }

    @Test
    fun `defaults the sets and reps it was not given, and says so`() {
        val pogo = review.days[1].entries.first { it.custom }
        assertEquals("Pogo jump", pogo.name)
        assertEquals(3, pogo.sets)
        assertEquals(10, pogo.reps)
        assertTrue(pogo.warns.contains("sets"))
        assertTrue(pogo.warns.contains("reps"))
    }

    @Test
    fun `leaves the primer lines out and reports them`() {
        assertTrue(review.days[1].entries.none { it.name == "Jerk primer*" })
        assertTrue(review.skipped.any { it.text == "Snatch primer (sequenza che fai di solito)" })
        assertTrue(review.skipped.any { it.text == "Jerk primer*" })
    }

    @Test
    fun `drops a day with nothing in it`() {
        // Day 3 of the real week is only a primer line: an empty routine is not worth importing.
        assertEquals(listOf(1, 2), review.days.map { it.n })
    }

    @Test
    fun `counts what it understood`() {
        assertEquals(5, review.stats.rows)
        assertEquals(6, review.stats.exercises)
        assertEquals(1, review.stats.custom)
    }

    /* ---------------------------------------------------------- bundleFromWeek -- */

    private val bundle: JsonObject by lazy { bundleFromWeek(review, unit = "kg") }

    @Test
    fun `is one week of the dated plan, named after the sheet`() {
        val startIso = bundle.str("startIso").orEmpty()
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2}$""").matches(startIso))
        assertEquals("23-29 marzo", bundle.str("name"))
        assertEquals(
            listOf("Giorno 1 · 23-29 marzo", "Giorno 2 · 23-29 marzo"),
            bundle.arr("days").map { it.asObj()?.str("name") },
        )
        assertEquals(listOf(3, 3), bundle.arr("days").map { it.asObj()?.arr("ex")?.size })
    }

    @Test
    fun `schedules the days on the weekdays it was given, in order`() {
        assertEquals(
            listOf(1.0, 3.0),
            bundle.arr("days").map { it.asObj()?.num("dow") },
        )
        val chosen = bundleFromWeek(review, unit = "kg", days = listOf(2, 4))
        assertEquals(listOf(2.0, 4.0), chosen.arr("days").map { it.asObj()?.num("dow") })
    }

    @Test
    fun `imports as a new week, with the coach's custom exercise created once`() {
        val s = js("weeks" to emptyList<JsonObject>(), "customEx" to emptyList<JsonObject>(), "unit" to "kg")
        val first = mergeWeek(s, bundleFromWeek(review, unit = "kg")) ?: error("merge failed")
        val weeks = first.arr("weeks")
        assertEquals(1, weeks.size)
        assertEquals(listOf(1.0, 3.0), weeks[0].asObj()?.arr("days")?.map { it.asObj()?.num("dow") })
        assertEquals(idOf("snatch"), weeks[0].asObj()?.arr("days")?.get(0)?.asObj()?.arr("ex")?.get(0)?.asObj()?.str("id"))
        val custom = first.arr("customEx").filter { it.asObj()?.str("n") == "Pogo jump" }
        assertEquals(1, custom.size)
        assertEquals("Jumping & Plyometrics", custom[0].asObj()?.str("bp"))
        assertEquals(true, custom[0].asObj()?.bool("custom"))
        val customId = custom[0].asObj()?.str("id")
        val day2 = weeks[0].asObj()?.arr("days")?.get(1)?.asObj()?.arr("ex")
        assertTrue(day2?.any { it.asObj()?.str("id") == customId } == true)

        // A second week with the same custom reuses it instead of making a twin.
        val other = reviewWeek(XlsxSheet("Settimana 2", importWeek.grid))
        val second = mergeWeek(first, bundleFromWeek(other, unit = "kg")) ?: error("merge failed")
        assertEquals(1, second.arr("customEx").count { it.asObj()?.str("n") == "Pogo jump" })
        assertEquals(2, second.arr("weeks").size)
    }

    @Test
    fun `hands its numbers over in the account's own unit`() {
        // The sheet is in kilos. An account in pounds gets pounds, the same way a shared plan does.
        val inLb = bundleFromWeek(review, unit = "lb")
        val first = inLb.arr("days")[0].asObj()?.arr("ex")?.get(0)?.asObj()
        val expected = convertWeight(JsonPrimitive(50), "kg", "lb")?.asNum() ?: -1.0
        assertEquals(expected, first?.num("weight") ?: -2.0, 1e-9)
        assertEquals(4, first?.num("sets")?.toInt())
    }
}

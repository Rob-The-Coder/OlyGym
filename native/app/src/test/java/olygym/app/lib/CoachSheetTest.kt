package olygym.app.lib

import org.junit.Assert.*
import org.junit.Test

/*
 * The spec of frontend/src/lib/coach-sheet.test.js, one case each, in the same order. The grid is
 * built through COACH_COLUMNS, as the web builds it, so a case that writes a column is also a check
 * of the column map the reader uses.
 */

private val COACH_COL_INDEX = mapOf(
    "day" to COACH_COLUMNS.day,
    "name" to COACH_COLUMNS.name,
    "reps" to COACH_COLUMNS.reps,
    "sets" to COACH_COLUMNS.sets,
    "load" to COACH_COLUMNS.load,
    "cue" to COACH_COLUMNS.cue,
    "comment" to COACH_COLUMNS.comment,
)

// A grid the shape of the coach's sheets: day markers in A, the exercise in B, reps in G, sets in
// H, the load in I, a cue in J, his own comment in P. Built dense and padded, the way readXlsx
// hands a sheet over.
private fun grid(rows: List<Map<String, String>>): List<List<String>> = rows.map { cells ->
    val row = MutableList(16) { "" }
    for ((col, value) in cells) row[COACH_COL_INDEX.getValue(col)] = value
    row
}

class CoachSheetTest {

    @Test
    fun `splits the coaches complexes on +`() {
        assertEquals(listOf("Strappo", "strappo sosp alta"), splitComplex("Strappo + strappo sosp alta"))
        assertEquals(listOf("Strappo"), splitComplex("Strappo"))
        assertEquals(emptyList<String>(), splitComplex(""))
    }

    @Test
    fun `reads the numbers Excel writes`() {
        assertEquals(4, setsOf("4.0"))
        assertEquals(3, setsOf(" 3 "))
        assertEquals(1, setsOf("1"))
    }

    @Test
    fun `refuses anything that is not a set count`() {
        // One week of the real workbook has a date serial in the sets column; 46115 sets is not a
        // number to hand a routine.
        assertNull(setsOf("46115.0"))
        assertNull(setsOf(""))
        assertNull(setsOf("0"))
        assertNull(setsOf("-2"))
        assertNull(setsOf("due"))
    }

    @Test
    fun `reads the days and their exercises`() {
        val read = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Strappo + strappo sosp alta", "reps" to "1+2", "sets" to "4.0", "load" to "50kg"),
                    mapOf("name" to "Gambe avanti", "reps" to "3.0", "sets" to "5.0"),
                    mapOf("day" to "Giorno 2", "name" to "Girata", "reps" to "1+1", "sets" to "3.0"),
                ),
            ),
        )
        assertEquals(listOf(1, 2), read.days.map { it.n })
        assertEquals(listOf("Strappo + strappo sosp alta", "Gambe avanti"), read.days[0].entries.map { it.name })
        val first = read.days[0].entries[0]
        assertEquals(1, first.row)
        assertEquals("1+2", first.reps)
        assertEquals(4, first.sets)
        assertEquals("50kg", first.load)
        assertEquals(listOf("Girata"), read.days[1].entries.map { it.name })
        assertEquals(emptyList<CoachSkipped>(), read.skipped)
    }

    @Test
    fun `keeps the exercise written on the day marker row`() {
        // Row 3 of the real sheets is "Giorno 1" in A and the day's first exercise in B.
        val days = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Pogo jump"),
                    mapOf("name" to "Gambe avanti", "reps" to "3.0", "sets" to "4.0"),
                ),
            ),
        ).days
        assertEquals(listOf("Pogo jump", "Gambe avanti"), days[0].entries.map { it.name })
    }

    @Test
    fun `drops the primers and the legend block`() {
        val read = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Snatch primer (sequenza che fai di solito)"),
                    mapOf("name" to "Strappo", "reps" to "2.0", "sets" to "4.0"),
                    mapOf("day" to "Giorno 2", "name" to "Jerk primer*"),
                    mapOf("name" to "Spinte di forza", "reps" to "5.0", "sets" to "3.0"),
                    mapOf("day" to "Jerk Primer:", "name" to "Due serie per esercizio, 5 rep per serie"),
                    mapOf("day" to "Spinte dalla spaccata"),
                    mapOf("day" to "Spinte dalla mezza spaccata"),
                ),
            ),
        )
        assertEquals(listOf(listOf("Strappo"), listOf("Spinte di forza")), read.days.map { d -> d.entries.map { it.name } })
        // The legend labels are reported rather than silently eaten: the review screen lists them.
        assertEquals(
            listOf(
                "Snatch primer (sequenza che fai di solito)",
                "Jerk primer*",
                "Jerk Primer:",
                "Due serie per esercizio, 5 rep per serie",
                "Spinte dalla spaccata",
                "Spinte dalla mezza spaccata",
            ),
            read.skipped.map { it.text },
        )
    }

    @Test
    fun `stops reading exercises after the legend block`() {
        val days = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Strappo", "reps" to "2.0", "sets" to "4.0"),
                    mapOf("day" to "Jerk Primer:"),
                    mapOf("name" to "Qualcosa che il parser non deve leggere", "reps" to "5.0", "sets" to "3.0"),
                ),
            ),
        ).days
        assertEquals(listOf("Strappo"), days[0].entries.map { it.name })
    }

    @Test
    fun `survives a sheet with no markers, blank rows and short rows`() {
        val days = readCoachSheet(
            listOf(
                emptyList<String>(),
                listOf("", "Strappo", "", "", "", "", "2.0", "4.0"),
            ),
        ).days
        assertEquals(
            listOf(CoachDay(1, listOf(CoachEntry(2, "Strappo", "2.0", 4, "", "", "")))),
            days,
        )
    }

    @Test
    fun `continues a day instead of shadowing it when a marker repeats`() {
        val days = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Strappo"),
                    mapOf("day" to "Giorno 1", "name" to "Girata"),
                ),
            ),
        ).days
        assertEquals(1, days.size)
        assertEquals(listOf("Strappo", "Girata"), days[0].entries.map { it.name })
    }

    @Test
    fun `takes the cue and the comment with the row`() {
        val days = readCoachSheet(
            grid(
                listOf(
                    mapOf("day" to "Giorno 1", "name" to "Gambe dietro", "reps" to "6.0", "sets" to "3.0", "cue" to "Esci dalla buca forte", "comment" to "Sono delle sosp"),
                ),
            ),
        ).days
        assertEquals("Esci dalla buca forte", days[0].entries[0].cue)
        assertEquals("Sono delle sosp", days[0].entries[0].comment)
    }
}

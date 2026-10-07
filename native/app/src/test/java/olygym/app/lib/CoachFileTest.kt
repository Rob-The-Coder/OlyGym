package olygym.app.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/*
 * csv.js ships with no test in the web app; these are the cases its own comment names, plus the
 * shape a coach's sheet actually arrives in.
 */

class CoachFileTest {

    @Test
    fun commaSeparatedRowsBecomeAGrid() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), parseCsv("a,b\nc,d"))
    }

    @Test
    fun aQuotedFieldKeepsItsComma() {
        assertEquals(listOf(listOf("Bench Press, Close Grip", "3")), parseCsv("\"Bench Press, Close Grip\",3"))
    }

    @Test
    fun doubledQuotesInsideAQuotedFieldBecomeOne() {
        assertEquals(listOf(listOf("say \"hi\"", "1")), parseCsv("\"say \"\"hi\"\"\",1"))
    }

    @Test
    fun aQuotedFieldKeepsItsNewline() {
        assertEquals(listOf(listOf("line1\nline2", "2")), parseCsv("\"line1\nline2\",2"))
    }

    @Test
    fun aByteOrderMarkIsDropped() {
        assertEquals(listOf(listOf("a", "b")), parseCsv("\uFEFFa,b"))
    }

    @Test
    fun crlfSplitsRowsTheSameWay() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), parseCsv("a,b\r\nc,d"))
    }

    @Test
    fun aLoneCarriageReturnSplitsRows() {
        assertEquals(listOf(listOf("a"), listOf("b")), parseCsv("a\rb"))
    }

    @Test
    fun blankRowsAreDropped() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), parseCsv("a,b\n\n,\nc,d"))
    }

    @Test
    fun aTrailingNewlineDoesNotAddAnEmptyRow() {
        assertEquals(listOf(listOf("a", "b")), parseCsv("a,b\n"))
    }

    @Test
    fun emptyFieldsSurvive() {
        assertEquals(listOf(listOf("a", "", "c")), parseCsv("a,,c"))
    }

    @Test
    fun nothingIsNoRows() {
        assertEquals(emptyList<List<String>>(), parseCsv(""))
    }

    @Test
    fun aCsvIsOneSheetNamedAfterTheFile() {
        val sheets = readCoachSheets("settimana.csv", "Giorno,Esercizio\n1,Strappo".toByteArray())
        assertEquals(1, sheets.size)
        assertEquals("settimana", sheets[0].name)
        assertEquals(listOf(listOf("Giorno", "Esercizio"), listOf("1", "Strappo")), sheets[0].grid)
    }

    @Test
    fun theExtensionIsNotCaseSensitive() {
        assertEquals("SETTIMANA", readCoachSheets("SETTIMANA.CSV", "a,b".toByteArray())[0].name)
    }

    @Test
    fun aFileThatIsNotACsvGoesToTheWorkbookReader() {
        assertThrows(NotAWorkbookException::class.java) { readCoachSheets("week.txt", "a,b".toByteArray()) }
        assertThrows(NotAWorkbookException::class.java) { readCoachSheets("week", "a,b".toByteArray()) }
    }

    @Test
    fun anEmptyCsvIsOneEmptySheet() {
        // The caller is what drops a sheet with nothing in it; the reader does not invent one.
        assertEquals(0, readCoachSheets("week.csv", "\n".toByteArray())[0].grid.size)
    }
}

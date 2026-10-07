package olygym.app.lib

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/*
 * The spec of frontend/src/lib/xlsx.test.js, one case each, in the same order. The web builds its
 * fixtures as real zips by hand; here ZipOutputStream writes them, stored or deflated, so the reader
 * meets both compression methods Excel writes without a binary checked in.
 */

private fun bytes(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

private val WORKBOOK = """<?xml version="1.0"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Log" sheetId="1" r:id="rId1"/>
    <sheet name="Notes" sheetId="2" r:id="rId2"/>
    <sheet name="Empty" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

// rId2 spells its target out with the leading /xl/ some writers use; the others are relative.
private val RELS = """<?xml version="1.0"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="t" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="t" Target="/xl/worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="t" Target="worksheets/sheet3.xml"/>
</Relationships>"""

// 0: one <t> holding a non-breaking space and a newline. 1: two <r> runs. 2: plain.
private val SHARED = """<?xml version="1.0"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
  <si><t xml:space="preserve">Barbell&#160;
 Bench</t></si>
  <si><r><rPr><b/></rPr><t>Leg</t></r><r><t xml:space="preserve">  Press </t></r></si>
  <si><t>Tail</t></si>
</sst>"""

private val SHEET_HEAD = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">"""

// Row 2 leaves B2 out; row 3 jumps to P3; the fourth row has no r at all (A4, continuing
// from row 3); row 16 sits alone, so everything between is padding.
private val SHEET1 = SHEET_HEAD + """<sheetData>
  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
  <row r="2"><c r="A2" t="inlineStr"><is><t>  Inline
 name </t></is></c><c r="C2"><v>4.0</v></c></row>
  <row r="3"><c r="A3" t="b"><v>1</v></c><c r="P3"><v>1.5</v></c></row>
  <row><c><v>7</v></c></row>
  <row r="16"><c r="P16" t="s"><v>2</v></c></row>
</sheetData></worksheet>"""

private val SHEET2 = SHEET_HEAD + """<sheetData><row r="1"><c r="B1" t="str"><v>cached</v></c></row></sheetData></worksheet>"""
private val SHEET3 = SHEET_HEAD + """<sheetData/></worksheet>"""

private val FILES = listOf(
    "xl/workbook.xml" to WORKBOOK,
    "xl/_rels/workbook.xml.rels" to RELS,
    "xl/sharedStrings.xml" to SHARED,
    "xl/worksheets/sheet1.xml" to SHEET1,
    "xl/worksheets/sheet2.xml" to SHEET2,
    "xl/worksheets/sheet3.xml" to SHEET3,
)

/** files -> a complete archive. Stored when 'deflated' is false. */
private fun zip(files: List<Pair<String, String>>, deflated: Boolean): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { archive ->
        archive.setMethod(if (deflated) ZipOutputStream.DEFLATED else ZipOutputStream.STORED)
        for ((name, xml) in files) {
            val raw = bytes(xml)
            val entry = ZipEntry(name)
            if (!deflated) {
                // A STORED entry must carry its own size and CRC; the default entry has neither.
                entry.size = raw.size.toLong()
                entry.compressedSize = raw.size.toLong()
                entry.crc = CRC32().apply { update(raw) }.value
            }
            archive.putNextEntry(entry)
            archive.write(raw)
            archive.closeEntry()
        }
    }
    return out.toByteArray()
}

private val stored = zip(FILES, false)
private val deflated = zip(FILES, true)

private const val WIDTH = 16   // column P is index 15, so every row is 16 cells wide

private fun row(cells: Map<Int, String>): List<String> = List(WIDTH) { cells[it] ?: "" }

class XlsxTest {

    @Test
    fun `reads the sheet names in workbook order`() {
        val sheets = readXlsx(stored).sheets
        assertEquals(listOf("Log", "Notes", "Empty"), sheets.map { it.name })
    }

    @Test
    fun `reads a deflated archive the same as a stored one`() {
        assertEquals(readXlsx(deflated), readXlsx(stored))
    }

    @Test
    fun `joins every t run of a shared string and collapses whitespace`() {
        val sheets = readXlsx(stored).sheets
        assertEquals("Barbell Bench", sheets[0].grid[0][0])   // nbsp + newline -> one space
        assertEquals("Leg Press", sheets[0].grid[0][1])       // two rich-text runs
    }

    @Test
    fun `reads an inline string from the cell itself`() {
        val sheets = readXlsx(stored).sheets
        assertEquals("Inline name", sheets[0].grid[1][0])
        assertEquals("cached", sheets[1].grid[0][1])          // t="str" cached formula result
    }

    @Test
    fun `returns numbers verbatim and booleans as TRUE and FALSE`() {
        val sheets = readXlsx(stored).sheets
        assertEquals("4.0", sheets[0].grid[1][2])
        assertEquals("1.5", sheets[0].grid[2][15])
        assertEquals("TRUE", sheets[0].grid[2][0])
        assertEquals("7", sheets[0].grid[3][0])
    }

    @Test
    fun `maps cell references and pads sparse rows to the widest one`() {
        val grid = readXlsx(stored).sheets[0].grid
        assertEquals(WIDTH, grid.size)
        assertEquals(row(mapOf(0 to "Barbell Bench", 1 to "Leg Press")), grid[0])
        assertEquals(row(mapOf(0 to "Inline name", 2 to "4.0")), grid[1])
        assertEquals(row(mapOf(0 to "TRUE", 15 to "1.5")), grid[2])
        assertEquals(row(mapOf(0 to "7")), grid[3])           // no r: the row continues, column A
        assertEquals(row(emptyMap()), grid[7])                // never written: a blank row
        assertEquals(row(mapOf(15 to "Tail")), grid[15])      // P16: two-letter column
        assertTrue(grid.all { it.size == WIDTH })
    }

    @Test
    fun `gives an empty sheet an empty grid`() {
        assertEquals(emptyList<List<String>>(), readXlsx(stored).sheets[2].grid)
    }

    @Test
    fun `reads the same archive twice`() {
        // The web hands readXlsx a Uint8Array once and an ArrayBuffer once to prove both are
        // accepted; Kotlin has a single ByteArray, so the case folds into reading it twice.
        assertEquals(3, readXlsx(stored).sheets.size)
        assertEquals(3, readXlsx(stored).sheets.size)
    }

    @Test
    fun `rejects a buffer that is not a zip`() {
        val e = assertThrows(NotAWorkbookException::class.java) { readXlsx(bytes("this is a csv, honestly")) }
        assertTrue(e.message?.contains("not an .xlsx workbook") == true)
    }

    @Test
    fun `rejects a zip without the workbook part`() {
        // The part the reader needs is xl/workbook.xml; a zip that lacks it is rejected.
        val archive = zip(listOf("readme.txt" to "nothing to see"), false)
        val e = assertThrows(NotAWorkbookException::class.java) { readXlsx(archive) }
        assertTrue(e.message?.contains("not an .xlsx workbook") == true)
    }
}

package olygym.app.lib

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/*
 * Read .xlsx workbooks without a spreadsheet dependency. A port of frontend/src/lib/xlsx.js.
 *
 * An .xlsx is a zip of XML parts, so there is no library to justify: the platform owns every hard
 * piece. The web walks the zip directory by hand, inflates with DecompressionStream and parses with
 * DOMParser; here ZipInputStream does the first two and the JVM's own DOM parser does the third,
 * which is the same read with less of it written down. Only the parts that matter are looked at --
 * workbook.xml for the sheet order, its rels for where each sheet lives, sharedStrings.xml for text,
 * one part per sheet -- and inside a sheet only the cell rectangles.
 *
 * Values come back as the strings the file holds, unrounded and unreformatted: the importer shows
 * them back to whoever wrote the file, and "4.0" becoming 4 changes what they see.
 */

/** The file is not a workbook: not a zip at all, or a zip with no xl/workbook.xml in it. */
class NotAWorkbookException : RuntimeException("this file is not an .xlsx workbook")

/** One sheet: its name, and a rectangle of strings as the file holds them. */
data class XlsxSheet(val name: String, val grid: List<List<String>>)

data class XlsxWorkbook(val sheets: List<XlsxSheet>)

/** name -> bytes for every entry in the archive. */
private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val parts = LinkedHashMap<String, ByteArray>()
    try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                parts[entry.name] = zip.readBytes()
            }
        }
    } catch (e: IOException) {
        // A file that is not a zip at all ends here rather than in a half-built map.
        throw NotAWorkbookException()
    }
    return parts
}

private fun parseXml(bytes: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        // The reads are by local name (getElementsByTagName), exactly as the web's DOMParser is
        // used, so namespaces are not resolved.
        isNamespaceAware = false
        isCoalescing = true
        isExpandEntityReferences = false
        // A workbook from a coach is not a document that may declare entities or fetch anything.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}

private fun element(node: org.w3c.dom.Node?): Element? = node as? Element

// A shared string or an inline string may be split into any number of <t> runs (inside <r> for rich
// text); the value is all of them joined.
private fun textsOf(el: Element): String {
    val runs = el.getElementsByTagName("t")
    if (runs.length == 0) return el.textContent ?: ""
    val out = StringBuilder()
    for (i in 0 until runs.length) out.append(runs.item(i).textContent)
    return out.toString()
}

// Anything that reads as blank to the eye is blank here: runs of whitespace -- newlines and
// non-breaking spaces from a formatted sheet included -- become one space, then the trim.
private val WHITESPACE = Regex("""[\s\u00a0]+""")

private fun clean(value: String?): String = WHITESPACE.replace(value ?: "", " ").trim()

// Excel letters are base-26 with A=1: A -> 0, Z -> 25, AA -> 26, P -> 15.
private fun colIndex(letters: String): Int {
    var n = 0
    for (ch in letters.uppercase()) n = n * 26 + (ch.code - 64)
    return n - 1
}

private val COLUMN_LETTERS = Regex("""^([A-Za-z]+)""")

private fun relId(el: Element): String? {
    val attrs = el.attributes
    for (i in 0 until attrs.length) {
        val name = attrs.item(i).nodeName
        if (name.endsWith(":id")) return attrs.item(i).nodeValue
    }
    return null
}

private fun cellValue(cell: Element, shared: List<String>): String {
    val type = cell.getAttribute("t")
    if (type == "inlineStr") return clean(textsOf(element(cell.getElementsByTagName("is").item(0)) ?: cell))
    val v = element(cell.getElementsByTagName("v").item(0))
    val raw = v?.textContent ?: ""
    if (raw.isEmpty()) return ""
    if (type == "s") return clean(shared.getOrNull(raw.toIntOrNull() ?: -1) ?: "")
    if (type == "b") return if (raw == "1") "TRUE" else "FALSE"
    return clean(raw)   // t="str" (a formula's cached string) and plain numbers, verbatim
}

private fun gridOf(doc: Document, shared: List<String>): List<List<String>> {
    val rowEls = doc.getElementsByTagName("row")
    val grid = HashMap<Int, Array<String?>>()
    var width = 0
    var prevRow = -1
    for (i in 0 until rowEls.length) {
        val rowEl = element(rowEls.item(i)) ?: continue
        val ref = rowEl.getAttribute("r")
        val ri = if (ref.isNotEmpty()) (ref.toIntOrNull() ?: 0) - 1 else prevRow + 1
        prevRow = ri
        var row = grid[ri] ?: arrayOfNulls<String>(0).also { grid[ri] = it }
        val cellEls = rowEl.getElementsByTagName("c")
        var prevCol = -1
        for (j in 0 until cellEls.length) {
            val cellEl = element(cellEls.item(j)) ?: continue
            val cref = cellEl.getAttribute("r")
            val letters = COLUMN_LETTERS.find(cref)
            val ci = if (letters != null) colIndex(letters.groupValues[1]) else prevCol + 1
            prevCol = ci
            if (ci >= row.size) {
                row = row.copyOf(ci + 1)
                grid[ri] = row
            }
            row[ci] = cellValue(cellEl, shared)
            if (ci + 1 > width) width = ci + 1
        }
    }
    // Rows are stored sparse, keyed by their own reference. Hand back a rectangle: every row the
    // same width, holes and a missing tail filled with blanks.
    if (width == 0) return emptyList()
    val last = grid.keys.maxOrNull() ?: return emptyList()
    val out = ArrayList<List<String>>(last + 1)
    for (i in 0..last) {
        val row = grid[i]
        out.add(List(width) { k -> row?.getOrNull(k) ?: "" })
    }
    return out
}

/** Read an .xlsx workbook: sheet names in workbook order, each with a rectangular string grid. */
fun readXlsx(bytes: ByteArray): XlsxWorkbook {
    val parts = unzip(bytes)
    val workbook = parseXml(parts["xl/workbook.xml"] ?: throw NotAWorkbookException())

    val rels = HashMap<String, String>()
    parts["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
        val relEls = parseXml(relBytes).getElementsByTagName("Relationship")
        for (i in 0 until relEls.length) {
            val el = element(relEls.item(i)) ?: continue
            // Targets are relative to xl/, but some writers spell that out with a leading "/xl/".
            val target = el.getAttribute("Target").replace(Regex("""^/+"""), "")
            if (target.isNotEmpty()) {
                rels[el.getAttribute("Id")] = if (target.startsWith("xl/")) target else "xl/" + target
            }
        }
    }

    val shared = ArrayList<String>()
    parts["xl/sharedStrings.xml"]?.let { sharedBytes ->
        val siEls = parseXml(sharedBytes).getElementsByTagName("si")
        for (i in 0 until siEls.length) shared.add(textsOf(element(siEls.item(i)) ?: continue))
    }

    val sheets = ArrayList<XlsxSheet>()
    val sheetEls = workbook.getElementsByTagName("sheet")
    for (i in 0 until sheetEls.length) {
        val el = element(sheetEls.item(i)) ?: continue
        val path = rels[relId(el)]
        val xml = path?.let { parts[it] }
        if (xml != null) sheets.add(XlsxSheet(el.getAttribute("name"), gridOf(parseXml(xml), shared)))
    }
    return XlsxWorkbook(sheets)
}

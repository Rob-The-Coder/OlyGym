package olygym.app.lib

/*
 * Read a coach's file, whatever it is called.
 *
 * The web dispatches on the file's name in sheets.jsx: a .csv goes through csv.js, anything else
 * through the workbook reader. The same split lives here, so a CSV picked out of Downloads, off a
 * USB stick or out of Drive lands on the same review sheet as an .xlsx.
 */

/**
 * A real CSV reader: quoted fields, embedded commas and newlines, doubled quotes, BOM and CRLF.
 *
 * The web ships this in csv.js with no test. Splitting on commas breaks on the first exercise named
 * "Bench Press, Close Grip" -- and a whole coach's sheet would import shifted by one column without
 * ever erroring.
 */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    // The byte-order mark Excel writes is not part of the first cell.
    val s = text.removePrefix("\uFEFF")
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (quoted) {
            if (c == '"') {
                if (i + 1 < s.length && s[i + 1] == '"') {
                    field.append('"')
                    i++
                } else {
                    quoted = false
                }
            } else {
                field.append(c)
            }
        } else when (c) {
            '"' -> quoted = true
            ',' -> {
                row.add(field.toString())
                field.clear()
            }
            '\n', '\r' -> {
                if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                row.add(field.toString())
                field.clear()
                // A row of empty cells is a blank line, not a row.
                if (row.any { it.isNotEmpty() }) rows.add(row)
                row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    row.add(field.toString())
    if (row.any { it.isNotEmpty() }) rows.add(row)
    return rows
}

/**
 * The sheets a picked coach's file holds: a .csv is one sheet named after the file, and anything
 * else goes through the workbook reader, which throws NotAWorkbookException for a file that is
 * neither. A sheet with no training in it is the caller's business, not this function's.
 */
fun readCoachSheets(name: String, bytes: ByteArray): List<XlsxSheet> =
    if (extensionOf(name) == "csv") {
        listOf(XlsxSheet(baseNameOf(name), parseCsv(bytes.decodeToString())))
    } else {
        readXlsx(bytes).sheets
    }

/** The extension, lowercased, or "" when the name carries none. */
private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

/** The name without its trailing `.ext`, the way sheets.jsx strips one (`/\.[a-z]+$/i`). */
private fun baseNameOf(name: String): String {
    val dot = name.lastIndexOf('.')
    val ext = if (dot >= 0) name.substring(dot + 1) else ""
    return if (ext.isNotEmpty() && ext.all { it in 'a'..'z' || it in 'A'..'Z' }) name.substring(0, dot) else name
}

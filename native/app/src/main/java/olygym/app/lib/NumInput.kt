package olygym.app.lib

/**
 * What a typed number means, and what the field should show while it is being typed.
 *
 * A port of the NumberField commit rule in frontend/src/components/ui.jsx, which exists because
 * the decimal keypad in many locales offers a comma and reports an empty string for it:
 *
 *   - a comma is a decimal point,
 *   - anything that is not a digit or a point is dropped,
 *   - only the first point survives when the field is a decimal one, and a point ends the number
 *     when it is not,
 *   - an empty string (or a lone point) is null for a nullable field and 0 otherwise, so "nothing
 *     logged" and "logged zero" stay different things (RIR 0 is a set taken to failure),
 *   - the result is never negative.
 *
 * The draft is returned as well: it is what the field keeps on screen while it has focus, so a
 * half-typed "62." is not rewritten under the finger.
 */
data class NumInput(val draft: String, val value: Double?)

fun parseNumInput(raw: String, decimal: Boolean = true, nullable: Boolean = false): NumInput {
    var s = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val i = s.indexOf('.')
    if (i != -1) {
        s = if (decimal) {
            s.substring(0, i + 1) + s.substring(i + 1).replace(".", "")
        } else {
            s.substring(0, i)
        }
    }
    val value = if (s.isEmpty() || s == ".") {
        if (nullable) null else 0.0
    } else {
        maxOf(0.0, s.toDoubleOrNull() ?: 0.0)
    }
    return NumInput(s, value)
}

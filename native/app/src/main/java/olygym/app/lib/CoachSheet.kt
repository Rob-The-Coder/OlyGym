package olygym.app.lib

/*
 * Read the coach's weekly sheet. A port of frontend/src/lib/coach-sheet.js.
 *
 * One sheet is one week, and inside a sheet the shape is fixed: column A carries the day marker
 * ("Giorno 1"), B the exercise, G the reps, H the sets, I the load or a note, J a cue and P the
 * coach's own comment. The same column A also carries the labels of the primer and of the legend
 * block at the bottom of the sheet -- "Jerk Primer:", "Snatch primer (sequenza che fai di solito)",
 * "Plio work" -- which are instructions, not exercises.
 *
 * Two traps the real workbook sets, both handled here rather than by the caller:
 *   * the day marker and the day's first entry share a row (row 3 is "Giorno 1" in A *and*
 *     "Pogo jump" in B), so a marker row still gets read for an exercise;
 *   * the sets column holds whatever landed there: "4.0" is four sets, and one week has a stray
 *     date serial (46115) that must not become a 46115-set exercise. Anything outside 1..20 reads
 *     as "no sets given" and the importer says so.
 *
 * Nothing here decides what an exercise *is*: this file says which rows are exercises and hands
 * back what they say, verbatim. PlanAliases.kt is what turns a name into a catalogue entry.
 */

/** A, B, G, H, I, J, P as 0-based column indexes. */
data class CoachColumns(
    val day: Int,
    val name: Int,
    val reps: Int,
    val sets: Int,
    val load: Int,
    val cue: Int,
    val comment: Int,
)

val COACH_COLUMNS = CoachColumns(day = 0, name = 1, reps = 6, sets = 7, load = 8, cue = 9, comment = 15)

private val DAY_MARKER = Regex("""^giorno\s*(\d+)""", RegexOption.IGNORE_CASE)
private val LABEL = Regex("""^(due serie|esercizi fisio|jerk primer|plio work|snatch primer)""", RegexOption.IGNORE_CASE)
private const val MAX_SETS = 20

/** One exercise row, with the coach's own words. `row` is 1-based, so it points at the sheet line. */
data class CoachEntry(
    val row: Int,
    val name: String,
    val reps: String,
    val sets: Int?,
    val load: String,
    val cue: String,
    val comment: String,
)

data class CoachDay(val n: Int, val entries: List<CoachEntry>)

data class CoachSkipped(val row: Int, val text: String)

data class CoachSheetRead(val days: List<CoachDay>, val skipped: List<CoachSkipped>)

/** The complexes the coach writes with "+": "Strappo + strappo sosp alta" is two exercises. */
fun splitComplex(name: String?): List<String> =
    (name ?: "").split("+").map { it.trim() }.filter { it.isNotEmpty() }

/** 4, "4.0" and " 4 " are four sets. "46115.0" and "" are no sets at all. */
fun setsOf(text: String?): Int? {
    val value = (text ?: "").trim().toDoubleOrNull() ?: return null
    if (!value.isFinite()) return null
    val n = Math.round(value)
    return if (n >= 1 && n <= MAX_SETS) n.toInt() else null
}

private class DayBuilder(val n: Int) {
    val entries = mutableListOf<CoachEntry>()
}

/**
 * Split a sheet grid into its days and their entries.
 *
 * A sheet whose day has no entries keeps the day (the review screen shows three days); a row before
 * any "Giorno" marker belongs to day 1 rather than being dropped.
 */
fun readCoachSheet(grid: List<List<String>>): CoachSheetRead {
    val days = mutableListOf<DayBuilder>()
    val skipped = mutableListOf<CoachSkipped>()
    var day: DayBuilder? = null
    var pastLegend = false

    grid.forEachIndexed { i, row ->
        fun cell(k: Int): String = row.getOrNull(k)?.trim() ?: ""
        val at = cell(COACH_COLUMNS.day)
        val name = cell(COACH_COLUMNS.name)

        val marker = DAY_MARKER.find(at)
        if (marker != null) {
            // A day marker seen twice (a program split over two blocks in one sheet) continues the
            // same day instead of shadowing it with an empty second one.
            val n = marker.groupValues[1].toIntOrNull() ?: 0
            val existing = days.firstOrNull { it.n == n }
            day = existing ?: DayBuilder(n).also { days.add(it) }
            pastLegend = false
        } else if (at.isNotEmpty()) {
            // "Jerk Primer:" and the four labels under it. Everything from here down is reference
            // material: a row's worth of B text below it is a legend, not the day's next exercise.
            pastLegend = true
            skipped.add(CoachSkipped(i + 1, at))
        }

        if (name.isEmpty()) return@forEachIndexed
        if (pastLegend || LABEL.containsMatchIn(name)) {
            skipped.add(CoachSkipped(i + 1, name))
            return@forEachIndexed
        }
        val current = day ?: DayBuilder(1).also { days.add(it) }
        day = current

        current.entries.add(
            CoachEntry(
                row = i + 1,
                name = name,
                reps = cell(COACH_COLUMNS.reps),
                sets = setsOf(cell(COACH_COLUMNS.sets)),
                load = cell(COACH_COLUMNS.load),
                cue = cell(COACH_COLUMNS.cue),
                comment = cell(COACH_COLUMNS.comment),
            ),
        )
    }
    return CoachSheetRead(days.map { CoachDay(it.n, it.entries) }, skipped)
}

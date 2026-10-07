package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import olygym.app.data.str
import olygym.app.data.with

/*
 * Turn one week of the coach's sheet into a week of the dated plan. A port of
 * frontend/src/lib/import-plan.js.
 *
 * Two steps, kept apart on purpose. reviewWeek reads a sheet and proposes an exercise, a rep scheme,
 * a load and a note for every row -- nothing is written, and the review screen renders exactly this.
 * bundleFromWeek turns the reviewed week into a week of S.weeks: each of the coach's days becomes a
 * day on Monday/Wednesday/Friday, a custom exercise the coach asked for is created once, and the
 * weights are converted to whatever unit the account is in. The screen merges it straight into state
 * with mergeWeek -- the review is already local, so nothing is re-serialised through a plan file.
 *
 * What the sheet says is kept where the catalogue has no word for it. A load written as a sentence
 * ("Trova peso congruo", "se ok ultime due a 75kg") becomes a note, and only a load the text *starts*
 * with becomes a weight: "poi togli 10kg" is an instruction, not a 10 kg bar, and a wrong number
 * nobody reads twice is worse than a note that says what the coach actually wrote.
 */

const val DEFAULT_SETS = 3
const val DEFAULT_REPS = 10

// Monday, Wednesday, Friday first -- where a three-day program normally goes -- then the other
// weekdays in order (see migrate-weeks.js's weekDayForPosition).
val WEEK_PLAN = listOf(1, 3, 5, 2, 4, 6, 0)

/** One set scheme, as the coach's reps column describes it. */
data class Scheme(
    val reps: Int? = null,
    val mode: String? = null,
    val sec: Int? = null,
    val side: Boolean = false,
    val amrap: Boolean = false,
    val rmax: Boolean = false,
)

/** The schemes for one row, plus the coach's own words when they did not line up. */
data class SchemePlan(val schemes: List<Scheme?>, val note: String)

/** One proposed exercise: the coach's own row, and what it was read as. */
data class ReviewEntry(
    val key: String,
    val row: Int,
    val raw: String,
    val part: String,
    val tier: Int,
    val exact: Boolean,
    val id: String,
    val name: String,
    val bp: String?,
    val custom: Boolean,
    val sets: Int,
    val reps: Int?,
    val mode: String?,
    val sec: Int?,
    val side: Boolean,
    val weight: Double?,
    val sg: String?,
    val note: String,
    val warns: List<String>,
)

data class ReviewDay(val n: Int, val name: String, val entries: List<ReviewEntry>)

data class ReviewStats(val rows: Int, val exercises: Int, val custom: Int, val fuzzy: Int, val warned: Int)

data class WeekReview(
    val name: String,
    val label: String,
    val days: List<ReviewDay>,
    val skipped: List<CoachSkipped>,
    val stats: ReviewStats,
)

private val SETTIMANA = Regex("""^settimana\s+""", RegexOption.IGNORE_CASE)
private val TRAILING_YEAR = Regex("""\s+\d{4}\s*\z""")

/** "Settimana 23-29 marzo 2026" is a week, not a routine name: the day and the dates are enough. */
fun sheetLabel(name: String?): String =
    (name ?: "")
        .replace(SETTIMANA, "")
        .replace(TRAILING_YEAR, "")
        .trim()

// "50kg, se ok ultime due a 75kg" is a 50 kg bar with an instruction; "poi togli 10kg" is only an
// instruction, and so is "1RM". A number only counts as a load when the text opens with it and it is
// not the "1" of "1RM" or the "5" of "5 minuti".
private val LOAD_KG = Regex(
    """^\s*(?:max\s*)?(\d+(?:[.,]\d+)?)(?:\s*-\s*\d+(?:[.,]\d+)?)?\s*(?:kg|k)\b""",
    RegexOption.IGNORE_CASE,
)
private val LOAD_PLAIN = Regex(
    """^\s*(?:max\s*)?(\d+)(?:\s*-\s*\d+)?(?=[,.\s]|\z)""",
    RegexOption.IGNORE_CASE,
)
private val NOT_LOAD = Regex(
    """^\s*(?:max\s*)?\d+(?:[.,]\d+)?\s*(?:rm\b|rep|serie|minut|secondi|per lato)""",
    RegexOption.IGNORE_CASE,
)

/**
 * The load the text starts with, or null when it opens with something else.
 *
 * The comma is a decimal separator only in front of a unit: "2,5kg" is two and a half kilos, while
 * the coach's "90,95 95" is the three loads of the day, and only the first one is the set's weight.
 */
fun loadWeight(text: String?): Double? {
    val s = (text ?: "").trim()
    if (s.isEmpty() || NOT_LOAD.containsMatchIn(s)) return null
    val match = LOAD_KG.find(s) ?: LOAD_PLAIN.find(s) ?: return null
    val n = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    return if (n.isFinite() && n > 0.0) n else null
}

/** One set scheme: "4", "4.0", "30 secondi", "1 minuto", "10 per lato", "Amrap", "Trova 5RM". */
private val NUMBER = Regex("""(\d+(?:[.,]\d+)?)""")
private val AMRAP = Regex("""^amrap""", RegexOption.IGNORE_CASE)
private val SEARCH_RM = Regex("""(?:trova|cerca)\s*\d+\s*rm""", RegexOption.IGNORE_CASE)
private val SECOND = Regex("""second""", RegexOption.IGNORE_CASE)
private val MINUTE = Regex("""minut|\bmin\b""", RegexOption.IGNORE_CASE)
private val PER_LATO = Regex("""per lato""", RegexOption.IGNORE_CASE)
private val PLAIN_NUMBER = Regex("""^\d+(?:[.,]\d+)?\z""")
private val REPS = Regex("""rep""", RegexOption.IGNORE_CASE)

private fun schemeOf(token: String?): Scheme? {
    val s = (token ?: "").trim()
    if (s.isEmpty()) return null
    if (AMRAP.containsMatchIn(s)) return Scheme(reps = DEFAULT_REPS, amrap = true)
    if (SEARCH_RM.containsMatchIn(s)) {
        val n = NUMBER.find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
        return Scheme(reps = n ?: DEFAULT_REPS, rmax = true)
    }
    if (SECOND.containsMatchIn(s)) {
        val value = NUMBER.find(s)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return Scheme(mode = "time", sec = Math.round(value).toInt())
    }
    if (MINUTE.containsMatchIn(s)) {
        val value = NUMBER.find(s)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return Scheme(mode = "time", sec = Math.round(value * 60).toInt())
    }
    if (PER_LATO.containsMatchIn(s)) {
        val value = NUMBER.find(s)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return Scheme(reps = Math.round(value).toInt(), side = true)
    }
    if (PLAIN_NUMBER.matches(s)) {
        return Scheme(reps = Math.round(s.replace(',', '.').toDouble()).toInt())
    }
    if (REPS.containsMatchIn(s)) {
        val value = NUMBER.find(s)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return Scheme(reps = Math.round(value).toInt())
    }
    return null
}

/**
 * One scheme per exercise in the row. A complex writes one number per component ("1+2+1+1"), but the
 * coach also writes "1+1" for a single clean and jerk and "3" for a whole complex, so: one number
 * covers every component, a short list repeats its last, a long one is cut and reported.
 */
fun schemesFor(repsText: String?, count: Int): SchemePlan {
    val tokens = (repsText ?: "").split("+").map { it.trim() }
    val parsed = tokens.map { schemeOf(it) }
    if (count <= 0) return SchemePlan(emptyList(), "")
    // A rep max or an AMRAP is a scheme the plan has no field for: the count is kept (five reps for
    // "Trova 5RM", ten for "Amrap"), but the coach's own words go in the note so the review shows
    // them instead of silently turning "Amrap" into ten reps.
    val flagged = parsed.any { it != null && (it.amrap || it.rmax) }
    val keep = if (flagged) (repsText ?: "").trim() else ""
    if (parsed.size == count) return SchemePlan(parsed, keep)
    if (parsed.size == 1) return SchemePlan(List(count) { parsed[0] }, keep)
    if (parsed.size > count) return SchemePlan(parsed.take(count), (repsText ?: "").trim())
    // "3+1" over four components is three of the first and one of the last.
    val filled = List(count - parsed.size) { parsed[0] } + parsed
    return SchemePlan(filled, (repsText ?: "").trim())
}

private fun joinNote(parts: List<String>): String =
    parts.filter { it.isNotEmpty() }.joinToString(" · ").take(NOTE_MAX)

private val PURE_LOAD = Regex(
    """^\s*(?:max\s*)?\d+(?:[.,]\d+)?\s*(?:kg|k)?\s*\z""",
    RegexOption.IGNORE_CASE,
)

/**
 * Read one week and propose, for every row, an exercise and how to train it.
 *
 * Every entry carries the coach's own row (raw, row) next to what was understood (tier, exact,
 * warns), because this is what the review screen shows and what the user corrects.
 */
fun reviewWeek(sheet: XlsxSheet, aliases: JsonObject? = null): WeekReview {
    val read = readCoachSheet(sheet.grid)
    val label = sheetLabel(sheet.name)
    var rows = 0
    var exercises = 0
    var custom = 0
    var fuzzy = 0
    var warned = 0
    val out = mutableListOf<ReviewDay>()

    read.days.forEach { day ->
        val entries = mutableListOf<ReviewEntry>()
        day.entries.forEach entryLoop@{ row ->
            rows++
            val matches = matchName(row.name, aliases)
            if (matches.items.isEmpty()) return@entryLoop
            val schemePlan = schemesFor(row.reps, matches.items.size)
            val weight = loadWeight(row.load)
            // The load text is worth keeping unless it was nothing but the number already taken.
            val loadNote = if (row.load.isNotEmpty() && !(weight != null && PURE_LOAD.matches(row.load))) {
                row.load
            } else {
                ""
            }
            matches.items.forEachIndexed { i, item ->
                val scheme = schemePlan.schemes.getOrNull(i) ?: Scheme()
                val warns = mutableListOf<String>()
                if (row.sets == null) warns.add("sets")
                if (row.reps.isEmpty()) warns.add("reps")
                if (row.reps.isNotEmpty() && scheme.reps == null && scheme.mode == null) warns.add("reps")
                exercises++
                if (item.tier == 3) custom++
                if (!item.exact) fuzzy++
                if (warns.isNotEmpty()) warned++
                entries.add(
                    ReviewEntry(
                        key = day.n.toString() + ":" + row.row + ":" + i,
                        row = row.row,
                        raw = row.name,
                        part = item.raw,
                        tier = item.tier,
                        exact = item.exact,
                        id = item.id,
                        name = item.name,
                        bp = item.bp,
                        custom = item.tier == 3,
                        sets = row.sets ?: DEFAULT_SETS,
                        reps = if (scheme.reps != null) scheme.reps else if (scheme.mode != null) null else DEFAULT_REPS,
                        mode = scheme.mode,
                        sec = scheme.sec,
                        side = scheme.side,
                        weight = weight,
                        sg = if (matches.items.size > 1) "coach-" + day.n + "-" + row.row else null,
                        note = joinNote(listOf(item.note, schemePlan.note, loadNote, row.cue, row.comment)),
                        warns = warns,
                    ),
                )
            }
        }
        if (entries.isNotEmpty()) {
            val suffix = if (label.isNotEmpty()) " · " + label else ""
            out.add(ReviewDay(day.n, "Giorno " + day.n + suffix, entries))
        }
    }
    return WeekReview(
        name = sheet.name,
        label = label,
        days = out,
        skipped = read.skipped,
        stats = ReviewStats(rows, exercises, custom, fuzzy, warned),
    )
}

/** The bundle's custom exercise for a name, created once however many weeks use it. */
private fun customOf(entry: ReviewEntry, made: MutableMap<String, JsonObject>): String {
    val name = entry.name.trim()
    val key = name.lowercase()
    val existing = made[key]
    if (existing == null) {
        made[key] = js("id" to ("coach-" + made.size), "n" to name, "bp" to entry.bp)
    }
    return made[key]?.str("id").orEmpty()
}

/**
 * The reviewed week as one week of S.weeks -- the coach's days are scheduled Monday/Wednesday/Friday
 * (or the days list given), each carrying the exercise configs a routine would have had.
 *
 * unit   the account's unit; the sheet itself is always kilos, so the weights are converted here
 * days   weekday numbers (0 = Sunday), in order, to put the days on; defaults to WEEK_PLAN
 *
 * The returned week carries customEx -- the user's own exercises it references, consumed and
 * stripped by mergeWeek. The review is already local, so there is no plan file to parse.
 */
fun bundleFromWeek(
    review: WeekReview,
    unit: String = "kg",
    name: String = "",
    days: List<Int>? = null,
): JsonObject {
    val made = LinkedHashMap<String, JsonObject>()
    val dows = if (!days.isNullOrEmpty()) days else WEEK_PLAN
    val out = review.days.mapIndexed { i, day ->
        val ex = day.entries.map { entry ->
            var cfg = js(
                "id" to (if (entry.custom) customOf(entry, made) else entry.id),
                "sets" to entry.sets,
            )
            if (entry.mode == "time") {
                cfg = cfg.with("mode", "time").with("sec", entry.sec ?: 45)
            } else {
                cfg = cfg.with("reps", entry.reps ?: DEFAULT_REPS)
            }
            if (entry.weight != null) cfg = cfg.with("weight", entry.weight)
            if (entry.side) cfg = cfg.with("side", true)
            entry.sg?.let { cfg = cfg.with("sg", it) }
            if (entry.note.isNotEmpty()) cfg = cfg.with("note", entry.note)
            convertedExercise(cfg, "kg", unit)
        }
        js(
            "dow" to dows.getOrElse(i) { WEEK_PLAN[i % WEEK_PLAN.size] },
            "name" to day.name,
            "ex" to ex,
        )
    }
    return js(
        "id" to uid(),
        "startIso" to isoOf(startOfWeek(todayISO(), MONDAY)),
        "name" to (if (name.isNotEmpty()) name else review.label),
        "days" to out,
        "customEx" to made.values.toList(),
    )
}

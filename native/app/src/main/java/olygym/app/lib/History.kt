package olygym.app.lib

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Day
import olygym.app.data.Week
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.toJson
import olygym.app.data.truthy
import olygym.app.data.with
import olygym.app.data.without

/*
 * Reading a logged session back out of the state object — a port of frontend/src/lib/history.js.
 * The session shapes stay JSON (see data/Js.kt); the week lookup needs the typed model and bridges
 * once in weeksOf.
 */

/** JS `value && typeof value === 'object' && !Array.isArray(value) ? value : {}`. */
private fun objectOf(value: JsonElement?): JsonObject = value.asObj() ?: JsonObject(emptyMap())


/** JS `a || b || c`: the first value that is not falsy, or null. */
private fun orTruthy(vararg values: JsonElement?): JsonElement? = values.firstOrNull { truthy(it) }

private fun sgOf(entry: JsonObject?): JsonElement? = entry?.get("sg")?.takeIf { it !is JsonNull }

/** JS `Math.round`, ties toward +infinity. */
private fun jsRound(value: Double): Double = floor(value + 0.5)

/** `String(number)` for the summaries that interpolate one, so 3 prints as "3" and not "3.0". */
private fun numberText(value: Double): String =
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) value.toLong().toString()
    else value.toString()

/** `x || fallback` where x is read as text — the numeric branch of a template literal. */
private fun numberOrText(value: JsonElement?, fallback: String): String {
    if (!truthy(value)) return fallback
    val p = value as? JsonPrimitive ?: return fallback
    if (p.isString) return p.content
    val d = p.doubleOrNull ?: return p.content
    return numberText(d)
}

/** How an exercise is logged (issue #16). */
fun modeOf(cfg: JsonElement?): String =
    if (cfg.asObj()?.str("mode") == "time") "time" else "reps"

fun isTimed(cfg: JsonElement?): Boolean = modeOf(cfg) == "time"

// One flag that rides on top of a mode rather than making a new one (issue #32). It is absent on
// every plan, workout and backup written before it existed, and absent reads as false.
fun isBw(cfg: JsonElement?): Boolean {
    val source = cfg.asObj()
    val flag = source?.get("bodyweight")
    if (flag != null && flag !is JsonNull) return truthy(flag)
    return isBodyweightEq(source?.get("id").asStr())
}

// mm:ss for a work duration — seconds alone read badly past a minute ("90 s" vs "1:30").
fun fmtSec(sec: Double?): String {
    val n = max(0.0, jsRound(if (sec == null || sec.isNaN()) 0.0 else sec))
    val total = n.toLong()
    return (total / 60).toString() + ":" + (total % 60).toString().padStart(2, '0')
}

// How hard a set felt: RIR counts the reps still in the tank, RPE reads the same effort off a
// 10-point scale from the top (RPE 8 is about RIR 2). min..max is the range the stepper walks.
data class EffortScale(val f: String, val hd: String, val step: Double, val min: Double, val max: Double)

val EFFORT: Map<String, EffortScale> = linkedMapOf(
    "rir" to EffortScale("rir", "RIR", 0.5, 0.0, 10.0),
    "rpe" to EffortScale("rpe", "RPE", 0.5, 6.0, 10.0),
)

// One tap of an effort stepper. Empty is not 0 — an unlogged effort must not become "went to
// failure" from one stray tap — so minus on an empty cell leaves it empty, and plus starts at the
// bottom of the scale and walks up. Stepping back off the bottom clears the cell again.
fun stepEffort(kind: String?, cur: Double?, dir: Int): Double? {
    val e = kind?.let { EFFORT[it] } ?: return cur
    if (cur == null) return if (dir < 0) null else e.min
    val n = jsRound((cur + dir * e.step) * 100.0) / 100.0
    if (dir < 0 && n < e.min) return null
    // only the ceiling is enforced on the way up: a value typed below the floor still steps in
    // even increments instead of snapping to the floor.
    return if (dir > 0) min(e.max, n) else max(e.min, n)
}

// A typed effort is capped but not floored — clamping up while someone types "10" would turn the
// first keystroke into the floor and fight the input.
fun capEffort(kind: String?, v: Double?): Double? {
    val e = kind?.let { EFFORT[it] } ?: return v
    if (v == null) return v
    return min(e.max, v)
}

// Which scale a profile logs. showRir is the boolean this replaced and is only consulted when the
// profile has no answer of its own — an explicit 'none' has to win over it.
fun effortOf(S: JsonElement?): String {
    val source = S.asObj()
    val e = source?.get("effort").asStr()
    if (e == "none") return "none"
    if (e != null && EFFORT.containsKey(e)) return e
    return if (source?.bool("showRir") == true) "rir" else "none"
}

// The "(RIR 2)" / "(RPE 8)" tail on a set summary, empty when nothing was logged.
private fun effortTail(s: JsonObject): String {
    val k = when {
        s["rir"].present() -> "rir"
        s["rpe"].present() -> "rpe"
        else -> null
    } ?: return ""
    return " (" + EFFORT.getValue(k).hd + " " + fmtNum(s.num(k) ?: 0.0) + ")"
}

// One-line summary of a logged set. cfg carries the mode when the caller has it; passing an id
// alone keeps the old body-part behaviour.
fun setLabel(id: String?, s: JsonElement?, cfg: JsonElement? = null): String {
    val source = objectOf(s)
    val c = cfg.asObj() ?: js("id" to id)
    var mode = modeOf(c)
    // A set saved by an older build carries no target with it; the set's own fields still say what
    // it was — seconds for a timed set — so those are not read back as "0 reps".
    if (!truthy(cfg) && !((source.num("r") ?: 0.0) > 0.0) && (source.num("sec") ?: 0.0) > 0.0) mode = "time"
    if (mode == "time") {
        val w = source.num("w") ?: 0.0
        return fmtSec(source.num("sec")) + if (w > 0) " \u00b7 " + fmtNum(w) else ""
    }
    val merged = c.with("id", c["id"]?.takeUnless { it is JsonNull } ?: id?.toJson())
    val bw = isBw(merged)
    val reps = numberOrText(source["r"], "0")
    // Bodyweight reads as what you did — "12", or "+10 x 12" once there is a belt involved —
    // rather than "0x12", which says a set was performed with no weight and means nothing.
    val body = if (bw) {
        (if ((source.num("w") ?: 0.0) > 0.0) "+" + fmtNum(source.num("w") ?: 0.0) + " \u00d7 " else "") + reps
    } else {
        fmtNum(source.num("w") ?: 0.0) + "\u00d7" + reps
    }
    return body + effortTail(source)
}

// Default config for a freshly added exercise. bodyweight is written only when true.
fun defaultConfig(id: String?, mode: String? = null): JsonObject {
    val m = if (!mode.isNullOrEmpty()) mode else modeOf(js("id" to id))
    var out = if (m == "time") {
        js("sets" to 3, "sec" to 45, "weight" to 0, "mode" to "time")
    } else {
        js("sets" to 3, "reps" to 10, "weight" to 0, "mode" to "reps")
    }
    if (isBodyweightEq(id)) out = out.with("bodyweight", true)
    return out
}

// One-line summary of a planned exercise ("3 x 10 . 60 kg"), shared by the routine editor and the
// plan export. Added weight reads as added: "+10 kg" on a dip belt, "60 kg" on a barbell.
fun exLine(cfg: JsonElement?, unit: String?): String {
    val c = objectOf(cfg)
    val mode = modeOf(c)
    val n = numberOrText(c["sets"], "1")
    val load =
        if (truthy(c["weight"])) " \u00b7 " + (if (isBw(c)) "+" else "") + fmtNum(c.num("weight") ?: 0.0) + " " + unit
        else ""
    if (mode == "time") {
        val sec = (c.num("sec") ?: 0.0).takeIf { it != 0.0 } ?: 45.0
        return n + " \u00d7 " + fmtSec(sec) + load
    }
    return n + " \u00d7 " + numberOrText(c["reps"], "undefined") + load
}

/** Drop superset ids that no longer have an adjacent partner (after unlink/reorder/remove). */
// The JS mutates ex; JSON is immutable, so this returns the new list and the caller uses it.
fun cleanupSg(ex: JsonArray): JsonArray {
    val out = ex.map { it.asObj()?.let { o -> JsonObject(o) } ?: JsonObject(emptyMap()) }.toMutableList()
    out.forEachIndexed { i, e ->
        val sg = e["sg"]
        if (truthy(sg) && sgOf(out.getOrNull(i - 1)) != sg && sgOf(out.getOrNull(i + 1)) != sg) {
            out[i] = e.without("sg")
        }
    }
    return JsonArray(out)
}

// Return the contiguous run around an entry that shares its superset id. A repeated id in a
// separated part of the list is deliberately not included.
private fun contiguousSgGroup(items: JsonArray, idx: Int): List<Int> {
    val raw = items.getOrNull(idx)?.asObj()?.get("sg")
    if (!truthy(raw)) return listOf(idx)
    var first = idx
    var last = idx
    while (first > 0 && sgOf(items.getOrNull(first - 1)?.asObj()) == raw) first--
    while (last + 1 < items.size && sgOf(items.getOrNull(last + 1)?.asObj()) == raw) last++
    return (first..last).toList()
}

private fun freshSg(items: List<JsonObject>, first: Int, second: Int): String {
    val base = "sg-" + min(first, second) + "-" + max(first, second)
    var sg = base
    var n = 2
    while (items.any { it["sg"] == JsonPrimitive(sg) }) {
        sg = base + "-" + n
        n++
    }
    return sg
}

// Purely pair two adjacent entries. Existing contiguous groups on either side are merged. A caller
// can provide a group id; otherwise an existing id is preferred, with a deterministic unused id for
// two previously ungrouped entries. The JS mutates the copies it returns; this returns the new list.
fun pairAdjacent(items: JsonArray, first: Int, second: Int, groupId: String? = null): JsonArray {
    if (!items.getOrNull(first).present() || !items.getOrNull(second).present()) {
        throw IllegalArgumentException("Superset entry indexes are invalid")
    }
    if (abs(first - second) != 1) throw IllegalArgumentException("Superset entries must be adjacent")
    val next = items.map { it.asObj()?.let { o -> JsonObject(o) } ?: JsonObject(emptyMap()) }.toMutableList()
    val left = min(first, second)
    val right = max(first, second)
    val group = orTruthy(groupId?.let { JsonPrimitive(it) }, next[left]["sg"], next[right]["sg"])
        ?: JsonPrimitive(freshSg(next, left, right))
    val members = (
        contiguousSgGroup(JsonArray(next), left) + contiguousSgGroup(JsonArray(next), right)
        ).toSet()
    members.forEach { next[it] = next[it].with("sg", group) }
    return JsonArray(next)
}

// Remove one entry from its superset and clean any ids that no longer have an adjacent partner.
// The JS mutates the copies it returns; this returns the new list.
fun unpairSuperset(items: JsonArray, idx: Int): JsonArray {
    if (!items.getOrNull(idx).present()) throw IllegalArgumentException("Superset entry index is invalid")
    val next = items.map { it.asObj()?.let { o -> JsonObject(o) } ?: JsonObject(emptyMap()) }.toMutableList()
    next[idx] = next[idx].without("sg")
    return cleanupSg(JsonArray(next))
}

/**
 * Is this completed entry excluded from progression / session read-back?
 *
 * "Excluded" moved from a whole-workout flag to a per-entry one (ENG-11). A legacy workout carries
 * the whole-workout flag and no per-entry field, so every one of its entries reads as excluded.
 */
fun entryExcluded(w: JsonElement?, entry: JsonElement?): Boolean =
    w.asObj()?.bool("excludeFromProgression") == true || entry.asObj()?.bool("noProg") == true

fun lastEntryFor(S: JsonElement?, exId: String?): JsonObject? {
    val workouts = S.asObj()?.arr("workouts") ?: JsonArray(emptyList())
    for (i in workouts.indices.reversed()) {
        val w = workouts[i].asObj() ?: continue
        val en = w.arr("entries").firstOrNull { it.asObj()?.str("id") == exId }?.asObj() ?: continue
        // A session that does not count — a planned deload, or a rehab block merged into a real
        // session — is not "last time" for the next regular prescription.
        if (entryExcluded(w, en)) continue
        // Work sets only: a warm-up answers none of "what did you actually lift last time".
        val done = JsonArray(en.arr("sets").filter { truthy(it.asObj()?.get("done")) && !isWarmupRow(it) })
        if (done.isNotEmpty()) {
            // target is what the session prescribed; older workouts have none.
            return js("d" to w["d"], "sets" to done, "target" to (orTruthy(en["target"]) ?: JsonNull))
        }
    }
    return null
}

/** How long any single note may get. Long enough for a paragraph, short enough to stay a note. */
const val NOTE_MAX = 500

/**
 * The most recent session note the user pinned for this exercise, or null. Only the newest pinned
 * note is returned: a pin is a message to your next self, and a stack of them is noise.
 */
fun pinnedNoteFor(S: JsonElement?, exId: String?): JsonObject? {
    val workouts = S.asObj()?.arr("workouts") ?: JsonArray(emptyList())
    for (i in workouts.indices.reversed()) {
        val w = workouts[i].asObj() ?: continue
        val en = w.arr("entries").firstOrNull { it.asObj()?.str("id") == exId }?.asObj()
        val note = (en?.str("note") ?: "").trim()
        if (note.isNotEmpty() && en?.bool("notePin") == true) return js("note" to note, "d" to w["d"])
    }
    return null
}

/** The standing note for an exercise — the one that is true every session. */
fun exNoteFor(S: JsonElement?, exId: String?): String? {
    val notes = S.asObj()?.obj("exNotes") ?: JsonObject(emptyMap())
    val value = if (exId == null) null else notes[exId].asStr()
    return (value ?: "").trim().ifEmpty { null }
}

// A freestyle exercise starts with the last target the user actually trained, rather than the
// generic config sheet defaults used when there is no history.
fun freestyleConfig(S: JsonElement?, cfg: JsonElement?): JsonObject {
    val c = objectOf(cfg)
    val last = lastEntryFor(S, c.str("id")) ?: return JsonObject(c)
    var out = JsonObject(c)
    last.obj("target")?.forEach { (k, v) -> out = JsonObject(out + (k to v)) }
    out = out.with("id", c["id"])
    out = out.with("sets", max(1, last.arr("sets").size))
    return out
}

fun bestWeightFor(S: JsonElement?, exId: String?): Double {
    var best = 0.0
    val workouts = S.asObj()?.arr("workouts") ?: JsonArray(emptyList())
    for (wi in workouts.indices) {
        val entries = workouts[wi].asObj()?.arr("entries") ?: continue
        for (ei in entries.indices) {
            val e = entries[ei].asObj() ?: continue
            if (e.str("id") != exId) continue
            val entryBest = bestWeightForEntry(e)
            if (entryBest > best) best = entryBest
        }
    }
    return best
}

/**
 * S.weeks as the typed weeks the date helpers read. The session state is JSON (data/Js.kt); the
 * week lookup is the one part that needs the typed model, so it bridges here.
 */
private fun weeksOf(S: JsonElement?): List<Week> =
    (S.asObj()?.arr("weeks") ?: JsonArray(emptyList())).mapNotNull { element ->
        val w = element.asObj() ?: return@mapNotNull null
        Week(
            id = w.str("id") ?: "",
            startIso = w.str("startIso") ?: "",
            name = w.str("name") ?: "",
            days = w.arr("days").mapNotNull { dayEl ->
                val d = dayEl.asObj() ?: return@mapNotNull null
                val dow = d.int("dow") ?: return@mapNotNull null
                Day(dow = dow, name = d.str("name") ?: "", ex = d.arr("ex").mapNotNull { it.asObj() })
            },
        )
    }

/**
 * The day planned for a date — the day object itself, or null for a rest day, a gap between weeks,
 * or a weekday this week leaves out. A thin alias for weeks.js's dayFor, kept here because every
 * session-path reader already imports this module and the two must never drift.
 */
fun effectiveDay(S: JsonElement?, iso: String): Day? = dayFor(weeksOf(S), iso)

/** The object nextTrainingDay returns: the date, its getDay() weekday, and the day itself. */
data class NextTrainingDay(val iso: String, val weekday: Int, val day: Day)

/**
 * The next day that actually has something to train, looking forward from iso (exclusive). Takes a
 * date string rather than reading the clock so callers and tests agree on "today". A day with no
 * exercises does not count: starting one lands you in an empty session.
 */
fun nextTrainingDay(S: JsonElement?, iso: String): NextTrainingDay? {
    for (i in 1..7) {
        val nextIso = addDays(iso, i.toLong())
        val day = dayFor(weeksOf(S), nextIso)
        if (day != null && day.ex.isNotEmpty()) {
            return NextTrainingDay(nextIso, LocalDate.parse(nextIso).dayOfWeek.value % 7, day)
        }
    }
    return null
}

/**
 * Build the rows a planned exercise starts a session with: its work sets, preceded by however many
 * warm-up sets the routine asks for (cfg.warmupSets, 0 by default so an existing plan behaves
 * exactly as before).
 */
fun buildSets(S: JsonElement?, cfg: JsonElement?, options: JsonObject = JsonObject(emptyMap())): JsonArray {
    val rows = buildWorkSets(S, cfg, options)
    val c = objectOf(cfg)
    val warm = max(0, min(MAX_PLANNED_WARMUPS, jsRound(c.num("warmupSets") ?: 0.0).toInt()))
    if (warm == 0) return rows
    val mode = modeOf(c)
    var out = rows
    val step = options.num("step") ?: 2.5
    for (i in 0 until warm) out = insertWarmupRow(out, mode, c, step)
    return out
}

/** Beyond this a "warm-up" is its own workout; the config stepper stops here too. */
const val MAX_PLANNED_WARMUPS = 5

private fun buildWorkSets(S: JsonElement?, cfg: JsonElement?, options: JsonObject): JsonArray {
    val preferLast = options.bool("preferLast") == true
    val useTarget = options.bool("useTarget") == true
    val c = objectOf(cfg)
    // lastEntryFor now skips any entry that does not count, so the rows seed from the last
    // counting session without this function pre-filtering the history itself.
    val last = lastEntryFor(S, c.str("id"))
    val n = max(1.0, (c.num("sets") ?: 0.0).takeIf { it != 0.0 } ?: 1.0)
    val mode = modeOf(c)
    val sets = mutableListOf<JsonObject>()
    val lastSets = last?.arr("sets") ?: JsonArray(emptyList())
    fun prevAt(i: Int): JsonObject? =
        if (!useTarget && last != null) (lastSets.getOrNull(i) ?: lastSets.lastOrNull())?.asObj() else null

    var i = 0
    while (i.toDouble() < n) {
        if (mode == "time") {
            // Only carry a previous value over when it came from a timed set — switching an
            // exercise from reps to time must not seed the duration from a rep count.
            val prev = prevAt(i)
            val carried = if (prev != null && (prev.num("sec") ?: 0.0) > 0.0) prev else null
            val sec = carried?.num("sec") ?: ((c.num("sec") ?: 0.0).takeIf { it != 0.0 } ?: 45.0)
            val w = carried?.let { it.num("w") ?: 0.0 } ?: (c.num("weight") ?: 0.0)
            sets += js("sec" to sec, "w" to w, "done" to false)
        } else {
            val prev = prevAt(i)
            val usable = if (prev != null && (prev.num("r") ?: 0.0) > 0.0) prev else null
            val lastRegular = if (last != null) (lastSets.getOrNull(i) ?: lastSets.lastOrNull())?.asObj() else null
            // A deload uses the routine's target weight; a routine that never set one (weight 0)
            // falls back to the last regular load rather than prescribing an empty bar.
            val w = if (useTarget) {
                if ((c.num("weight") ?: 0.0) > 0.0) c.num("weight") ?: 0.0
                else if (lastRegular != null && (lastRegular.num("r") ?: 0.0) > 0.0) lastRegular.num("w") ?: 0.0
                else c.num("weight") ?: 0.0
            } else if (preferLast && usable != null) {
                usable.num("w") ?: 0.0
            } else {
                val confW = c.str("id")?.let { id -> S.asObj()?.obj("exWeights")?.obj(id) }?.num("w") ?: 0.0
                if (confW > 0.0) confW else (usable?.num("w") ?: (c.num("weight") ?: 0.0))
            }
            val r = if (usable != null) usable.num("r") ?: 0.0 else c.num("reps") ?: 0.0
            sets += js("w" to w, "r" to r, "done" to false)
        }
        i++
    }
    return JsonArray(sets)
}

fun workoutVolume(w: JsonElement?): Double {
    var v = 0.0
    // Completed work rows count load x reps; unchecked rows and warm-ups contribute no volume.
    val entries = w.asObj()?.arr("entries") ?: JsonArray(emptyList())
    entries.forEach { e ->
        e.asObj()?.arr("sets")?.forEach { s -> if (!isWarmupRow(s)) v += completedVolumeOf(s) }
    }
    return v
}

// How many of a row's units are done: a row is one unit, done or not.
fun doneUnits(s: JsonElement?): Int = if (truthy(s.asObj()?.get("done"))) 1 else 0

// Total completion-units across a session's rows.
fun setUnitsTotal(entries: JsonElement?): Int =
    (entries.asArr() ?: JsonArray(emptyList())).sumOf { it.asObj()?.arr("sets")?.size ?: 0 }

fun setsDone(w: JsonElement?): Int {
    var n = 0
    val entries = w.asObj()?.arr("entries") ?: JsonArray(emptyList())
    entries.forEach { e -> e.asObj()?.arr("sets")?.forEach { n += doneUnits(it) } }
    return n
}

fun setsDoneActive(A: JsonElement?): Int {
    var n = 0
    if (A != null && A !is JsonNull) {
        val entries = A.asObj()?.arr("entries") ?: JsonArray(emptyList())
        entries.forEach { e -> e.asObj()?.arr("sets")?.forEach { n += doneUnits(it) } }
    }
    return n
}

fun lastBW(S: JsonElement?): JsonElement? {
    val bw = S.asObj()?.arr("bodyweight") ?: JsonArray(emptyList())
    return if (bw.isNotEmpty()) bw.last() else null
}

// Group consecutive items sharing a superset id (sg) into "units" of indices.
fun supersetUnits(items: JsonElement?): JsonArray {
    val list = items.asArr() ?: JsonArray(emptyList())
    val units = mutableListOf<JsonArray>()
    list.forEachIndexed { i, e ->
        val prev = list.getOrNull(i - 1)?.asObj()
        val sg = sgOf(e.asObj())
        if (i > 0 && truthy(sg) && prev != null && truthy(sgOf(prev)) && sgOf(prev) == sg) {
            units[units.size - 1] = JsonArray(units.last() + JsonPrimitive(i))
        } else {
            units += JsonArray(listOf(JsonPrimitive(i)))
        }
    }
    return JsonArray(units)
}

// Move the selected occurrence's complete display unit by one neighbouring unit. Returning a new
// array keeps this helper pure. Index identity matters because the same exercise id may appear more
// than once with different setup. The JS mutates nothing; this returns the new list.
fun moveSupersetUnit(items: JsonElement?, index: Int, direction: Int): JsonArray? {
    if (items.asArr() == null || (direction != -1 && direction != 1)) return null
    val list = items.asArr()!!
    val units = supersetUnits(list).map { it.asArr()!! }
    val source = units.indexOfFirst { unit -> unit.any { it.asInt() == index } }
    val target = source + direction
    if (source < 0 || target < 0 || target >= units.size) return null
    val reordered = units.toMutableList()
    val selected = reordered[source]
    reordered[source] = reordered[target]
    reordered[target] = selected
    return JsonArray(reordered.flatten().map { list[it.asInt()!!] })
}

fun unitOf(units: JsonElement?, idx: Int): JsonArray =
    (units.asArr() ?: JsonArray(emptyList()))
        .firstOrNull { it.asArr()?.any { i -> i.asInt() == idx } == true }?.asArr()
        ?: JsonArray(listOf(JsonPrimitive(idx)))

fun streakWeeks(S: JsonElement?): Int {
    val workouts = S.asObj()?.arr("workouts") ?: JsonArray(emptyList())
    if (workouts.isEmpty()) return 0
    val ws = weekStartOf(S.asObj()?.int("weekStart"))
    val weeks = workouts.mapNotNull { it.asObj()?.str("d") }.map { weekKey(it, ws) }.toSet()
    var streak = 0
    var cur = LocalDate.now()
    for (i in 0 until 520) {
        val wk = weekKey(isoOf(cur), ws)
        if (weeks.contains(wk)) streak++ else if (i > 0) break
        cur = cur.minusDays(7)
    }
    return streak
}

/**
 * Cascade a weight change forward: following sets of the same warm-up flag that are still undone
 * take the new value (null deletes the key). Done sets are never rewritten.
 * The JS mutates the rows in the returned list; JSON is immutable, so this returns the new list.
 */
fun cascadeWeight(rows: JsonArray, from: Int, value: Any?): JsonArray {
    val warm = isWarmupRow(rows.getOrNull(from))
    val next = rows.toMutableList()
    for (j in (from + 1) until next.size) {
        val row = next[j].asObj() ?: continue
        if (isWarmupRow(next[j]) == warm && !truthy(row["done"])) {
            next[j] = if (value == null) row.without("w") else row.with("w", value)
        }
    }
    return JsonArray(next)
}

/**
 * Recompute the warm-up block so it ramps toward the weight the work rows ACTUALLY carry. A warm-up
 * already logged keeps its weight and becomes what the next one ramps from: it happened, and
 * rewriting performed work is data loss. Returns a new list.
 */
fun rerampWarmups(rows: JsonArray, step: Double = 2.5): JsonArray {
    val firstWork = rows.indexOfFirst { !isWarmupRow(it) }
    if (firstWork <= 0) return rows
    val target = rows[firstWork].asObj()?.num("w") ?: 0.0
    if (!(target > 0.0)) return rows
    val out = rows.toMutableList()
    var from = 0.0
    for (i in 0 until firstWork) {
        val row = out[i].asObj() ?: continue
        if (truthy(row["done"])) {
            from = row.num("w") ?: 0.0
            continue
        }
        val w = if (target > from) {
            max(0.0, min(target, floor((from + (target - from) / 2.0) / step) * step))
        } else {
            target
        }
        out[i] = row.with("w", w)
        from = w
    }
    return JsonArray(out)
}

/**
 * Insert a warm-up row at the end of the warm-up block, ramping toward the working weight. Each
 * added row halves what is left between the last warm-up and the first work set. step is the
 * exercise's own loading step, passed in by the caller so this module keeps no dependency on
 * progression. Returns a new list.
 */
fun insertWarmupRow(rows: JsonArray, mode: String?, target: JsonElement?, step: Double = 2.5): JsonArray {
    val firstWork = rows.indexOfFirst { !isWarmupRow(it) }
    val at = if (firstWork == -1) rows.size else firstWork
    val prev = if (at > 0) rows[at - 1].asObj() else null
    val work = if (firstWork == -1) null else rows[firstWork].asObj()
    val targetObj = target.asObj()
    fun rampTo(to: Double): Double {
        val from = prev?.num("w") ?: 0.0
        // Nothing to ramp toward: bodyweight, a timed hold with no load.
        if (!(to > 0.0)) return 0.0
        // A warm-up is never heavier than the set it warms up for. Rounded DOWN to the step: a
        // warm-up that lands a notch light costs nothing, one that lands a notch heavy is a set you
        // have to strip plates off before you can use it.
        if (to <= from) return to
        return max(0.0, min(to, floor((from + (to - from) / 2.0) / step) * step))
    }
    val warm = if (mode == "time") {
        val secEl: JsonElement? = when {
            prev != null -> prev["sec"]
            work != null -> work["sec"]
            else -> JsonPrimitive((targetObj?.num("sec") ?: 0.0).takeIf { it != 0.0 } ?: 45.0)
        }
        js(
            "sec" to secEl,
            "w" to rampTo(work?.num("w") ?: (targetObj?.num("weight") ?: 0.0)),
            "done" to false, "phase" to "warmup", "warmup" to true,
        )
    } else {
        val rEl: JsonElement? = when {
            work != null -> work["r"]
            prev != null -> prev["r"]
            else -> targetObj?.get("reps")
        }
        js(
            "w" to rampTo(work?.num("w") ?: (targetObj?.num("weight") ?: 0.0)),
            "r" to rEl,
            "done" to false, "phase" to "warmup", "warmup" to true,
        )
    }
    val next = rows.toMutableList()
    next.add(at, warm)
    return JsonArray(next)
}

/** Remove the row at i, never emptying the entry below one row. Returns a new list. */
fun removeRowAt(rows: JsonArray, i: Int): JsonArray {
    if (rows.size <= 1) return JsonArray(rows.toList())
    val next = rows.toMutableList()
    if (i in next.indices) next.removeAt(i)
    return JsonArray(next)
}

/** Completed non-warm-up sets across a workout's entries. */
fun workSetsDone(w: JsonElement?): Int =
    (w?.asObj()?.arr("entries") ?: JsonArray(emptyList())).sumOf { e ->
        e.asObj()?.arr("sets")?.count { truthy(it.asObj()?.get("done")) && !isWarmupRow(it) } ?: 0
    }

private val METRIC_MODES = listOf("reps", "time")

// Completed-state-independent work rows whose authoritative mode matches the requested mode.
private fun workRowsForMode(entry: JsonElement?, mode: String?): JsonArray {
    val source = objectOf(entry)
    val target = objectOf(orTruthy(source["target"], source) ?: source)
    val expectedMode = normalizeMode(mode, "reps")
    return JsonArray(source.arr("sets").filter { phaseForSet(it) == "work" && modeForSet(it, target) == expectedMode })
}

private fun completedRowsForMode(entry: JsonElement?, mode: String?): JsonArray =
    JsonArray(workRowsForMode(entry, mode).filter { it.asObj()?.bool("done") == true && !isWarmupRow(it) })

fun metricRowsForEntry(entry: JsonElement?, mode: String? = null): JsonArray {
    val requested = mode?.trim()?.lowercase() ?: ""
    val resolved = if (METRIC_MODES.contains(requested)) requested else metricModeForEntry(entry)
    return if (resolved.isNullOrEmpty()) JsonArray(emptyList()) else completedRowsForMode(entry, resolved)
}

/** The authoritative metric for an entry; reps rows take precedence over timed rows. */
fun metricModeForEntry(entry: JsonElement?, fallback: String? = null): String? {
    for (mode in METRIC_MODES) {
        if (completedRowsForMode(entry, mode).isNotEmpty()) return mode
    }
    return modeForEntry(entry, fallback)
}

/** Best load from completed work rows, with a guarded reps-only legacy topW fallback. */
fun bestWeightForEntry(entry: JsonElement? = null): Double {
    val source = objectOf(entry)
    val target = objectOf(orTruthy(source["target"], source) ?: source)
    val sets = source["sets"].asArr()
    val workRows = if (sets != null) JsonArray(sets.filter { phaseForSet(it) == "work" }) else JsonArray(emptyList())
    val repsRows = metricRowsForEntry(entry, "reps")
    // Reps rows are the authoritative load metric for a mixed entry. Otherwise use every completed
    // work row (timed holds can carry an added load too).
    val completedRows = if (repsRows.isNotEmpty()) repsRows
    else JsonArray(workRows.filter { it.asObj()?.bool("done") == true && !isWarmupRow(it) })
    var best = 0.0
    var hasUsableWeight = false
    completedRows.forEach { set ->
        val weight = set.asObj()?.num("w")
        if (weight == null || !weight.isFinite()) return@forEach
        best = if (hasUsableWeight) max(best, weight) else weight
        hasUsableWeight = true
    }
    // A real completed row, including an explicit zero for an unloaded bodyweight set, always wins.
    if (hasUsableWeight) return best

    val parentMode = modeForSet(JsonObject(emptyMap()), target)
    val hasNonRepsWorkRow = workRows.any { modeForSet(it, target) != "reps" }
    val hasWarmup = sets != null && sets.any { isWarmupRow(it) }
    val topWeight = source.num("topW")
    // topW predates phase-tagged warm-ups. It remains a fallback for legacy all-work records, but
    // cannot override resolved work rows once any warm-up marker exists.
    if (parentMode == "reps" && !hasNonRepsWorkRow && !hasWarmup && topWeight != null
        && topWeight.isFinite() && (best <= 0.0 || topWeight > best)) best = topWeight
    return best
}

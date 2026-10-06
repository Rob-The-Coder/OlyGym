package olygym.app.lib

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.int
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.truthy

/*
 * Effort as a statistic: one internal scale, both display scales — a port of
 * frontend/src/lib/effort.js.
 *
 * A set carries either `rir` or `rpe` and is never rewritten — switching the setting changes what
 * new sets ask for, nothing else (see history.js). For a *chart* that is a problem: a history that
 * mixes the two (own logs in RIR, an imported file in RPE) would draw two half-empty series. So
 * everything aggregates in RIR and is converted back for display. RIR is the internal unit because
 * it has a real zero — a set taken to failure — where RPE's floor of 6 is only a convention about
 * which sets are worth rating. RPE 8 == RIR 2.
 */

// At or below this a set is close enough to failure to be the kind that drives adaptation.
// 3 rather than 2: the line is a convention, and drawn one rep too generously it still separates
// working sets from the ones left in the warm-up range.
const val HARD_RIR = 3.0
// Below this many rated sets an average is noise. Showing "RIR 1.0" off a single set reads like a
// finding when it is one tap, so the callers show a dash instead.
const val MIN_RATED = 5


/** A set's effort in RIR, or null when it was never rated. 0 is a rating, not "empty". */
fun rirOf(s: JsonElement?): Double? {
    if (!truthy(s)) return null
    val o = s.asObj() ?: return null
    val rir = o["rir"]
    if (rir.present()) return rir.asNum()
    val rpe = o["rpe"]
    if (rpe.present()) return 10.0 - (rpe.asNum() ?: return null)
    return null
}

/** RIR → the scale being displayed. The reverse of rirOf, for one number. */
fun toScale(kind: String?, rir: Double?): Double? =
    if (rir == null) null
    else floor((if (kind == "rpe") 10.0 - rir else rir) * 10.0 + 0.5) / 10.0

/**
 * Which scale to *label* aggregates with. The profile's own setting wins; a profile that logs
 * nothing itself but carries rated history (an imported case) is shown the scale that history is
 * actually written in, rather than an RIR it has never seen.
 */
fun displayScale(S: JsonElement?): String {
    val k = effortOf(S)
    if (EFFORT.containsKey(k)) return k
    var rir = 0
    var rpe = 0
    eachDoneSet(S) { s, _, _ ->
        if (s["rir"].present()) rir++ else if (s["rpe"].present()) rpe++
    }
    return if (rpe > rir) "rpe" else "rir"
}

fun scaleName(kind: String): String = EFFORT.getValue(kind).hd

/**
 * Every finished set in the profile, oldest first. `fn` gets the set plus the workout it belongs
 * to, which is what the windowed and per-week views need.
 */
private inline fun eachDoneSet(S: JsonElement?, fn: (JsonObject, JsonObject, JsonObject) -> Unit) {
    (S.asObj()?.arr("workouts") ?: JsonArray(emptyList())).forEach { wEl ->
        val w = wEl.asObj() ?: return@forEach
        w.arr("entries").forEach { eEl ->
            val e = eEl.asObj() ?: return@forEach
            e.arr("sets").forEach { sEl ->
                val s = sEl.asObj() ?: return@forEach
                if (truthy(s["done"]) && !isWarmupRow(s)) fn(s, w, e)
            }
        }
    }
}

// A window in days, counted back from now. 0 = everything, which is also what an empty history
// means for every caller here.
private fun inWindow(w: JsonObject, days: Int?): Boolean {
    if (days == null || days == 0) return true
    val startEl = w["start"]
    val start: Double? =
        if (truthy(startEl)) startEl.asNum() else w.str("d")?.let { dateMillis(it)?.toDouble() }
    return start != null && start > System.currentTimeMillis() - days * 86_400_000.0
}

/** `new Date(iso).getTime()` — a date-only ISO string parses as UTC midnight. */
private fun dateMillis(iso: String): Long? =
    try {
        LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }

/** `startOfWeek(iso, ws).getTime()` — the JS Date sits at noon local, so this does too. */
private fun weekStartMillis(iso: String, ws: Int): Long =
    startOfWeek(iso, ws).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun avgRir(sets: JsonElement?): Double? {
    val vs = (sets.asArr() ?: JsonArray(emptyList())).mapNotNull { rirOf(it) }
    return if (vs.isNotEmpty()) vs.sum() / vs.size else null
}

/** The headline numbers for a window. */
data class EffortSummary(
    val done: Int,
    val rated: Int,
    val hard: Int,
    val avg: Double?,
    val hardPct: Double?,
)

/**
 * The headline numbers for a window: how hard, how much of it was hard, and — the part that keeps
 * the rest honest — how much of the training was rated at all. Effort is optional and off by
 * default, so partial coverage is the normal case; an average without its denominator would
 * quietly speak for sets that were never rated.
 */
fun effortSummary(S: JsonElement?, days: Int?): EffortSummary {
    var done = 0
    var rated = 0
    var sum = 0.0
    var hard = 0
    eachDoneSet(S) { s, w, _ ->
        if (!inWindow(w, days)) return@eachDoneSet
        done++
        val r = rirOf(s) ?: return@eachDoneSet
        rated++
        sum += r
        if (r <= HARD_RIR) hard++
    }
    return EffortSummary(
        done = done,
        rated = rated,
        hard = hard,
        avg = if (rated >= MIN_RATED) sum / rated else null,
        hardPct = if (rated >= MIN_RATED) hard.toDouble() / rated else null,
    )
}

/** Does this profile hold any rated set at all? Decides whether the effort UI exists. */
fun hasEffort(S: JsonElement?): Boolean {
    var any = false
    eachDoneSet(S) { s, _, _ -> if (!any && rirOf(s) != null) any = true }
    return any
}

/** One point of the per-week effort curve: the week's start (ms), its average RIR and its volume. */
data class EffortWeekPoint(val t: Long, val rir: Double, val n: Int, val sets: Int)

/**
 * Average effort per calendar week, with the week's set count alongside: the pair is the point.
 * Volume up with effort up is fatigue accumulating; volume up with effort flat is the adaptation
 * you were training for. Weeks with a single rated set are dropped rather than drawn — one tap
 * should not become a peak in the curve.
 */
fun effortWeeks(S: JsonElement?, days: Int?): List<EffortWeekPoint> {
    val ws = weekStartOf(S.asObj()?.int("weekStart"))
    val wk = LinkedHashMap<String, MutableEffortWeek>()
    eachDoneSet(S) { s, w, _ ->
        if (!inWindow(w, days)) return@eachDoneSet
        val iso = w.str("d") ?: return@eachDoneSet
        val k = weekKey(iso, ws)
        val e = wk.getOrPut(k) { MutableEffortWeek(weekStartMillis(iso, ws)) }
        e.sets++
        val r = rirOf(s)
        if (r != null) {
            e.sum += r
            e.n++
        }
    }
    return wk.values.filter { it.n >= 2 }.sortedBy { it.t }
        .map { EffortWeekPoint(t = it.t, rir = it.sum / it.n, n = it.n, sets = it.sets) }
}

private data class MutableEffortWeek(val t: Long, var sum: Double = 0.0, var n: Int = 0, var sets: Int = 0)

/**
 * How the rated sets spread across the scale, in whole steps with everything past the top bucket
 * collapsed into it. This is the chart that answers "am I training too far from failure, or
 * leaving nothing for the next session" — an average alone hides both, because half the sets at 0
 * and half at 4 average to a healthy-looking 2.
 */
const val BUCKETS = 4 // 0,1,2,3 and a "4+" tail

/** One histogram column: the bucket's floor RIR, its "4+" tail flag, count and share. */
data class EffortBin(val rir: Int, val tail: Boolean, val n: Int, val pct: Double)

fun effortHistogram(S: JsonElement?, days: Int?): List<EffortBin> {
    val bins = IntArray(BUCKETS + 1)
    var rated = 0
    eachDoneSet(S) { s, w, _ ->
        if (!inWindow(w, days)) return@eachDoneSet
        val r = rirOf(s) ?: return@eachDoneSet
        rated++
        bins[min(BUCKETS, max(0, floor(r).toInt()))]++
    }
    return bins.mapIndexed { i, n ->
        EffortBin(rir = i, tail = i == BUCKETS, n = n, pct = if (rated != 0) n.toDouble() / rated else 0.0)
    }
}

/** A set that counts as hard — the filter behind the muscle map's "hard sets" mode. */
fun isHardSet(s: JsonElement?): Boolean {
    val r = rirOf(s)
    return r != null && r <= HARD_RIR
}

// ---------------------------------------------------------------- colour bands --
//
// A set's effort read as a colour, so a glance down a workout tells you how hard it ran without
// reading every number. Bands are defined once, in RIR (the internal scale), and apply unchanged
// whether the profile logs RIR or RPE — RPE 8 and RIR 2 are the same set and get the same colour.
// The scale runs from "nothing left" (purple, past red — the far end, not just "very hard") through
// to "easy, warm-up range" (green).
//
// The named buckets are also the quick-pick presets: one tap logs the middle of a band. They are
// deliberately coarse where estimation is coarse — nobody reliably tells 5 RIR from 7, so
// everything from 4 up shares one bucket — and fine near failure, where the difference between 0,
// 0.5 and 1 actually changes the training. A typed value between presets still colours by the band
// it falls in, so the flexibility of a free number is never lost.
data class EffortBand(val rir: Double, val max: Double, val color: String, val feel: String)

val EFFORT_BANDS: List<EffortBand> = listOf(
    EffortBand(0.0, 0.25, "var(--purple)", "Nothing left — went to failure"),
    EffortBand(0.5, 0.75, "var(--red)", "Maybe half a rep left"),
    EffortBand(1.0, 1.5, "var(--orange)", "One more rep in the tank"),
    EffortBand(2.0, 2.5, "var(--yellow)", "Two more reps"),
    EffortBand(3.0, 3.5, "var(--green)", "Three more reps"),
    EffortBand(4.0, Double.POSITIVE_INFINITY, "var(--acc-2)", "Easy — warm-up territory"),
)

// The presets shown in the picker, hardest first — the order they read on the scale and the order
// the colours run. `tail` is the collapsed top bucket ("4+"): its value is the floor it stands for,
// so a tap logs a concrete 4, not a range nothing downstream could average.
data class EffortPreset(val rir: Double, val color: String, val feel: String, val tail: Boolean)

val EFFORT_PRESETS: List<EffortPreset> = EFFORT_BANDS.mapIndexed { i, b ->
    EffortPreset(rir = b.rir, color = b.color, feel = b.feel, tail = i == EFFORT_BANDS.size - 1)
}

/**
 * The colour for one effort value, given in RIR (use rirOf to convert a set first). Picks the band
 * the value falls into: an exact preset lands on its own band, a typed in-between value (RIR 1.5)
 * takes the band it sits within so it is never left uncoloured. null (unrated) has no colour — the
 * caller shows a neutral control, not a green one.
 */
fun effortColor(rir: Double?): String? {
    if (rir == null) return null
    for (b in EFFORT_BANDS) if (rir <= b.max) return b.color
    return EFFORT_BANDS[EFFORT_BANDS.size - 1].color
}

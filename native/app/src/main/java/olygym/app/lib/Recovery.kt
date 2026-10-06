package olygym.app.lib

import kotlin.math.exp
import kotlin.math.pow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.jsText
import olygym.app.data.num
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.truthy

/*
 * Per-muscle fatigue and retained strength over the recent workout history - a port of
 * frontend/src/lib/recovery.js and frontend/src/lib/recovery-view.js.
 *
 * Inputs are the stored JSON shapes (a workout, an entry, a set), never data classes: the same
 * loose reads the JS makes, through the accessors in data/Js.kt.
 */

// A "normal" hard session for one muscle, in primary-set equivalents. The saturation curve
// 1 - exp(-stimulus / REF) maps any session size onto [0,1) so volume raises the starting
// fatigue level without ever pinning it, and the value can then fade asymptotically.
const val FATIGUE_REF_VOLUME: Double = 2000.0
const val FATIGUE_MIN_SESSIONS: Double = 3.0
// Computational bound for the stimulus scan, not a semantic cliff: after 30 days (20
// half-lives) a session contributes below 1e-6 to the accumulated value.
const val FATIGUE_SCAN_MS: Double = 30.0 * 24 * 60 * 60 * 1000
const val BODYWEIGHT_REF_LOAD: Double = 75.0
const val CARDIO_TONNAGE_PER_MIN: Double = 50.0

// Preserve the shipped set-count signal when a completed custom/imported exercise has no load.
// Two such sets therefore retain the old 1 - exp(-2 / 3) starting-fatigue reading.
private val ZERO_LOAD_SET_STIMULUS: Double = FATIGUE_REF_VOLUME / 3

/** Exponential half-life for fatigue stimulus. */
const val FATIGUE_HALF_LIFE_MS: Double = 129600000.0

/** Period after training during which retained strength remains at full value. */
const val STRENGTH_FULL_MS: Double = 1209600000.0

/** Exponential half-life for retained strength after the full-retention period. */
const val STRENGTH_HALF_LIFE_MS: Double = 2419200000.0

/** Minimum retained-strength value for an untrained or fully detrained muscle. */
const val STRENGTH_FLOOR: Double = 0.5

/**
 * Stable labels for consumer fatigue buckets: values below 0.25 are ready, values from 0.25
 * through 0.5 are recovering, and values above 0.5 are fatigued.
 */
object FATIGUE_STATES {
    const val READY = "ready"
    const val RECOVERING = "recovering"
    const val FATIGUED = "fatigued"
}

/**
 * Return exponential decay expressed as a fraction of one half-life.
 *
 * @param ageMs Elapsed age of the stimulus in milliseconds.
 * @param halfLifeMs Duration of one half-life in milliseconds.
 * @return Remaining fraction, using the exact 0.5 ** (age / halfLife) formula.
 */
fun halfLifeDecay(ageMs: Double, halfLifeMs: Double): Double = 0.5.pow(ageMs / halfLifeMs)

/** The consumer-facing fatigue state for one numeric recovery value (recovery-view.js). */
fun fatigueStateOf(value: Double): String = when {
    value < 0.25 -> FATIGUE_STATES.READY
    value <= 0.5 -> FATIGUE_STATES.RECOVERING
    else -> FATIGUE_STATES.FATIGUED
}

private const val REP_CAP = 12.0

const val LB_TO_KG = 0.45359237

private val UNIT_KEYS = listOf("unit", "u", "weightUnit", "weight_unit", "loadUnit")

private val MUSCLES_BY_SLUG: Set<String> = MUSCLES.toSet()

private val POUNDS_UNITS = setOf("lb", "lbs", "pound", "pounds")

// JS `new Date(d).getTime()` for the stored ISO strings.
private fun dateMillis(value: JsonElement?): Double {
    if (value == null || value is JsonNull) return Double.NaN
    if (value is JsonPrimitive && !value.isString) return value.doubleOrNull ?: Double.NaN
    val text = value.asStr() ?: return Double.NaN
    return runCatching { java.time.Instant.parse(text).toEpochMilli().toDouble() }
        .recoverCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli().toDouble() }
        .recoverCatching {
            java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli().toDouble()
        }
        .recoverCatching {
            java.time.LocalDateTime.parse(text).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli().toDouble()
        }
        .getOrElse { Double.NaN }
}

// The v2 data contract has one timestamp per workout, not per set. Keeping the fallback in one
// place means fatigue and strength are scored at exactly the same stimulus time as effort.js.
private fun workoutTimestamp(workout: JsonElement?): Double {
    val start = workout.asObj()?.get("start")
    if (truthy(start)) {
        val primitive = start as? JsonPrimitive
        val number = if (primitive != null && !primitive.isString) primitive.doubleOrNull else null
        if (number != null && number.isFinite()) return number
        return start.asNum() ?: Double.NaN
    }
    return dateMillis(workout.asObj()?.get("d"))
}

private fun emptyMuscleMap(value: Double): LinkedHashMap<String, Double> {
    val map = LinkedHashMap<String, Double>()
    MUSCLES.forEach { map[it] = value }
    return map
}

// Current catalogue metadata stays authoritative. Finished entries retain a nested muscle
// snapshot specifically so deleted custom exercises can still contribute to recovery maps.
private fun exerciseFor(entry: JsonElement?): JsonElement? {
    val id = entry.asObj()?.str("id")
    val ex = if (id != null) Catalogue[id] else null
    return if (ex != null) exerciseJson(ex) else entry
}

// JS `Number(value)` with the explicit rejections recovery.js makes first.
private fun numeric(value: JsonElement?): Double? {
    if (value == null || value is JsonNull) return null
    if (value is JsonPrimitive) {
        if (value.isString) {
            if (value.content == "") return null
        } else if (value.booleanOrNull != null) {
            return null
        }
    }
    val n = value.asNum() ?: return null
    return if (n.isFinite()) n else null
}

private fun isPounds(unit: String?): Boolean = POUNDS_UNITS.contains((unit ?: "").trim().lowercase())

private fun unitOf(vararg records: JsonElement?): String {
    for (record in records) {
        val obj = record as? JsonObject ?: continue
        for (key in UNIT_KEYS) {
            val value = obj[key]
            if (value != null && value !is JsonNull && value.jsText().trim().isNotEmpty()) return value.jsText()
        }
    }
    return "kg"
}

private fun kgOf(value: JsonElement?, unit: String?): Double {
    val n = numeric(value) ?: return 0.0
    return maxOf(0.0, n) * (if (isPounds(unit)) LB_TO_KG else 1.0)
}

// A bodyweight value stamped on a workout is the best historical value. When old records do not
// carry one, use the current profile's canonical bodyweight supplied by Stats, then the stable
// fallback used by the original fatigue model.
private fun bodyweightKgFor(workout: JsonElement?, opts: JsonObject, stampedLoadUnit: String?): Double {
    val stamped = numeric(workout.asObj()?.get("bw")) ?: numeric(workout.asObj()?.get("bodyweight"))
    if (stamped != null) {
        return kgOf(
            JsonPrimitive(stamped),
            unitOf(
                js("unit" to workout.asObj()?.str("bwUnit")),
                js("unit" to workout.asObj()?.str("bodyweightUnit")),
                workout,
                if (stampedLoadUnit != null) js("unit" to stampedLoadUnit) else null,
                opts,
            ),
        )
    }
    val canonical = numeric(opts["bodyweightKg"])
    if (canonical != null) return maxOf(0.0, canonical)
    val display = numeric(opts["bodyweight"])
    if (display != null) {
        val displayUnit = listOf("bodyweightUnit", "unit", "profileUnit")
            .mapNotNull { opts.str(it) }
            .firstOrNull { it.isNotEmpty() }
        return kgOf(JsonPrimitive(display), displayUnit)
    }
    return BODYWEIGHT_REF_LOAD
}

private fun bodyweightTarget(entry: JsonObject?): Boolean? {
    val target = entry?.get("target").asObj()
    if (target != null && target.containsKey("bodyweight")) return truthy(target["bodyweight"])
    if (entry != null && entry.containsKey("bodyweight")) return truthy(entry["bodyweight"])
    return null
}

private fun hasUnitStamp(vararg records: JsonElement?): Boolean = records.any { record ->
    val obj = record as? JsonObject ?: return@any false
    UNIT_KEYS.any { key ->
        val value = obj[key]
        value != null && value !is JsonNull && value.jsText().trim().isNotEmpty()
    }
}

private fun bodyweightConfigured(
    ex: JsonElement?,
    entry: JsonObject?,
    set: JsonElement?,
    workout: JsonElement?,
    opts: JsonObject,
): Boolean {
    val configured = bodyweightTarget(entry)
    if (configured != null) return configured
    // Before target.bodyweight was persisted, a positive w on a catalogue bodyweight exercise
    // already meant an explicitly entered load. Keep those rows compatible when no body-mass
    // context is available; a stamped workout or a profile bodyweight makes the intended
    // total-load semantics unambiguous even for old entries.
    val added = kgOf(set.asObj()?.get("w"), unitOf(set, entry?.get("target"), entry, workout, opts))
    val hasBodyweightContext = numeric(workout.asObj()?.get("bw")) != null ||
        numeric(workout.asObj()?.get("bodyweight")) != null ||
        numeric(opts["bodyweightKg"]) != null ||
        numeric(opts["bodyweight"]) != null ||
        hasUnitStamp(set, entry?.get("target"), entry, workout)
    return ex.asObj()?.str("eq") == "body weight" && (added == 0.0 || hasBodyweightContext)
}

private fun loadKgFor(
    ex: JsonElement?,
    entry: JsonObject?,
    set: JsonElement?,
    workout: JsonElement?,
    opts: JsonObject,
): Double {
    val setUnit = unitOf(set, entry?.get("target"), entry, workout, opts)
    val addedKg = kgOf(set.asObj()?.get("w"), setUnit)
    val loadUnit = if (hasUnitStamp(set, entry?.get("target"), entry)) setUnit else null
    return if (bodyweightConfigured(ex, entry, set, workout, opts)) {
        bodyweightKgFor(workout, opts, loadUnit) + addedKg
    } else {
        addedKg
    }
}

// Epley one-rep-max estimate (REP_CAP included so high-rep sets do not inflate the estimate).
// Used only to express a set's intensity relative to the lifter's own capacity.
private fun epley1RM(load: Double, reps: Double): Double = load * (1 + minOf(reps, REP_CAP) / 30)

// Best Epley estimate inside one session. Keeping intensity context on the session makes a
// scored stimulus independent of later imports/deletes; unlike an all-history maximum, a
// 90-day-old CSV row cannot retroactively reweight today's sets.
private fun session1RMs(workout: JsonElement?, opts: JsonObject): Map<String, Double> {
    val best = LinkedHashMap<String, Double>()
    for (entryElement in workout.asObj()?.arr("entries") ?: JsonArray(emptyList())) {
        val entry = entryElement.asObj() ?: continue
        val ex = exerciseFor(entry)
        for (setElement in entry.arr("sets")) {
            val set = setElement.asObj()
            val load = loadKgFor(ex, entry, setElement, workout, opts)
            val reps = set?.num("r")
            if (set?.bool("done") != true || !(load > 0.0) || !(reps != null && reps > 0.0)) continue
            val est = epley1RM(load, reps)
            val id = entry.str("id")
            val previous = if (id != null) best[id] else null
            if (id != null && (previous == null || est > previous)) best[id] = est
        }
    }
    return best
}

// Intensity-weighted tonnage for one completed set: load x reps x (load / exercise 1RM)^1.5.
// The exponent saturates the hard-set effect - a set at 90% of your 1RM counts ~0.81 of its
// raw tonnage, one at 50% only ~0.35. Cardio, timed holds, and sets whose exercise has no
// 1RM history stay unweighted (duration proxies, or intensity 1 for the first sessions).
private fun setTonnage(
    ex: JsonElement?,
    entry: JsonObject?,
    set: JsonElement?,
    workout: JsonElement?,
    oneRm: Double?,
    opts: JsonObject,
): Double {
    val setObj = set.asObj()
    if (ex.asObj()?.str("bp") == "cardio") {
        val min = setObj?.get("min").asNum() ?: 0.0
        val sec = setObj?.get("sec").asNum() ?: 0.0
        return maxOf(min, sec / 60.0) * CARDIO_TONNAGE_PER_MIN
    }
    val secElement = setObj?.get("sec")
    val repsElement = setObj?.get("r")
    if (secElement.present() && (repsElement == null || repsElement is JsonNull)) {
        return (secElement.asNum() ?: 0.0) / 60.0 * CARDIO_TONNAGE_PER_MIN
    }
    val reps = setObj?.get("r")?.asNum() ?: 1.0
    val load = loadKgFor(ex, entry, set, workout, opts)
    val raw = load * reps
    // A bodyweight target is already an external-load-normalised total (body mass + any added
    // load). It has no meaningful barbell-style 1RM intensity ratio in the legacy data model, so
    // retain the monotonic total-load stimulus instead of letting a newly created low 1RM shrink
    // a weighted bodyweight set below the unloaded version.
    if (bodyweightConfigured(ex, entry, set, workout, opts)) return raw
    if (oneRm == null || !(oneRm > 0.0) || !(load > 0.0)) return raw
    return raw * minOf(1.0, load / oneRm).pow(1.5)
}

// One session's per-muscle stimulus, calculated only from that session. A completed zero-load
// set gets the set-equivalent fallback instead of disappearing from fatigue.
private fun sessionTonnages(workout: JsonElement?, opts: JsonObject): LinkedHashMap<String, Double> {
    val sums = emptyMuscleMap(0.0)
    val oneRms = session1RMs(workout, opts)
    for (entryElement in workout.asObj()?.arr("entries") ?: JsonArray(emptyList())) {
        val entry = entryElement.asObj() ?: continue
        val ex = exerciseFor(entry)
        val weights = musclesOf(ex)
        for (setElement in entry.arr("sets")) {
            if (setElement.asObj()?.bool("done") != true) continue
            val measured = setTonnage(ex, entry, setElement, workout, oneRms[entry.str("id")], opts)
            val tonnage = if (measured.isFinite() && measured > 0.0) measured else ZERO_LOAD_SET_STIMULUS
            for ((slug, weight) in weights) {
                if (MUSCLES_BY_SLUG.contains(slug)) {
                    sums[slug] = sums.getValue(slug) + tonnage * (weight.asNum() ?: 0.0)
                }
            }
        }
    }
    return sums
}

private data class StimulusEvent(val timestamp: Double, val stimulus: Double)

// Build normalised stimuli in workout order. The reference seen by a session is strictly the
// reference left by earlier in-window sessions; the session cannot dilute its own score. The
// downward-only EWMA is deliberate: removing any earlier workout can only raise a later
// denominator (and removes its own positive stimulus), so deletion can never increase fatigue.
// Rebuilding from the bounded scan also makes imports older than the scan exactly irrelevant.
private fun fatigueStimuli(
    workouts: JsonElement?,
    current: Double,
    opts: JsonObject,
): Map<String, List<StimulusEvent>> {
    val cutoff = current - FATIGUE_SCAN_MS
    val ordered = ((workouts as? JsonArray)?.toList() ?: emptyList())
        .mapIndexed { index, workout -> Triple(workout, index, workoutTimestamp(workout)) }
        .filter { it.third.isFinite() && it.third > cutoff }
        .sortedWith(compareBy({ it.third }, { it.second }))
    val references = emptyMuscleMap(FATIGUE_REF_VOLUME)
    val byMuscle = LinkedHashMap<String, MutableList<StimulusEvent>>()
    MUSCLES.forEach { byMuscle[it] = mutableListOf() }

    for ((workout, _, timestamp) in ordered) {
        val sums = sessionTonnages(workout, opts)
        for (slug in MUSCLES) {
            val stimulus = sums[slug] ?: 0.0
            if (!(stimulus > 0.0)) continue
            byMuscle.getValue(slug).add(StimulusEvent(timestamp, stimulus / references.getValue(slug)))
            val ewma = references.getValue(slug) + (stimulus - references.getValue(slug)) / FATIGUE_MIN_SESSIONS
            references[slug] = minOf(references.getValue(slug), ewma)
        }
    }
    return byMuscle
}

private fun fatigueValue(events: List<StimulusEvent>, now: Double): Double {
    if (events.isEmpty()) return 0.0
    val ordered = events.sortedBy { it.timestamp }
    var value = 0.0
    var lastTimestamp = ordered.first().timestamp
    for (event in ordered) {
        value *= halfLifeDecay(event.timestamp - lastTimestamp, FATIGUE_HALF_LIFE_MS)
        value += event.stimulus
        lastTimestamp = event.timestamp
    }
    value *= halfLifeDecay(maxOf(0.0, now - lastTimestamp), FATIGUE_HALF_LIFE_MS)
    // Normalise the accumulated stimulus to a saturating fatigue level: more volume starts
    // higher but never pins, and the value fades asymptotically - no window-edge cliff.
    return 1 - exp(-value)
}

/**
 * Calculate current per-muscle fatigue from completed sets in the recent window.
 *
 * Stimulus time is the workout start, falling back to the workout date. Each completed set
 * contributes the exercise's musclesOf weights; the scan is bounded to FATIGUE_SCAN_MS for
 * performance, not semantics. Stimuli are accumulated chronologically with a 36-hour half-life,
 * decayed to now, and normalised with the saturation curve 1 - exp(-v). Each session is scored
 * against a causal, downward-only EWMA left by strictly earlier in-window sessions. The result
 * always contains every drawable muscle slug.
 */
fun fatigueOf(
    workouts: JsonElement?,
    now: Long,
    bodyweightKg: Double? = null,
    unit: String? = null,
): Map<String, Double> {
    val current = now.toDouble()
    val result = emptyMuscleMap(0.0)
    val opts = js("bodyweightKg" to bodyweightKg, "unit" to unit)
    val byMuscle = fatigueStimuli(workouts, current, opts)
    for (slug in MUSCLES) result[slug] = fatigueValue(byMuscle.getValue(slug), current)
    return result
}

/**
 * Calculate retained per-muscle strength from the latest completed stimulus in all history.
 *
 * A muscle with no completed set starts at the 0.5 floor. After a completed set, strength is
 * 1.0 through 14 days old, then decays toward the floor with a 28-day half-life. Any later
 * completed set becomes the new latest stimulus and resets the 14-day full-retention period.
 * The result always contains every drawable muscle slug.
 */
fun strengthOf(
    workouts: JsonElement?,
    now: Long,
    @Suppress("UNUSED_PARAMETER") bodyweightKg: Double? = null,
    @Suppress("UNUSED_PARAMETER") unit: String? = null,
): Map<String, Double> {
    val current = now.toDouble()
    val latest = LinkedHashMap<String, Double>()
    MUSCLES.forEach { latest[it] = Double.NEGATIVE_INFINITY }
    for (workoutElement in (workouts as? JsonArray)?.toList() ?: emptyList()) {
        val timestamp = workoutTimestamp(workoutElement)
        if (!timestamp.isFinite()) continue
        val workout = workoutElement.asObj() ?: continue
        for (entryElement in workout.arr("entries")) {
            val entry = entryElement.asObj() ?: continue
            val hasWork = entry.arr("sets").any { it.asObj()?.bool("done") == true && !isWarmupRow(it) }
            if (!hasWork) continue
            for (slug in musclesOf(exerciseFor(entry)).keys) {
                if (MUSCLES_BY_SLUG.contains(slug) && timestamp > latest.getValue(slug)) latest[slug] = timestamp
            }
        }
    }

    val result = emptyMuscleMap(STRENGTH_FLOOR)
    for (slug in MUSCLES) {
        val lastTimestamp = latest.getValue(slug)
        if (!lastTimestamp.isFinite()) continue
        val age = current - lastTimestamp
        if (age <= STRENGTH_FULL_MS) {
            result[slug] = 1.0
        } else {
            result[slug] = maxOf(
                STRENGTH_FLOOR,
                halfLifeDecay(age - STRENGTH_FULL_MS, STRENGTH_HALF_LIFE_MS),
            )
        }
    }
    return result
}

/** List muscles currently above the fatigued threshold, in MUSCLES head-to-toe order. */
fun fatiguedMuscles(
    workouts: JsonElement?,
    now: Long,
    bodyweightKg: Double? = null,
    unit: String? = null,
): List<String> = MUSCLES.filter { (fatigueOf(workouts, now, bodyweightKg, unit)[it] ?: 0.0) > 0.5 }

/**
 * List muscles whose retained strength is below full retention, in MUSCLES head-to-toe order;
 * never-trained muscles are included at the 0.5 floor.
 */
fun detrainedMuscles(
    workouts: JsonElement?,
    now: Long,
    bodyweightKg: Double? = null,
    unit: String? = null,
): List<String> = MUSCLES.filter { (strengthOf(workouts, now, bodyweightKg, unit)[it] ?: 0.0) < 1.0 }


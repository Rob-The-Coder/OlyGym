package olygym.app.lib

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asBool
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.num
import olygym.app.data.present
import olygym.app.data.str

/*
 * Focused workout semantics shared by the session and history views — a port of
 * frontend/src/lib/workout-model.js. Legacy records have no explicit phase or mode, so the
 * defaults preserve the work/reps shape.
 */

private val MODES = listOf("reps", "time")

/** JS `objectOf`: the value when it is an object, an empty one otherwise. */
private fun objectOf(value: JsonElement?): JsonObject = value.asObj() ?: JsonObject(emptyMap())

private fun normalizedPhase(value: String?, fallback: String = "work"): String {
    val token = value?.trim()?.lowercase() ?: ""
    if (token == "warmup" || token == "warm-up" || token == "warm_up") return "warmup"
    if (token == "work") return "work"
    return if (fallback == "warmup") "warmup" else "work"
}

/** Resolve a row's phase. An explicit phase wins over the legacy warmup boolean. */
fun phaseForSet(set: JsonElement?, fallback: String = "work"): String {
    val source = objectOf(set)
    val phase = source["phase"]
    // `phase != null && phase !== ''`: a non-string passes this test and then reads as the
    // fallback inside normalizedPhase, which is what the JS does with any non-string.
    if (phase.present() && phase.asStr() != "") return normalizedPhase(phase.asStr(), fallback)
    return if (source["warmup"].asBool() == true) "warmup" else normalizedPhase(null, fallback)
}

fun isWarmupRow(set: JsonElement?): Boolean = phaseForSet(set) == "warmup"

/** Is this row checked off? */
fun hasCompletedWork(set: JsonElement?): Boolean = set.asObj()?.bool("done") == true

/** Actual completed load x reps. */
fun completedVolumeOf(set: JsonElement?): Double {
    val source = set.asObj() ?: return 0.0
    if (source.bool("done") != true) return 0.0
    return (source.num("w") ?: 0.0) * (source.num("r") ?: 0.0)
}

fun normalizeMode(value: String?, fallback: String = "reps"): String {
    val token = value?.trim()?.lowercase() ?: ""
    if (MODES.contains(token)) return token
    return if (MODES.contains(fallback)) fallback else "reps"
}

private val REP_UNITS = listOf("rep", "reps", "repetition", "repetitions")
private val TIME_UNITS = listOf("sec", "secs", "second", "seconds")

private fun modeFromUnit(value: JsonElement?): String? {
    val token = value.asStr()?.trim()?.lowercase() ?: ""
    if (REP_UNITS.contains(token)) return "reps"
    if (TIME_UNITS.contains(token)) return "time"
    return null
}

private fun explicitMode(source: JsonObject): String? {
    val token = source["mode"].asStr()?.trim()?.lowercase() ?: ""
    if (MODES.contains(token)) return token
    return modeFromUnit(source["unit"])
}

private fun inferredMode(source: JsonObject): String? {
    explicitMode(source)?.let { return it }
    if ((source.str("mode") ?: "").trim().lowercase() == "amrap") return "reps"
    if (source["sec"].present() || source["seconds"].present() || source["durationSec"].present()) return "time"
    if (source["r"].present() || source["reps"].present() || source["actualReps"].present()) return "reps"
    return null
}

/** Resolve one row's mode: explicit row, parent target, then legacy result fields. */
fun modeForSet(set: JsonElement?, target: JsonElement? = null): String =
    explicitMode(objectOf(set))
        ?: inferredMode(objectOf(target))
        ?: inferredMode(objectOf(set))
        ?: "reps"

/** Resolve a single mode for an entry; mixed work-row modes intentionally return null. */
fun modeForEntry(entry: JsonElement?, fallback: String? = null): String? {
    val source = objectOf(entry)
    // JS `source.target || source`: a missing or null target reads as the entry itself.
    val target = if (source["target"].asObj() != null) objectOf(source["target"]) else source
    val sets = source.arr("sets")
    val work = sets.filter { !isWarmupRow(it) }
    val observed = if (work.isNotEmpty()) work else sets
    val modes = observed.map { modeForSet(it, target) }.toSet()
    if (modes.size > 1) return null
    if (modes.size == 1) return modes.first()
    val targetMode = inferredMode(target)
    if (targetMode != null) return targetMode
    return if (fallback == null) modeForSet(source, target) else normalizeMode(fallback)
}

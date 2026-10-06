package olygym.app.lib

import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.with

/*
 * Automatic progression (issue #17) — a port of frontend/src/lib/progression.js.
 *
 * Everything here is a pure function of the workout history. Nothing writes back into a finished
 * workout: the log is what happened, and the next prescription is derived from it every time it is
 * needed. One policy remains, linear: hit every rep in every set and the weight goes up; repeated
 * misses trigger a deload. It is opt-in — off unless Settings turns it on, or an exercise names a
 * rule of its own — so a planned weight stays the planned weight.
 *
 * Reading a session honestly is the whole game:
 *   · a set checked off with at least its target reps → hit
 *   · a set checked off with fewer reps                → miss (you logged what you got)
 *   · a set never checked off                          → miss (it was not performed)
 *   · fewer sets than prescribed                       → miss
 * So a session that fell apart can never advance the load as though it had succeeded.
 */

val POLICIES = listOf("off", "linear")

// Which policies can sensibly drive which logging mode.
val POLICIES_FOR: Map<String, List<String>> = linkedMapOf(
    "reps" to listOf("off", "linear"),
    "time" to listOf("off"),
)

val POLICY_NAME: Map<String, String> = linkedMapOf(
    "off" to "No automatic progression",
    "linear" to "Linear progression",
)
val POLICY_DESC: Map<String, String> = linkedMapOf(
    "off" to "Targets stay where you set them.",
    "linear" to "Hit every rep in every set and the weight goes up. Repeated misses trigger a deload.",
)

// Back off by this factor when a linear session stalls.
const val DELOAD_FACTOR = 0.9
val DELOAD_AFTER: Map<String, Int> = mapOf("linear" to 3)

// Muscles where a 5 kg jump is normal rather than brutal: the lower body and the posterior chain.
// Reading the exercise's own muscles keeps the rule honest for any catalogue: a back squat is
// quadriceps-led, a bench press is not, and a competition snatch (trapezius-led) stays on the
// 2.5 kg step the sport actually uses.
private val HEAVY_MUSCLES = listOf("quadriceps", "gluteal", "hamstring", "lower-back", "adductors", "calves", "tibialis")

/** JS truthiness, !value — see History.kt. Kept private to this file on purpose. */
private fun truthy(value: JsonElement?): Boolean = when (value) {
    null, is JsonNull -> false
    is JsonArray, is JsonObject -> true
    is JsonPrimitive -> when {
        value.isString -> value.content.isNotEmpty()
        else -> {
            val b = value.booleanOrNull
            if (b != null) b else {
                val d = value.doubleOrNull
                d != null && d != 0.0 && !d.isNaN()
            }
        }
    }
}

/** JS a || b || c: the first value that is not falsy, or null. */
private fun orTruthy(vararg values: JsonElement?): JsonElement? = values.firstOrNull { truthy(it) }

/** JS Math.round, ties toward +infinity. */
private fun jsRound(value: Double): Double = floor(value + 0.5)

private fun round1(value: Double): Double = jsRound(value * 10.0) / 10.0

/** JS x || 0, NaN included. */
private fun n0(value: JsonElement?): Double {
    val d = value.asNum()
    return if (d == null || d.isNaN()) 0.0 else d
}

// The catalogue's exercise objects are materialised into the JSON muscles.js reads (its own
// catalogueJson is private to Muscles.kt). The JS caches isHeavy in a WeakMap for render cost; the
// port computes it per call rather than keep a second cache of a lookup that is already cheap.
private fun exerciseJson(ex: Exercise): JsonObject =
    js("id" to ex.id, "n" to ex.n, "bp" to ex.bp, "tg" to ex.tg, "sm" to ex.sm)

private fun isHeavy(exId: String?): Boolean {
    val ex = exId?.let { Catalogue[it] } ?: return false
    val weights = musclesOf(exerciseJson(ex))
    return HEAVY_MUSCLES.any { (weights[it].asNum() ?: 0.0) >= 1.0 }
}

// Default load step. Lower-body lifts take the bigger jump — that is the "lift-specific increment"
// a linear program lives on; an exercise can override it with cfg.inc.
fun defaultIncrement(exId: String?): Double = if (isHeavy(exId)) 5.0 else 2.5

// Resolve the load step for reps-mode weight controls and progression.
fun weightIncrement(cfg: JsonElement?): Double {
    val inc = cfg.asObj()?.get("inc").asNum()
    return if (inc != null && inc > 0.0) inc else defaultIncrement(cfg.asObj()?.str("id"))
}

// What automatic progression does when neither the exercise nor its day names a rule: nothing,
// unless the profile switched it on. A planned weight is a target, not a starting point to add to.
fun defaultPolicy(S: JsonElement?): String =
    if (S.asObj()?.bool("autoProg") == true) "linear" else "off"

// The policy in force for one exercise: its own override, else the routine's default, else the
// profile's default. A rule set by hand still beats the setting, in both directions.
fun policyFor(cfg: JsonElement?, routine: JsonElement?, mode: String? = null, fallback: String = "off"): String {
    val m = if (mode != null && mode.isNotEmpty()) mode else modeOf(cfg ?: JsonObject(emptyMap()))
    val allowed = POLICIES_FOR[m] ?: listOf("off")
    val raw = orTruthy(cfg.asObj()?.get("prog"), routine.asObj()?.get("prog"))
    val pick: String? = if (raw == null) fallback else raw.asStr()
    return if (pick != null && allowed.contains(pick)) pick else "off"
}

// Snap to a loadable multiple of the step. Manual weight controls use this same normalization so
// fractional increments produce the same number as automatic progression.
fun snapWeight(v: Double, step: Double): Double =
    if (!(step > 0.0)) round1(v) else round1(jsRound(v / step) * step)

// Add step to a weight the way a stepper tap does: from a weight that sits on the increment's grid
// the sum is snapped to it, from one off the grid the step is simply added (issue #175).
fun addStep(w: Double, step: Double, inc: Double): Double {
    val onGrid = inc > 0.0 && abs(w - jsRound(w / inc) * inc) <= 0.1
    val next = w + step
    return maxOf(0.0, if (onGrid) snapWeight(next, inc) else round1(next))
}

fun stepWeight(value: Double?, step: Double, direction: Int): Double {
    val v = value?.takeIf { !it.isNaN() } ?: 0.0
    return addStep(v, direction * step, step)
}

// Back off by a factor, landing on something you can actually load.
fun deloadTo(cur: Double, step: Double, factor: Double = DELOAD_FACTOR): Double {
    // Below one increment there is nothing left to take off, and snapping the cut would round
    // straight back up to the step: hold the load instead of "deloading" to a heavier weight.
    if (cur <= step) return cur
    var next = snapWeight(cur * factor, step)
    if (next >= cur) next = snapWeight(cur - step, step)
    return maxOf(step, next)
}

// The load the session is judged by.
private fun loadOf(entry: JsonElement?, sets: List<JsonElement>): Double {
    val done = sets.filter { truthy(it.asObj()?.get("done")) }.map { n0(it.asObj()?.get("w")) }
    return maxOf(0.0, done.maxOrNull() ?: 0.0)
}

/**
 * Reduce one finished workout entry to what a policy needs to judge it.
 *
 * Workouts only started recording their prescription in v1.2.2, so most existing history has no
 * target at all. Judging those against nothing would score every past session as a miss. An entry
 * without its own target is judged against fallback, the exercise's current plan.
 */
fun readSession(entry: JsonElement?, fallback: JsonElement? = null): JsonObject {
    val e = entry.asObj()
    val target = orTruthy(e?.get("target"), fallback)?.asObj() ?: JsonObject(emptyMap())
    val mode = modeOf(target)
    val logged = e?.arr("sets")?.filter { !isWarmupRow(it) } ?: emptyList()
    val plannedRaw = target["sets"].asNum()
    val planned = if (plannedRaw != null && !plannedRaw.isNaN() && plannedRaw != 0.0) plannedRaw
    else logged.size.toDouble()
    val enough = logged.size.toDouble() >= planned
    val sets = logged.take(maxOf(1.0, planned).toInt())
    if (mode == "time") {
        val goal = n0(target["sec"])
        val held = sets.map { s -> if (truthy(s.asObj()?.get("done"))) n0(s.asObj()?.get("sec")) else 0.0 }
        return js(
            "mode" to mode,
            "target" to target,
            "goal" to goal,
            "held" to held,
            "weight" to loadOf(e, sets),
            "best" to maxOf(0.0, held.maxOrNull() ?: 0.0),
            "ok" to (goal > 0.0 && enough && held.isNotEmpty() && held.all { it >= goal }),
        )
    }
    val goal = n0(target["reps"])
    val reps = sets.map { s -> if (truthy(s.asObj()?.get("done"))) n0(s.asObj()?.get("r")) else 0.0 }
    return js(
        "mode" to mode,
        "target" to target,
        "goal" to goal,
        "reps" to reps,
        "weight" to loadOf(e, sets),
        "ok" to (goal > 0.0 && enough && reps.isNotEmpty() && reps.all { it >= goal }),
    )
}

/** Every past session for one exercise, oldest first. fallback — see readSession. */
fun sessionsFor(S: JsonElement?, exId: String?, fallback: JsonElement? = null): JsonArray {
    val out = mutableListOf<JsonElement>()
    (S.asObj()?.arr("workouts") ?: JsonArray(emptyList())).forEach { wEl ->
        val w = wEl.asObj() ?: return@forEach
        val entry = w.arr("entries").firstOrNull { it.asObj()?.str("id") == exId }?.asObj() ?: return@forEach
        // A session that does not count for this exercise cannot become the baseline for its next
        // prescription. Exclusion is per-entry now (ENG-11).
        if (entryExcluded(w, entry)) return@forEach
        if (entry.arr("sets").any { truthy(it.asObj()?.get("done")) && !isWarmupRow(it) }) {
            out.add(JsonObject(readSession(entry, fallback) + ("d" to (w["d"] ?: JsonNull))))
        }
    }
    return JsonArray(out)
}

// Count how many sessions in a row ended in a miss, counting back from the most recent. A hit ends
// the streak, and so does a change of weight: a deload should reflect the failures at the weight
// that earned it, not the lighter weight that follows.
fun stallCount(sessions: JsonArray): Int {
    var n = 0
    for (i in sessions.indices.reversed()) {
        val s = sessions[i].asObj() ?: continue
        if (truthy(s["ok"])) break
        if (i < sessions.size - 1 && s["weight"] != sessions[i + 1].asObj()?.get("weight")) break
        n++
    }
    return n
}

/**
 * The next prescription for one exercise.
 *
 * Returns { weight, reps, sec, why, kind } — kind being one of first | up | hold | deload | off,
 * and why a translatable template + args so the app can always answer "why this number?". A field
 * the policy has no opinion on comes back absent and the caller keeps whatever the plan said.
 */
fun nextPrescription(S: JsonElement?, cfg: JsonElement?, routine: JsonElement? = null): JsonObject {
    val mode = modeOf(cfg)
    val policy = policyFor(cfg, routine, mode, defaultPolicy(S))
    val unit = orTruthy(S.asObj()?.get("unit"))?.asStr() ?: "kg"
    val inc = weightIncrement(cfg)
    if (policy == "off") return js("policy" to policy, "kind" to "off")

    val sessions = JsonArray(sessionsFor(S, cfg.asObj()?.str("id"), cfg).filter { it.asObj()?.str("mode") == mode })
    val last = sessions.lastOrNull()?.asObj()
    if (last == null) {
        return js(
            "policy" to policy,
            "kind" to "first",
            "why" to listOf("Nothing logged yet — this session sets the baseline."),
        )
    }

    val stalls = stallCount(sessions)
    val deloadAt = DELOAD_AFTER[policy] ?: 3

    val w = n0(last["weight"])
    // Bodyweight work carries no external load, so there is nothing to add or take away — "deload
    // your push-ups to 2.5 kg" is not advice. Progress in reps instead. Note the trigger is the
    // logged weight, not the bw flag: a dip done with a belt has a load to progress and belongs on
    // the normal path.
    if (w <= 0.0) {
        val goal = n0(last["goal"]).let { if (it != 0.0) it else n0(cfg.asObj()?.get("reps")) }
        if (!truthy(last["ok"]) || goal <= 0.0) {
            return js(
                "policy" to policy,
                "kind" to "hold",
                "weight" to 0,
                "reps" to goal.takeIf { it != 0.0 },
                "why" to listOf("Bodyweight — same target again until every set is clean."),
            )
        }
        val next = goal + 1.0
        return js(
            "policy" to policy,
            "kind" to "up",
            "weight" to 0,
            "reps" to next,
            "why" to listOf("Bodyweight — every rep last time, so go for {0} this time.", next),
        )
    }

    if (truthy(last["ok"])) {
        return js(
            "policy" to policy,
            "kind" to "up",
            "weight" to addStep(w, inc, inc),
            "why" to listOf("Every rep last time — {0} {1} more.", inc, unit),
        )
    }
    if (stalls >= deloadAt) {
        val dw = deloadTo(w, inc)
        val why = if (stalls > 1) {
            listOf("Missed reps {0} sessions running — reset to {1} {2} and work back up.", stalls.toDouble(), dw, unit)
        } else {
            listOf("Missed reps — reset to {0} {1} and work back up.", dw, unit)
        }
        return js("policy" to policy, "kind" to "deload", "weight" to dw, "why" to why)
    }
    return js(
        "policy" to policy,
        "kind" to "hold",
        "weight" to w,
        "why" to listOf("Missed reps last time — same weight again ({0} of {1} to go).", (deloadAt - stalls).toDouble(), deloadAt.toDouble()),
    )
}

/**
 * Apply a prescription to freshly built sets. Only the fields the policy actually decided are
 * touched, and only on sets that have not been logged yet.
 */
fun applyPrescription(sets: JsonArray, p: JsonElement?, step: Double = 2.5): JsonArray {
    val plan = p.asObj()
    if (plan == null) return sets
    val kind = plan.str("kind")
    if (kind == "off" || kind == "first") return sets
    val out = sets.map { sEl ->
        val source = sEl.asObj()
        // Never rewrite a logged set, and never rewrite a warm-up: the prescription speaks to the
        // work rows only.
        if (source == null || truthy(source["done"]) || isWarmupRow(sEl)) {
            sEl
        } else {
            var o = source
            if (plan["weight"].present()) o = o.with("w", plan["weight"])
            if (plan["reps"].present()) o = o.with("r", plan["reps"])
            if (plan["sec"].present()) o = o.with("sec", plan["sec"])
            o
        }
    }
    // Last, because the work rows now carry their final weight: the warm-up block ramps toward what
    // you are actually about to lift, not toward what you lifted last time.
    return rerampWarmups(JsonArray(out), step)
}

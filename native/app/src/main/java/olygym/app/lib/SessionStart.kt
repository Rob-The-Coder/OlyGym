package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.with

/*
 * How a session's exercise entries are built from one day of the dated-weeks plan — a port of
 * frontend/src/lib/session-start.js. Shared by the live start and by "log a past workout", which is
 * the same screen pointed at another day: both must walk up to identical entries, or the two paths
 * drift apart the first time a prescription rule changes.
 */

/**
 * A bare list of session entries. "Excluded from progression" is per-entry (`noProg`, written only
 * when true) rather than a wrapper flag on the session: a day planned as rehab excludes only its own
 * exercises. A day is atomic, so no entry remembers which routine it came from — there are none.
 */
fun buildSessionEntries(st: JsonObject?, day: JsonObject?): List<JsonObject> {
    // The prescription is applied as the session is built, so you walk up to the bar with the right
    // weight on the screen instead of being told about it afterwards. `plan` is kept on the entry
    // purely so the workout can explain the number it chose.
    val noProg = day?.bool("excludeFromProgression") == true
    return (day?.arr("ex") ?: JsonArray(emptyList()))
        .mapNotNull { it as? JsonObject }
        .map { cfg ->
            val plan = if (noProg) js("policy" to "off", "kind" to "off") else nextPrescription(st, cfg, day)
            // The warm-up ramp and the prescription snap to the exercise's own increment (1.25 kg
            // plates exist), not the unit default; a timed exercise's inc is seconds, so it keeps the
            // default for its optional load.
            val step = if (modeOf(cfg) == "reps") weightIncrement(cfg) else defaultIncrement(cfg.str("id"))
            val sets = applyPrescription(
                buildSets(st, cfg, js("step" to step, "useTarget" to (plan.str("kind") == "off"))),
                plan,
                step,
            )
            var target = JsonObject(cfg)
            plan["weight"]?.takeIf { it.present() }?.let { target = target.with("weight", it) }
            plan["reps"]?.takeIf { it.present() }?.let { target = target.with("reps", it) }
            plan["sec"]?.takeIf { it.present() }?.let { target = target.with("sec", it) }
            plan["sets"]?.takeIf { it.present() }?.let { target = target.with("sets", it) }
            js(
                "id" to cfg["id"],
                "sg" to cfg["sg"],
                "target" to target,
                "plan" to plan,
                "sets" to sets,
                "noProg" to if (noProg) true else null,
            )
        }
}

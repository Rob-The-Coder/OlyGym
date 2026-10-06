package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.truthy

/**
 * Presentation-only view of a progression result, ported from lib/progression-copy.js. The engine
 * stays the source of truth for both the outcome and its explanation; this only makes the policy
 * behind that result explicit on the workout screen.
 */
data class ProgressionGuidance(val policyLabel: String, val why: JsonArray)

/**
 * The step a rule would use: what the config asks for, or the dataset's own default. Ported from the
 * two helpers that live in sheets.jsx rather than in lib/progression.js, because only the config
 * sheet reads them.
 */
fun progressionStepOf(cfg: JsonObject?, mode: String?, exId: String?): Double {
    val asked = cfg?.num("inc")
    if (asked != null && asked >= 0) return asked
    return if (mode == "time") 5.0 else defaultIncrement(exId)
}

/** A rule with no progression needs no step; anything else needs a positive one. */
fun progressionStepIsValid(step: Double?, policy: String?): Boolean =
    policy == "off" || (step != null && !step.isNaN() && !step.isInfinite() && step > 0)

fun progressionGuidance(plan: JsonElement?): ProgressionGuidance? {
    val p = plan.asObj() ?: return null
    // A plan with no reason has nothing to say; note that the web tests this with !plan.why, where
    // an empty array is truthy — hence truthy() rather than a check for emptiness.
    if (!truthy(p["why"])) return null
    if (p.str("kind") == "off" || p.str("policy") == "off") return null
    val label = POLICY_NAME[p.str("policy")] ?: return null
    return ProgressionGuidance(label, p.arr("why"))
}

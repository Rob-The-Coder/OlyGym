package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.str
import olygym.app.data.truthy

/**
 * Presentation-only view of a progression result, ported from lib/progression-copy.js. The engine
 * stays the source of truth for both the outcome and its explanation; this only makes the policy
 * behind that result explicit on the workout screen.
 */
data class ProgressionGuidance(val policyLabel: String, val why: JsonArray)

fun progressionGuidance(plan: JsonElement?): ProgressionGuidance? {
    val p = plan.asObj() ?: return null
    // A plan with no reason has nothing to say; note that the web tests this with !plan.why, where
    // an empty array is truthy — hence truthy() rather than a check for emptiness.
    if (!truthy(p["why"])) return null
    if (p.str("kind") == "off" || p.str("policy") == "off") return null
    val label = POLICY_NAME[p.str("policy")] ?: return null
    return ProgressionGuidance(label, p.arr("why"))
}

package olygym.app.lib

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.obj
import olygym.app.data.str

/*
 * Per-exercise bar weight (issue: plate math for barbell work). Logged weights stay the TOTAL on
 * the bar — history and progression keep meaning exactly what they always did. The bar weight is
 * display metadata: it feeds the "X per side" plate math and nothing else.
 *
 * Unit: kilos. Weights are stored in kg, so the equipment defaults are the kg bars a real gym
 * racks (a 20 kg olympic bar), never a converted pound figure.
 *
 * A port of frontend/src/lib/bar.js. The JS module's EXIDX is data/Exercises.kt's Catalogue.
 */

/** Equipment values (e.eq) that put a bar in your hands. */
val BAR_EQ: Set<String> = setOf("barbell", "olympic barbell", "ez barbell", "smith machine", "trap bar")

/** Typical bar weights per equipment type, in kg. */
val DEFAULT_BAR_KG: Map<String, Double> = mapOf(
    "barbell" to 20.0,
    "olympic barbell" to 20.0,
    "ez barbell" to 10.0,
    "smith machine" to 9.0,
    "trap bar" to 25.0,
)

/** JS `typeof exOrId === 'string' ? EXIDX[exOrId] : exOrId`, read as an object. */
private fun exOf(exOrId: JsonElement?): JsonObject? {
    val id = exOrId.asStr()
    if (id != null) {
        val ex = Catalogue[id] ?: return null
        return js("id" to ex.id, "eq" to ex.eq)
    }
    return exOrId.asObj()
}

/** JS `S?.barWeights || {}`. */
private fun barWeights(S: JsonElement?): JsonObject = S.asObj()?.obj("barWeights") ?: JsonObject(emptyMap())

/** Whether this exercise (object or id) is done with a bar. */
fun usesBar(exOrId: JsonElement?): Boolean {
    val eq = exOf(exOrId)?.str("eq")
    return eq != null && BAR_EQ.contains(eq)
}

/** The default bar weight for an equipment type. null off the list. */
fun defaultBarWeight(eq: String?): Double? = DEFAULT_BAR_KG[eq]

/*
 * A stored 0 is "no bar", not "unset" (issue #138). Smith machines that counterbalance their
 * carriage put nothing in your hands, so the plate math has to start from the weight you logged,
 * not from a 9 kg bar that is not there. The key being absent is what means "use the default for
 * this bar type" — which is why the editor clears the key rather than writing a 0 when you ask
 * for the default back.
 */
fun isNoBar(S: JsonElement?, exId: String): Boolean = barWeights(S)[exId]?.asNum() == 0.0

/** True when the user has set their own bar weight for this exercise — including "no bar". */
fun hasBarOverride(S: JsonElement?, exId: String): Boolean {
    val own = barWeights(S)[exId]?.asNum() ?: return false
    return own >= 0.0
}

/*
 * Effective bar weight for one exercise, in kg: the explicit S.barWeights[exId] if set, else the
 * default for the bar type. null for anything that is not a bar exercise.
 */
fun barWeightFor(S: JsonElement?, exOrId: JsonElement?): Double? {
    val ex = exOf(exOrId) ?: return null
    val eq = ex.str("eq") ?: return null
    if (!BAR_EQ.contains(eq)) return null
    val own = ex.str("id")?.let { barWeights(S)[it] }?.asNum()
    if (own == 0.0) return 0.0
    if (own != null && own > 0.0) return own
    return defaultBarWeight(eq)
}

/*
 * Plates per side: (total - bar) / 2, rounded to 2 decimals. null when there is nothing sensible
 * to show — a missing number, or a total at or below the bar itself.
 */
fun plateSplit(total: Double?, bar: Double?): Double? {
    // `bar` of 0 is a real answer, not a missing one: with no bar every kilo you logged is on the
    // ends, so the split is simply half of it (issue #138).
    if (total == null || bar == null) return null
    if (!(total > 0.0)) return null
    if (!(bar >= 0.0)) return null
    if (total <= bar) return null
    return (((total - bar) / 2.0) * 100.0).roundToLong() / 100.0
}

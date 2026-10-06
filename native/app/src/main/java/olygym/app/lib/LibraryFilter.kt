package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Exercise
import olygym.app.data.asNum

/**
 * One pass of the Library's filters, in the order the screens have always applied them: body part,
 * then the search text, then whoever is allowed on this device (the active equipment profile, unless
 * the reader asked for everything), then the equipment filter. A port of
 * frontend/src/lib/library-filter.js.
 *
 * The equipment choice is dropped when the filters before it have narrowed past it, and the
 * effective value comes back as [LibraryResult.eq]: the caller renders the chip it actually has,
 * not the one it asked for. That is the fix for a search that used to leave you on a dead end with
 * no result and no way to tell why.
 */
data class LibraryResult(val list: List<Exercise>, val eqOpts: List<String>, val eq: String)

fun libraryResults(
    all: List<Exercise>,
    q: String = "",
    bp: String = "",
    eq: String = "",
    available: ((Exercise) -> Boolean)? = null,
): LibraryResult {
    val byPart = if (bp.isNotEmpty()) all.filter { it.bp == bp } else all
    val searched = searchExercises(byPart, q)
    val narrowed = if (available != null) searched.filter(available) else searched
    val eqOpts = equipmentOf(narrowed)
    val eqOn = if (eqOpts.contains(eq)) eq else ""
    return LibraryResult(
        list = if (eqOn.isNotEmpty()) narrowed.filter { it.eq == eqOn } else narrowed,
        eqOpts = eqOpts,
        eq = eqOn,
    )
}

/**
 * Every body part the catalogue uses, in the order it first appears — what the custom-exercise form
 * offers. The JS keeps this list in exercises.js (BODYPARTS); deriving it from the catalogue cannot
 * drift from what the picker will accept.
 */
fun bodyParts(all: List<Exercise>): List<String> =
    all.mapNotNull { it.bp?.takeIf { part -> part.isNotBlank() } }.distinct()

// The catalogue entry the way the muscle map reads it. Materialised once per exercise, because the
// explorer asks about eighteen muscles at a time and the map walk is not free.
private fun muscleWeights(ex: Exercise): JsonObject = musclesOf(exerciseJson(ex))

/**
 * What one exercise puts on one muscle: 1.0 when it is a primary target, 0.4 when it only assists,
 * 0 when it does not train it. This is the By-muscle explorer's own read.
 */
fun muscleWeightOf(ex: Exercise, slug: String): Double = muscleWeights(ex)[slug].asNum() ?: 0.0

/**
 * How many of `catalog` each muscle trains — the count on the By-muscle chips. Every muscle is a key,
 * so the strip can render a zero. A port of the count in frontend/src/components/MuscleExplorer.jsx.
 */
fun muscleCounts(catalog: List<Exercise>): Map<String, Int> {
    val weights = catalog.map { muscleWeights(it) }
    return MUSCLES.associateWith { slug -> weights.count { (it[slug].asNum() ?: 0.0) > 0.0 } }
}

/**
 * Every equipment value the catalogue uses, most common first — the JS's ALL_EQUIPMENT, which
 * Settings shows as a checklist.
 */
fun allEquipment(all: List<Exercise>): List<String> {
    val counts = linkedMapOf<String, Int>()
    all.forEach { ex -> ex.eq?.takeIf { it.isNotBlank() }?.let { counts[it] = (counts[it] ?: 0) + 1 } }
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
}

package olygym.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The persisted profile, as far as this phase reads it. The whole point is that the file is the
 * contract with the shipping React app: unknown keys are ignored rather than rejected, and every
 * field has a default, so a profile written by any version loads.
 *
 * The fields the plan list does not use — workouts, bodyweight, the settings the session engine
 * needs — are deliberately absent. They arrive with the screens that read them.
 */
@Serializable
data class Persisted(
    val unit: String = "kg",
    val weekStart: Int = 1,
    val lang: String = "en",
    val theme: String = "dark",
    val accent: String = "violet",
    val wdec: Int = 1,
    val weeks: List<Week> = emptyList(),
    /** The repeating plan that predates S.weeks; migrate-weeks.js reads it, and so does this. */
    val routines: List<Routine> = emptyList(),
    /** weekday -> routine ids, in the old shape. Read by the migration only. */
    val week: JsonObject? = null,
)

/** One concrete calendar week: no date arithmetic and no per-date override. */
@Serializable
data class Week(
    val id: String = "",
    val startIso: String = "",
    val name: String = "",
    val days: List<Day> = emptyList(),
)

/**
 * One session's plan. `dow` is a getDay() index — 1 Monday … 0 Sunday. Several days may share a
 * weekday; the first is the day, exactly as in the React app.
 */
@Serializable
data class Day(
    val dow: Int = 1,
    val name: String = "",
    /**
     * The prescribed exercises, kept as raw JSON on purpose: the shape is the file's, this phase
     * only reads a few fields out of it, and Phase 1 must be able to write the file back without
     * having dropped anything it did not model.
     */
    val ex: List<JsonObject> = emptyList(),
    /** Written only when true, so a day that is not excluded carries no key at all. */
    val excludeFromProgression: Boolean? = null,
)

/** A routine in the old repeating plan. Only the migration reads this shape. */
@Serializable
data class Routine(
    val id: String = "",
    val name: String = "",
    val ex: List<JsonObject> = emptyList(),
    val excludeFromProgression: Boolean? = null,
)

/** The scalar settings the shell needs before any screen exists. */
data class Settings(
    val unit: String,
    val weekStart: Int,
    val lang: String,
    val theme: String,
    val accent: String,
    val wdec: Int,
)

/**
 * Read a JSON field without committing to a type. The plan file stores sets, reps and weights as
 * numbers, but it also carries hand-edited and imported data, so a wrong type must read as absent
 * rather than throw away the whole profile.
 */
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

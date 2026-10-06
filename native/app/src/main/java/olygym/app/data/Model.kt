package olygym.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

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
    /** The weight goal, or null when there is none. Shown on Home's body-weight card. */
    val targetW: Double? = null,
    /** Whether Start opens the quick weigh-in first. Defaults on for a profile written before it. */
    val weighIn: Boolean? = null,
    /** The default rest between sets, in seconds. Zero means the profile turned the rest off. */
    val restSec: Int = 90,
    /** Whether ticking a set and the end of a rest make a sound. On unless it was turned off. */
    val sound: Boolean = true,
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

/** The day as the JSON the session builders read — the same shape the file holds. */
fun Day.toJsonObject(): JsonObject = Json.encodeToJsonElement(Day.serializer(), this) as JsonObject

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
    val targetW: Double?,
    val weighIn: Boolean,
    val restSec: Int,
    val sound: Boolean,
)

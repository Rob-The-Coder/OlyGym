package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.obj
import olygym.app.data.truthy

/** The lean default: the sets and one "more" button per exercise, and none of the old button rows. */
val WC_DEFAULT = mapOf(
    "steppers" to true,
    "pairButtons" to false,
    "exerciseButtons" to false,
)

/** Which optional control groups the session screen shows besides the sets themselves. */
data class WorkoutControls(val steppers: Boolean, val pairButtons: Boolean, val exerciseButtons: Boolean)

/**
 * A port of frontend/src/lib/workout-controls.js: the saved profile's choices over the lean default.
 *
 * The JS is a spread — `{...WC_DEFAULT, ...(S.wc || {})}` — and its call sites then test truthiness,
 * so an absent key takes the default and a stored null reads as off. That distinction is the whole
 * reason this is not a plain read with a fallback.
 */
fun workoutControls(S: JsonObject?): WorkoutControls {
    val wc = S.obj("wc")
    fun flag(key: String, fallback: Boolean): Boolean =
        if (wc != null && wc.containsKey(key)) truthy(wc[key]) else fallback
    return WorkoutControls(
        steppers = flag("steppers", true),
        pairButtons = flag("pairButtons", false),
        exerciseButtons = flag("exerciseButtons", false),
    )
}

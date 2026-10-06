package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.str

/*
 * How often an exercise is already in use, for the picker's "Chosen" view and its marker. Ported
 * from the two helpers at the top of the exercise-picker section in frontend/src/sheets.jsx.
 */

/** Every exercise in the plan — the days of every dated week, flattened. */
fun plannedEx(S: JsonObject?): List<JsonObject> {
    val planned = mutableListOf<JsonObject>()
    S.arr("weeks").forEach { week ->
        val days = week.asObj()?.arr("days") ?: JsonArray(emptyList())
        days.forEach { day ->
            val ex = day.asObj()?.arr("ex") ?: JsonArray(emptyList())
            ex.forEach { entry -> entry.asObj()?.let { planned += it } }
        }
    }
    return planned
}

/** Exercises already used in the plan or in a past workout: id to how many times. */
fun usageMap(S: JsonObject?): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    fun bump(id: String?) {
        if (id.isNullOrEmpty()) return
        counts[id] = (counts[id] ?: 0) + 1
    }
    plannedEx(S).forEach { bump(it.str("id")) }
    S.arr("workouts").forEach { workout ->
        val entries = workout.asObj()?.arr("entries") ?: JsonArray(emptyList())
        entries.forEach { entry -> bump(entry.asObj()?.str("id")) }
    }
    return counts
}

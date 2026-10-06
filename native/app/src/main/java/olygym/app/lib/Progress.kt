package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str

/*
 * The exercise-progress reading behind Stats' progress card — lifted out of frontend/src/views/Stats.jsx
 * so the series a chart draws is a value a test can pin. The drawing is Compose; this is the choice
 * of what the curve means.
 */

/** One exercise with a history, and what its latest session reads as. */
data class ExerciseReading(val id: String, val name: String, val mx: Double, val unit: String)

/** One session's point on a curve: when, the value, and the sets behind it. */
data class ProgressPoint(
    val t: Long,
    val y: Double,
    val d: String?,
    val sets: JsonArray,
    val target: JsonObject?,
)

data class ProgressSeries(
    val id: String,
    val mode: String,
    /** "s" for a hold, the profile's unit for a load, or the word "reps" for unloaded work. */
    val unit: String,
    val points: List<ProgressPoint>,
    val best: Double,
    /** True when nothing in this exercise's history was ever loaded, so its progress is its reps. */
    val repsOnly: Boolean,
)

/** The display name of an exercise that may no longer be in the catalogue: the entry's snapshot. */
fun progressNameOf(S: JsonObject, id: String): String {
    if (Catalogue[id] != null) return Catalogue.nameOf(id)
    val workouts = S.arr("workouts")
    for (i in workouts.indices.reversed()) {
        val entry = workouts[i].asObj()?.arr("entries")?.firstOrNull { it.asObj()?.str("id") == id }?.asObj()
        if (entry != null) {
            return entry["muscleSnapshot"].asObj()?.str("n") ?: entry.str("n") ?: id
        }
    }
    return id
}

/** The mode an exercise's latest session actually logged, or its catalogue mode. */
fun progressModeOf(S: JsonObject, id: String): String {
    val workouts = S.arr("workouts")
    for (i in workouts.indices.reversed()) {
        val entry = workouts[i].asObj()?.arr("entries")?.firstOrNull { it.asObj()?.str("id") == id }?.asObj()
        if (entry != null) {
            val mode = metricModeForEntry(entry)
            if (mode != null) return mode
        }
    }
    return modeOf(js("id" to id))
}

/** The figure one entry reads as, in one mode, or 0 when it logged nothing usable. */
private fun entryFigure(entry: JsonObject?, mode: String, repsOnly: Boolean = false): Double {
    if (entry == null) return 0.0
    val rows = metricRowsForEntry(entry, mode)
    if (mode == "reps") {
        return if (repsOnly) {
            rows.maxOfOrNull { it.asObj()?.num("r") ?: 0.0 } ?: 0.0
        } else {
            bestWeightForEntry(entry)
        }
    }
    return rows.maxOfOrNull { row ->
        val set = row.asObj()
        if (mode == "time") set?.num("sec") ?: 0.0 else set?.num("w") ?: 0.0
    } ?: 0.0
}

/** The entry of one exercise in one workout, or null. */
private fun entryIn(workout: JsonObject?, id: String): JsonObject? =
    workout?.arr("entries")?.firstOrNull { it.asObj()?.str("id") == id }?.asObj()
/**
 * Every exercise with a history, with the latest figure that exercise reads as, sorted by that
 * figure and then by name — the order the picker shows, so the biggest lift leads.
 */
fun progressExercises(S: JsonObject): List<ExerciseReading> {
    val workouts = S.arr("workouts")
    val unit = S.str("unit") ?: "kg"
    val ids = linkedSetOf<String>()
    workouts.forEach { w ->
        w.asObj()?.arr("entries")?.forEach { e -> e.asObj()?.str("id")?.let { ids.add(it) } }
    }
    return ids
        .filter { Catalogue[it] != null || progressNameOf(S, it) != it }
        .map { id ->
            val mode = progressModeOf(S, id)
            var mx = 0.0
            var mxUnit = if (mode == "time") "s" else unit
            for (i in workouts.indices.reversed()) {
                val entry = entryIn(workouts[i].asObj(), id) ?: continue
                val rows = metricRowsForEntry(entry, mode)
                val value = entryFigure(entry, mode)
                if (value > 0.0) {
                    mx = value
                    mxUnit = if (mode == "time") "s" else unit
                    break
                }
                // Unloaded reps work still has a figure — its rep count — or the picker label went
                // blank and the exercise sorted to the bottom as if it had no history.
                if (mode == "reps") {
                    val reps = rows.maxOfOrNull { it.asObj()?.num("r") ?: 0.0 } ?: 0.0
                    if (reps > 0.0) {
                        mx = reps
                        mxUnit = "reps"
                        break
                    }
                }
            }
            ExerciseReading(id, progressNameOf(S, id), mx, mxUnit)
        }
        .sortedWith(compareByDescending<ExerciseReading> { it.mx }.thenBy { it.name })
}

/**
 * One exercise's curve: the sessions that logged it in the mode being read, and the best of them.
 * A session that logged a different mode is skipped rather than mixed in — a hold and a load are not
 * the same reading, and the web is explicit about it. When nothing in the history was ever loaded,
 * the progress IS the rep count (issue #5: dropping every zero point left the card empty for
 * exercises with a full history behind them).
 */
fun progressSeries(S: JsonObject, id: String): ProgressSeries {
    val workouts = S.arr("workouts")
    val mode = progressModeOf(S, id)
    val unit = S.str("unit") ?: "kg"
    val repsOnly = mode == "reps" &&
        workouts.none { w -> bestWeightForEntry(entryIn(w.asObj(), id)) > 0.0 }
    val points = mutableListOf<ProgressPoint>()
    var best = 0.0
    workouts.forEach { w ->
        val workout = w.asObj() ?: return@forEach
        val entry = entryIn(workout, id) ?: return@forEach
        if (metricModeForEntry(entry) != mode) return@forEach
        val value = entryFigure(entry, mode, repsOnly)
        if (value > 0.0) {
            points.add(
                ProgressPoint(
                    t = (workout.num("start") ?: 0.0).toLong(),
                    y = value,
                    d = workout.str("d"),
                    sets = metricRowsForEntry(entry, mode),
                    target = entry["target"].asObj(),
                ),
            )
            best = maxOf(best, value)
        }
    }
    return ProgressSeries(
        id = id,
        mode = mode,
        unit = if (mode == "time") "s" else if (repsOnly) "reps" else unit,
        points = points,
        best = best,
        repsOnly = repsOnly,
    )
}

/** The workouts that happened on one date — what a heatmap cell's tap needs. */
fun workoutsOnDate(workouts: JsonElement?, iso: String): List<JsonObject> =
    (workouts as? JsonArray).orEmpty()
        .mapNotNull { it.asObj() }
        .filter { it.str("d") == iso }

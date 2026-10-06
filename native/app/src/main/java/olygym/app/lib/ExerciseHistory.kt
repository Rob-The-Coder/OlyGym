package olygym.app.lib

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str

/*
 * One exercise past, read back for the history sheet (issue #43): a chart series and the last few
 * sessions, derived in a single pass over the log so the sheet can read it once. A port of
 * frontend/src/lib/exercise-history.js.
 *
 * The chart plots ONE number per session, chosen the way Stats does it:
 *   reps   - the heaviest completed work set; an exercise never loaded (pull-ups, push-ups) plots
 *            its best rep count instead, because that is the thing improving
 *   time   - the longest completed hold, in seconds
 * The mode is the one the exercise was logged in most recently; a session logged in another mode
 * still appears in the list (its sets are labelled by their own target) but gets no point and no
 * value, so the curve never mixes seconds with kilos.
 *
 * Warm-up rows are excluded from every number here, as everywhere else.
 */

const val HISTORY_SESSIONS = 10

/** One logged session of an exercise, in the list the sheet shows. */
data class ExerciseSession(
    val id: String,
    val d: String,
    val t: Long,
    val mode: String,
    val target: JsonObject?,
    val sets: JsonArray,
    /** The plotted value, or null when this session was logged in another mode. */
    val value: Double?,
    /** Completed load x reps, reps sessions only: there is no honest tonnage for a hold. */
    val volume: Double?,
    val pr: Boolean,
)

/** One point of the curve, chronological. */
data class HistoryPoint(val t: Long, val d: String, val y: Double)

data class ExerciseHistory(
    val mode: String,
    /** weight, reps or sec: what the curve plots. */
    val metric: String,
    val best: Double,
    val prId: String?,
    val total: Int,
    val sessions: List<ExerciseSession>,
    val points: List<HistoryPoint>,
)

private class LoggedEntry(val w: JsonObject, val en: JsonObject, val mode: String, val rows: JsonArray)

/** When a workout happened: its own start, or noon on its date, as the JS falls back. */
private fun startOf(w: JsonObject): Long {
    val raw = w["start"]
    // Number.isFinite does not coerce: a numeric string is not a number to it, and falls through.
    if (raw is JsonPrimitive && !raw.isString) {
        val millis = raw.doubleOrNull
        if (millis != null && millis.isFinite()) return millis.toLong()
    }
    val day = w.str("d").orEmpty()
    return runCatching {
        LocalDate.parse(day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}

// Volume of the exercise in one session: completed load x reps, reps mode only.
private fun entryVolume(rows: JsonArray): Double = rows.sumOf { completedVolumeOf(it) }

fun exerciseHistory(S: JsonElement?, exId: String, limit: Int = HISTORY_SESSIONS): ExerciseHistory {
    val workouts = S.asObj()?.arr("workouts") ?: JsonArray(emptyList())
    // Chronological pairs of (workout, entry); the sort covers backfilled sessions, which are
    // inserted by date rather than appended.
    val logged = mutableListOf<LoggedEntry>()
    workouts.forEach { element ->
        val w = element.asObj() ?: return@forEach
        val en = w.arr("entries").firstOrNull { it.asObj()?.str("id") == exId }?.asObj() ?: return@forEach
        val mode = metricModeForEntry(en)
        if (mode.isNullOrEmpty()) return@forEach
        val rows = metricRowsForEntry(en, mode)
        if (rows.isNotEmpty()) logged.add(LoggedEntry(w, en, mode, rows))
    }
    logged.sortBy { startOf(it.w) }

    val fallbackMode = modeOf(js("id" to exId))
    if (logged.isEmpty()) {
        return ExerciseHistory(fallbackMode, "weight", 0.0, null, 0, emptyList(), emptyList())
    }

    val mode = logged.last().mode
    val repsOnly = mode == "reps" && logged.none { it.mode == "reps" && bestWeightForEntry(it.en) > 0.0 }
    val metric = when {
        mode == "time" -> "sec"
        repsOnly -> "reps"
        else -> "weight"
    }
    fun valueOf(item: LoggedEntry): Double {
        val top = when (metric) {
            "sec" -> item.rows.maxOfOrNull { it.asObj()?.num("sec") ?: 0.0 } ?: 0.0
            "reps" -> item.rows.maxOfOrNull { it.asObj()?.num("r") ?: 0.0 } ?: 0.0
            else -> bestWeightForEntry(item.en)
        }
        return maxOf(0.0, top)
    }

    var best = 0.0
    var prId: String? = null
    val sessions = mutableListOf<ExerciseSession>()
    val points = mutableListOf<HistoryPoint>()
    logged.forEach { item ->
        val same = item.mode == mode
        val value = if (same) valueOf(item) else null
        val t = startOf(item.w)
        val id = item.w.str("id").orEmpty()
        val day = item.w.str("d").orEmpty()
        // "PR" goes on the session that first reached the all-time best, not on every session that
        // later matched it - one marker says where the record was set.
        if (value != null && value > best) {
            best = value
            prId = id
        }
        if (value != null && value > 0.0) points.add(HistoryPoint(t, day, value))
        sessions.add(
            ExerciseSession(
                id = id,
                d = day,
                t = t,
                mode = item.mode,
                target = item.en.obj("target"),
                sets = item.rows,
                value = value,
                volume = if (item.mode == "reps") entryVolume(item.rows) else null,
                pr = false,
            ),
        )
    }
    // The "first reached" rule only holds for records above zero: a bodyweight session with no
    // weight logged is not a PR of anything.
    if (best <= 0.0) prId = null

    return ExerciseHistory(
        mode = mode,
        metric = metric,
        best = best,
        prId = prId,
        total = sessions.size,
        sessions = sessions.takeLast(limit).reversed().map { it.copy(pr = it.id == prId) },
        points = points,
    )
}

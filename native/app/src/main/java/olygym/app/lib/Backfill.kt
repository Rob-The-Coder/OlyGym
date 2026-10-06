package olygym.app.lib

import java.time.LocalDate
import java.util.Calendar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str

/*
 * Logging a workout after the fact — a port of frontend/src/lib/backfill.js. The session itself is
 * the ordinary active workout with a `backfill` field on it; these helpers are the parts that
 * differ from a live session, kept pure so the date arithmetic and the history surgery can be
 * tested without the UI.
 */

fun workoutsOn(S: JsonObject, iso: String): JsonArray =
    JsonArray(S.arr("workouts").filter { it.asObj()?.str("d") == iso })

/**
 * Epoch of `iso` at the given wall-clock time, in the device's zone — the same zone todayISO() and
 * isoOf() use, so `d` and `start` agree the way they do for a live session.
 */
fun backfillStart(iso: String, time: String? = "18:00"): Long {
    val parts = (if (time.isNullOrEmpty()) "18:00" else time).split(":").map { it.toDoubleOrNull() }
    val h = parts.getOrNull(0)?.let { if (it.isNaN() || it == 0.0) 0 else it.toInt() } ?: 0
    val m = parts.getOrNull(1)?.let { if (it.isNaN() || it == 0.0) 0 else it.toInt() } ?: 0
    val date = LocalDate.parse(iso)
    // The JS lands on local noon and then sets the wall-clock fields; Calendar does the same field
    // arithmetic, including the hour rollover setHours allows.
    val d = Calendar.getInstance()
    d.clear()
    d.set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
    d.set(Calendar.MILLISECOND, 0)
    d.set(Calendar.HOUR_OF_DAY, h)
    d.set(Calendar.MINUTE, m)
    d.set(Calendar.SECOND, 0)
    return d.timeInMillis
}

// A live session ends when you tap finish; a logged one ends when you said it did.
fun backfillEnd(active: JsonObject): Long {
    val minutes = active.obj("backfill")?.num("durationMin")?.takeIf { it != 0.0 } ?: 60.0
    return ((active.num("start") ?: 0.0) + maxOf(1.0, minutes) * 60000.0).toLong()
}

// The workouts array is chronological (History reverses it), so a past workout cannot just be
// pushed — it goes where its date and start time put it, after anything from the same moment.
fun insertChronological(workouts: JsonArray, w: JsonObject): JsonArray {
    fun key(x: JsonElement): Pair<String, Double> {
        val o = x.asObj()
        return (o?.str("d") ?: "") to (o?.num("start") ?: 0.0)
    }
    val (d, s) = key(w)
    var i = workouts.size
    while (i > 0) {
        val (pd, ps) = key(workouts[i - 1])
        if (pd < d || (pd == d && ps <= s)) break
        i--
    }
    return JsonArray(workouts.take(i) + w + workouts.drop(i))
}

// The history after a backfilled session is filed: the workout it replaces (if any) is gone and
// the new one sits in date order. Returns a new array; the caller stores it.
fun completeBackfill(workouts: JsonArray, active: JsonObject, w: JsonObject): JsonArray {
    val replaceId = active.obj("backfill")?.str("replaceId")
    val kept = if (!replaceId.isNullOrEmpty()) {
        JsonArray(workouts.filter { it.asObj()?.str("id") != replaceId })
    } else {
        workouts
    }
    return insertChronological(kept, w)
}

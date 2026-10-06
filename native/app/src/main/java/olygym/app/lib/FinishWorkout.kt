package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.obj
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.with

/*
 * The persisted boundary for a finished session — a port of frontend/src/lib/finish-workout.js.
 * Pure, so the exact shape the app writes can be tested without a screen mounting anything.
 */

/**
 * The workout record a finished session becomes. A key is written only when it has something to say,
 * so an untouched session is the same shape it always was.
 *
 * `snapshotFor` is the muscle snapshot the session screen collected for an entry — a callback,
 * because the muscle map is not this function's business.
 *
 * One difference from the JS: it writes `target: null` and `topW: null` explicitly and `js` drops
 * nulls, so those two keys are absent here. Every reader checks them loosely (`entry.target || entry`,
 * `bestWeightForEntry(entry) || null`), so a re-read cannot tell the difference.
 */
fun buildCompletedWorkout(
    active: JsonObject?,
    end: Long = System.currentTimeMillis(),
    prs: JsonArray = JsonArray(emptyList()),
    snapshotFor: ((JsonObject) -> JsonObject?)? = null,
): JsonObject {
    val entries = (active?.arr("entries") ?: JsonArray(emptyList()))
        .mapNotNull { it as? JsonObject }
        .map { entry ->
            val topW = bestWeightForEntry(entry).takeIf { it != 0.0 }
            val note = (entry.str("note") ?: "").trim()
            val snapshot = snapshotFor?.invoke(entry)?.takeIf { it.isNotEmpty() }
            var completed = js(
                "id" to entry["id"],
                "sets" to entry.arr("sets"),
                "topW" to topW,
                "target" to entry.obj("target"),
                // Written only when true, so a normal session keeps the shape it always had.
                "noProg" to if (entry.bool("noProg") == true) true else null,
                "muscleSnapshot" to snapshot,
                "note" to note.takeIf { it.isNotEmpty() },
            )
            if (note.isNotEmpty() && entry.bool("notePin") == true) completed = completed.with("notePin", true)
            completed
        }
        .filter { entry -> entry.arr("sets").any { hasCompletedWork(it) } }

    val sessionNote = (active?.str("note") ?: "").trim()
    // Legacy mirror, kept for older builds and external readers. It only makes sense when the whole
    // session is excluded, which is why a mixed session omits it.
    val allNoProg = entries.isNotEmpty() && entries.all { it.bool("noProg") == true }

    return js(
        "id" to active?.get("id"),
        "d" to active?.get("d"),
        "start" to active?.get("start"),
        "end" to end,
        // Where the session came from in the dated weeks; null for freestyle.
        "weekId" to active?.get("weekId")?.takeIf { it.present() },
        "dow" to active?.get("dow")?.takeIf { it.present() },
        "name" to active?.get("name"),
        "bw" to active?.get("bw"),
        "entries" to entries,
        "prs" to prs,
        "excludeFromProgression" to if (allNoProg) true else null,
        "note" to sessionNote.takeIf { it.isNotEmpty() },
    )
}

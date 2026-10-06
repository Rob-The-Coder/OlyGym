package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj

/*
 * Pure decisions for the active-workout superset flow — a port of
 * frontend/src/lib/supersetFlow.js. Keeping these independent of React and the stores makes the
 * uneven-round and re-check rules explicit and directly testable.
 */

private fun hasWork(entries: JsonArray, idx: Int?): Boolean {
    if (idx == null) return false
    val entry = entries.getOrNull(idx)?.asObj() ?: return false
    return entry.arr("sets").any { it.asObj()?.bool("done") != true }
}

/**
 * The rounds of a complex rendered as one table, or null when the unit cannot be logged that way.
 *
 * A complex is done as a sequence of movements without stopping, so one check has to mean "this
 * round of every movement is done". That only works when every member carries the same set
 * structure: the same number of rows, the same warm-up/work phase at every index, and reps mode
 * throughout. A coach's "3+3 @ 60kg" produces exactly that. Anything else — rows edited apart, a
 * timed hold in the mix — keeps the per-movement tables and their own checks, which is what the
 * screen did before this existed.
 *
 * Returns one `warmup` per round, in `sets` order, so the caller can draw the phase headings
 * from the same list it ticks.
 */
fun complexRounds(entries: JsonElement?, unit: JsonElement?): JsonArray? {
    val entriesArr = entries.asArr() ?: return null
    val unitArr = unit.asArr() ?: return null
    if (unitArr.size < 2) return null
    val first = unitArr[0].asInt()?.let { entriesArr.getOrNull(it) }
    val rows = first.asObj()?.arr("sets") ?: JsonArray(emptyList())
    if (rows.isEmpty()) return null
    val aligned = unitArr.all { idxEl ->
        val entry = idxEl.asInt()?.let { entriesArr.getOrNull(it) }
        val sets = entry.asObj()?.arr("sets") ?: JsonArray(emptyList())
        if (sets.size != rows.size) return@all false
        if (modeForEntry(entry, "reps") != "reps") return@all false
        sets.withIndex().all { (i, set) -> isWarmupRow(set) == isWarmupRow(rows[i]) }
    }
    return if (aligned) JsonArray(rows.map { js("warmup" to isWarmupRow(it)) }) else null
}

// Return the first unfinished navigation unit after the current one, wrapping once so a user
// who completed units out of order is never offered workout completion while earlier work remains.
fun nextUnfinishedUnit(entries: JsonElement?, units: JsonElement?, fromIdx: Int): JsonElement? {
    val entriesArr = entries.asArr() ?: return null
    val unitsArr = units.asArr() ?: return null
    if (unitsArr.isEmpty()) return null
    val current = unitsArr.indexOfFirst { it.asArr()?.any { idx -> idx.asInt() == fromIdx } == true }
    val ordered = if (current < 0) unitsArr.toList() else unitsArr.drop(current + 1) + unitsArr.take(current)
    return ordered.firstOrNull { unit ->
        unit.asArr()?.any { idx -> hasWork(entriesArr, idx.asInt()) } == true
    }
}

// The current exercise may be one member of a contiguous superset. Insert after that complete
// navigation unit; invalid/empty state safely falls back to the end of the entry list.
fun insertionIndexAfterCurrentUnit(units: JsonElement?, currentIndex: Int, entryCount: Int?): Int {
    val length = maxOf(0, entryCount ?: 0)
    val unitsArr = units.asArr() ?: return length
    if (unitsArr.isEmpty()) return length
    val unit = unitsArr.firstOrNull { it.asArr()?.any { idx -> idx.asInt() == currentIndex } == true }?.asArr()
    if (unit == null || unit.isEmpty()) return length
    val last = unit.mapNotNull { it.asInt() }.maxOrNull() ?: return length
    return minOf(length, last + 1)
}

// A completion is new progress only when it takes this exercise beyond the largest number of
// simultaneously completed sets seen in this mounted session. Uncheck/re-check therefore does
// not repeat navigation or rest side effects, while completing an added set still can.
fun setProgressHighWater(entry: JsonElement?, previous: Int = 0): JsonObject {
    val sets = entry.asObj()?.arr("sets") ?: JsonArray(emptyList())
    val done = sets.count { it.asObj()?.bool("done") == true }
    return js("isNew" to (done > previous), "highWater" to maxOf(previous, done))
}

/**
 * Whether completing a set should start a rest timer.
 *
 * A rest belongs after every completed set — the last set of an exercise included, because
 * another exercise follows it and you rest before that one too. The only set with nothing
 * left to time is the last set of the last exercise, where the session is over.
 *
 * Ordinary exercises used to "finish quietly" instead: an exercise started no rest on its
 * closing set, so a two-set exercise timed one rest instead of two (issue #3) and a rest
 * never carried across the gap into the next exercise. Supersets already did it this way.
 */
fun restAfterSet(unitDone: Boolean, lastUnit: Boolean): Boolean = !unitDone || !lastUnit

/**
 * Whether re-checking an already-completed set should start a rest.
 *
 * The high-water rule deliberately swallows a re-check so that unchecking and re-checking
 * finished work does not replay navigation or reopen sheets. But a re-check is still you
 * telling the app a set is done, and that is the other half of issue #3 — "after the first
 * set, sometimes a break doesn't appear". That is what it looks like when you uncheck a set
 * to correct the reps after its rest has already run out: nothing times the rest you are
 * actually about to take.
 *
 * So: fill a gap, never disturb a rest that is already counting down. A timer that is running
 * belongs to the set you finished most recently, which is a better answer than restarting it.
 */
fun restOnRecheck(timerRunning: Boolean, unitDone: Boolean, lastUnit: Boolean): Boolean =
    !timerRunning && restAfterSet(unitDone, lastUnit)

/**
 * How long the rest after a completed set should run, in seconds.
 *
 * An exercise may carry its own `restSec` in its target (issue #10) — a heavy triple and a set
 * of curls do not want the same break. One that carries none inherits `defaultRestSec`, the
 * global rest timer, which is what every routine did before the field existed.
 *
 * `unit` is the superset group the set belongs to, as entry indices — a plain exercise is a
 * group of one. A group rests once, after the round, so it takes the LONGEST rest any of its
 * members asked for: the shortest would send you back to the bar before the member that needs
 * the most recovery is ready.
 *
 * `defaultRestSec` of 0 is the rest timer turned off (v1.2.11). That silences the members that
 * have no rest of their own, but an exercise that explicitly asks for one still gets it — the
 * setting is a default, and this field overrides the default.
 */
fun restSecFor(entries: JsonElement?, unit: JsonElement?, defaultRestSec: Int?): Double {
    val fallback = if ((defaultRestSec ?: 0) > 0) (defaultRestSec ?: 0).toDouble() else 0.0
    val unitArr = unit.asArr()
    val idxs = if (unitArr != null && unitArr.isNotEmpty()) unitArr else JsonArray(emptyList())
    if (idxs.isEmpty()) return fallback
    return idxs.fold(0.0) { longest, idx ->
        val own = idx.asInt()?.let { entries.asArr()?.getOrNull(it) }?.asObj()?.obj("target")?.num("restSec")
        maxOf(longest, if (own != null && own > 0) own else fallback)
    }
}

/**
 * The rest a completed set earns when it was a warm-up ramp set.
 *
 * Ramp sets are light and short, so an exercise may carry its own `warmupRestSec` in its target
 * next to `restSec`. It applies between ramp sets only: the break after the LAST ramp set, into
 * the first work set, is `workRestSec` (the value restSecFor resolved), because that is the rest
 * the first heavy set actually needs. An exercise without the field, or a work set, rests
 * `workRestSec` — exactly what every routine did before the field existed.
 */
fun warmupRestSecFor(entry: JsonElement?, setIdx: Int, workRestSec: Int): Double {
    val sets = entry.asObj()?.arr("sets") ?: JsonArray(emptyList())
    val set = sets.getOrNull(setIdx) ?: return workRestSec.toDouble()
    if (!isWarmupRow(set)) return workRestSec.toDouble()
    val next = sets.drop(maxOf(0, setIdx + 1)).firstOrNull { it.asObj()?.bool("done") != true }
    if (next == null || !isWarmupRow(next)) return workRestSec.toDouble()
    val own = entry.asObj()?.obj("target")?.num("warmupRestSec")
    return if (own != null && own > 0) own else workRestSec.toDouble()
}

// Decide where a newly completed superset set goes next. Spent members are skipped, including
// across the wrap. A round ends when no later member in display order has work left; this makes
// the last *active* member the boundary rather than blindly using the group's last array index.
fun supersetFlowStep(entries: JsonElement?, unit: JsonElement?, fromIdx: Int): JsonObject? {
    val entriesArr = entries.asArr() ?: return null
    val unitArr = unit.asArr() ?: return null
    if (unitArr.size <= 1) return null
    val pos = unitArr.indexOfFirst { it.asInt() == fromIdx }
    if (pos < 0) return null

    val unitDone = unitArr.none { hasWork(entriesArr, it.asInt()) }
    if (unitDone) {
        return JsonObject(
            mapOf(
                "unitDone" to JsonPrimitive(true),
                "roundDone" to JsonPrimitive(false),
                "nextIdx" to JsonNull,
            ),
        )
    }

    val wrapped = unitArr.drop(pos + 1) + unitArr.take(pos + 1)
    val nextIdx = wrapped.firstOrNull { hasWork(entriesArr, it.asInt()) }
    val roundDone = !unitArr.drop(pos + 1).any { hasWork(entriesArr, it.asInt()) }
    return JsonObject(
        mapOf(
            "unitDone" to JsonPrimitive(false),
            "roundDone" to JsonPrimitive(roundDone),
            "nextIdx" to (nextIdx ?: JsonNull),
        ),
    )
}

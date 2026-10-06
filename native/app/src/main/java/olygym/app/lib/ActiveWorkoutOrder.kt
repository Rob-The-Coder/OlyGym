package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.with

/*
 * Reordering a superset as one unit inside the running session — a port of
 * frontend/src/lib/active-workout-order.js.
 */

/** The new order, as the original indices, and the session that carries it. */
data class MovedUnit(val indices: List<Int>, val active: JsonObject)

private data class MoveTarget(val units: List<List<Int>>, val source: Int, val target: Int)

/** history.js's supersetUnits hands the groups back as an array of index arrays. */
private fun unitsOf(entries: JsonArray): List<List<Int>> =
    supersetUnits(entries).map { unit -> unit.asArr().orEmpty().mapNotNull { it.asInt() } }

private fun moveTarget(active: JsonObject?, index: Int, direction: Int): MoveTarget? {
    if (active == null || (direction != -1 && direction != 1)) return null
    val entries = active["entries"] as? JsonArray ?: return null
    val units = unitsOf(entries)
    val source = units.indexOfFirst { it.contains(index) }
    if (source < 0) return null
    val target = source + direction
    if (target < 0 || target >= units.size) return null
    return MoveTarget(units, source, target)
}

fun canMoveActiveWorkoutUnit(active: JsonObject?, index: Int, direction: Int): Boolean =
    moveTarget(active, index, direction) != null

/**
 * Moves the unit the entry at `index` belongs to one place up or down, and puts `cur` back on the
 * entry the user was on. Returns null when the move is not possible.
 *
 * The JS mutates `active.entries` and `active.cur` in place and returns the new order; JSON is
 * immutable, so the new session is the return value and the caller stores it.
 */
fun moveActiveWorkoutUnit(active: JsonObject?, index: Int, direction: Int): MovedUnit? {
    val move = moveTarget(active, index, direction) ?: return null
    val entries = active!!["entries"] as? JsonArray ?: return null

    val selected = entries.getOrNull(index)
    val reordered = move.units.toMutableList()
    reordered[move.source] = move.units[move.target]
    reordered[move.target] = move.units[move.source]
    val indices = reordered.flatten()
    val movedEntries = indices.map { entries[it] }

    val cur = movedEntries.indexOf(selected).takeIf { it >= 0 } ?: 0
    return MovedUnit(indices, active.with("entries", movedEntries).with("cur", cur))
}

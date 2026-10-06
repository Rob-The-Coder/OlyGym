package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.bool
import olygym.app.data.str
import olygym.app.data.with

/*
 * Swapping one exact occurrence of an exercise in the running session — a port of
 * frontend/src/lib/active-exercise-swap.js.
 */

/** What a swap did, or what it needs before it can do anything. */
sealed interface SwapEvent {
    val index: Int

    /** The occurrence had nothing logged, so it was replaced in place. */
    data class Replaced(override val index: Int, val active: JsonObject) : SwapEvent

    /** The occurrence has logged sets. Nothing is relabelled without an explicit yes. */
    data class NeedsConfirmation(override val index: Int, val grouped: Boolean) : SwapEvent

    /** Confirmed: the replacement sits beside the original rather than on top of it. */
    data class Inserted(override val index: Int, val active: JsonObject) : SwapEvent
}

private val SWAPPED_KEYS = setOf("id", "target", "plan", "sets", "sg")

private fun hasLoggedSet(entry: JsonObject?): Boolean =
    entry != null && entry.arr("sets").any { (it as? JsonObject)?.bool("done") == true }

/**
 * Swap one exact active-workout occurrence.
 *
 * Unlogged occurrences are replaced in place. Logged results are never relabelled: after explicit
 * confirmation the replacement is inserted beside the original, and a logged group member also needs
 * an explicit choice to keep the replacement in the group or detach it after the group.
 *
 * The JS mutates the session and returns a small result object; JSON is immutable, so the new session
 * travels in the return value.
 */
fun swapActiveExercise(
    active: JsonObject?,
    index: Int,
    replacement: JsonObject?,
    loggedConfirmed: Boolean = false,
    groupDisposition: String? = null,
): SwapEvent? {
    if (active == null || replacement == null) return null
    val entries = active.arr("entries")
    if (index < 0 || index >= entries.size) return null

    val current = entries[index] as? JsonObject ?: return null
    val group = current.str("sg")

    if (!hasLoggedSet(current)) {
        // Everything the entry carried that is not the exercise itself survives the swap: the note
        // you wrote, the warmup ramp, whatever a later phase adds.
        val metadata = current.filterKeys { it !in SWAPPED_KEYS }
        val merged = metadata + replacement + if (group == null) emptyMap() else mapOf("sg" to current["sg"]!!)
        val next = active.with("entries", entries.mapIndexed { i, e -> if (i == index) JsonObject(merged) else e })
        return SwapEvent.Replaced(index, next.with("cur", index))
    }

    if (!loggedConfirmed) return SwapEvent.NeedsConfirmation(index, grouped = group != null)
    if (group != null && groupDisposition !in listOf("keep", "detach")) {
        return SwapEvent.NeedsConfirmation(index, grouped = true)
    }

    // A logged group member is inserted after the whole group unless the user says to keep it in.
    val keepGroup = group != null && groupDisposition == "keep"
    val unit = unitOf(supersetUnits(entries), index).asArr().orEmpty().mapNotNull { it.asInt() }
    val insertAt = if (keepGroup) index + 1 else if (unit.size > 1) unit.last() + 1 else index + 1

    val inserted = if (keepGroup) replacement.with("sg", current["sg"]) else replacement
    val nextEntries = JsonArray(entries.toMutableList().apply { add(insertAt, inserted) })
    return SwapEvent.Inserted(insertAt, active.with("entries", nextEntries).with("cur", insertAt))
}

package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.append
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.editAt
import olygym.app.data.js
import olygym.app.data.objectAt
import olygym.app.data.removeObjectAt
import olygym.app.data.str
import olygym.app.data.truthy
import olygym.app.data.with
import olygym.app.data.without

/*
 * Every write the plan editor makes — a port of the update(s => ...) bodies in
 * frontend/src/views/Plan.jsx, WeekEdit.jsx and the complex sheet.
 *
 * They are pure functions over the whole state object, not over the typed Week model: the file is
 * the contract with the shipping React app, and a week or a day carries keys this app does not
 * model (a week's customEx, anything a newer build writes). Round-tripping through a data class
 * would drop them, so the edits are made on the JSON itself through the combinators in data/Js.kt.
 *
 * Every function returns the document unchanged when the week, the day or the row it addresses is
 * gone: a screen that races a removal must not corrupt the profile.
 */

private fun weeksOf(raw: JsonObject): JsonArray = raw["weeks"] as? JsonArray ?: JsonArray(emptyList())

/** The week's days array as the file has it. */
private fun daysOf(week: JsonObject): JsonArray = week["days"] as? JsonArray ?: JsonArray(emptyList())

private fun exOf(day: JsonObject): JsonArray = day["ex"] as? JsonArray ?: JsonArray(emptyList())

/** The live exercise list of a day, or null — the web's locate(). */
private fun exAt(raw: JsonObject, weekId: String, dayIndex: Int): JsonArray? =
    weeksOf(raw).firstOrNull { it.asObj()?.str("id") == weekId }
        ?.asObj()?.let { daysOf(it) }?.objectAt(dayIndex)?.let { exOf(it) }

/** One week after f, or the document unchanged when there is no such week. */
private fun editWeek(raw: JsonObject, weekId: String, f: (JsonObject) -> JsonObject): JsonObject {
    var found = false
    val next = JsonArray(weeksOf(raw).map { element ->
        val week = element.asObj()
        if (week != null && week.str("id") == weekId) { found = true; f(week) } else element
    })
    return if (found) raw.with("weeks", next) else raw
}

private fun editDay(raw: JsonObject, weekId: String, index: Int, f: (JsonObject) -> JsonObject): JsonObject =
    editWeek(raw, weekId) { week -> week.with("days", daysOf(week).editAt(index, f)) }

private fun editEx(raw: JsonObject, weekId: String, dayIndex: Int, f: (JsonArray) -> JsonArray): JsonObject =
    editDay(raw, weekId, dayIndex) { day -> day.with("ex", f(exOf(day))) }

/* ---------------------------------------------------------------------------- weeks -- */

/** Append a ready-built week — the starter plan and the New week action both land here. */
fun appendWeek(raw: JsonObject, week: JsonObject): JsonObject = raw.with("weeks", weeksOf(raw).append(week))

fun addWeek(raw: JsonObject, id: String, startIso: String): JsonObject =
    appendWeek(raw, js("id" to id, "startIso" to startIso, "name" to "", "days" to emptyList<JsonObject>()))

fun deleteWeek(raw: JsonObject, id: String): JsonObject {
    val weeks = weeksOf(raw)
    if (weeks.none { it.asObj()?.str("id") == id }) return raw
    return raw.with("weeks", JsonArray(weeks.filterNot { it.asObj()?.str("id") == id }))
}

fun setWeekName(raw: JsonObject, id: String, name: String): JsonObject =
    editWeek(raw, id) { it.with("name", name) }

/* ----------------------------------------------------------------------------- days -- */

fun addDay(raw: JsonObject, weekId: String, dow: Int, name: String): JsonObject =
    editWeek(raw, weekId) { week ->
        week.with("days", daysOf(week).append(js("dow" to dow, "name" to name, "ex" to emptyList<JsonObject>())))
    }

fun deleteDay(raw: JsonObject, weekId: String, index: Int): JsonObject =
    editWeek(raw, weekId) { week -> week.with("days", daysOf(week).removeObjectAt(index)) }

fun setDayDow(raw: JsonObject, weekId: String, index: Int, dow: Int): JsonObject =
    editDay(raw, weekId, index) { it.with("dow", dow) }

fun setDayName(raw: JsonObject, weekId: String, index: Int, name: String): JsonObject =
    editDay(raw, weekId, index) { it.with("name", name) }

/* ------------------------------------------------------------------------ exercises -- */

/** Add a configured exercise: { id, ...cfg }, the web's spread of the config over the id. */
fun addExToDay(raw: JsonObject, weekId: String, dayIndex: Int, cfg: JsonObject): JsonObject =
    editEx(raw, weekId, dayIndex) { it.append(cfg) }

/**
 * Replace one row's config, keeping the row's own id and superset id — the web writes
 * { id: list[i].id, sg: list[i].sg, ...cfg }, so the config may override them.
 */
fun setExAt(raw: JsonObject, weekId: String, dayIndex: Int, index: Int, cfg: JsonObject): JsonObject =
    editEx(raw, weekId, dayIndex) { list ->
        list.editAt(index) { old -> JsonObject(js("id" to old["id"], "sg" to old["sg"]) + cfg) }
    }

fun removeExAt(raw: JsonObject, weekId: String, dayIndex: Int, index: Int): JsonObject =
    editEx(raw, weekId, dayIndex) { list -> cleanupSg(list.removeObjectAt(index)) }

/**
 * Link the row to the one above as a complex, or unlink it when the two already share a group.
 * A single-member group is what cleanupSg exists for, so the id it may leave behind is dropped.
 */
fun toggleLinkAt(raw: JsonObject, weekId: String, dayIndex: Int, index: Int): JsonObject =
    editEx(raw, weekId, dayIndex) { list ->
        val cur = list.objectAt(index)
        val prev = list.objectAt(index - 1)
        if (index < 1 || cur == null || prev == null) return@editEx list
        val curSg = cur["sg"]
        val prevSg = prev["sg"]
        val next = if (truthy(curSg) && truthy(prevSg) && curSg == prevSg) {
            list.editAt(index) { it.without("sg") }
        } else {
            val gid = prevSg.asStr()?.takeIf { it.isNotEmpty() } ?: ("sg" + uid())
            list.editAt(index - 1) { it.with("sg", gid) }.editAt(index) { it.with("sg", gid) }
        }
        cleanupSg(next)
    }

/**
 * Move the row's whole display unit one unit up or down. The guard runs on the live list first, the
 * way the screen does it: an activation that cannot move must not persist anything.
 */
fun moveUnitAt(raw: JsonObject, weekId: String, dayIndex: Int, index: Int, direction: Int): JsonObject {
    val live = exAt(raw, weekId, dayIndex) ?: return raw
    if (moveSupersetUnit(live, index, direction) == null) return raw
    return editEx(raw, weekId, dayIndex) { list ->
        val reordered = moveSupersetUnit(list, index, direction) ?: return@editEx list
        cleanupSg(reordered)
    }
}

/**
 * The complex's two shared numbers onto every member of the unit. Sets and load belong to the whole
 * complex — the coach writes "3+3 @ 30kg" once — while each row keeps its own reps.
 */
fun patchUnit(raw: JsonObject, weekId: String, dayIndex: Int, unitIndex: Int, patch: JsonObject): JsonObject =
    editEx(raw, weekId, dayIndex) { list ->
        val members = supersetUnits(list).getOrNull(unitIndex)?.asArr() ?: return@editEx list
        members.mapNotNull { it.asInt() }.fold(list) { acc, i -> acc.editAt(i) { JsonObject(it + patch) } }
    }

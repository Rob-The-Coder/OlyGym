package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.with

/*
 * Merging a plan. A port of the merging half of frontend/src/lib/plan-share.js: the printable page
 * and the sharing half are not here yet.
 *
 * The dated-weeks model (see lib/weeks.js) is what a plan is. A week that is already local and
 * trusted -- the coach's reviewed sheet -- lands in the plan through mergeWeek.
 */

// Kilos only. Missing unit is deliberately legacy-compatible: old files were read as already being
// in the recipient's unit, so keep their values unchanged.
private val PLAN_UNITS = setOf("kg")

fun planUnit(value: String?): String? = if (value != null && PLAN_UNITS.contains(value)) value else null

private fun currentMonday(): String = isoOf(startOfWeek(todayISO(), MONDAY))

private val ISO_DAY = Regex("""^\d{4}-\d{2}-\d{2}$""")

fun isIsoDay(value: String?): Boolean = value != null && ISO_DAY.matches(value)

/** A load written in sourceUnit as the same prescription in destinationUnit. */
fun convertedExercise(e: JsonObject, sourceUnit: String?, destinationUnit: String?): JsonObject {
    if (sourceUnit.isNullOrEmpty() || sourceUnit == destinationUnit) return e
    var out = e
    val weight = out["weight"]
    if (weight != null && weight !is JsonNull) {
        out = out.with("weight", convertWeight(weight, sourceUnit, destinationUnit))
    }
    // A timed increment is seconds, not a load. Rep-mode increments are load overrides.
    if (modeOf(out) == "reps" && (out.num("inc") ?: 0.0) > 0.0) {
        out = out.with("inc", convertWeight(out["inc"], sourceUnit, destinationUnit))
    }
    return out
}

// The muscles the map can draw.
private fun muscleList(value: JsonElement?): List<String> =
    inMuscleOrder(
        (value as? JsonArray).orEmpty().mapNotNull { it.asStr() }
            .filter { MUSCLES.contains(it) }
            .distinct(),
    )

/**
 * A custom exercise with its own metadata -- equipment, muscles, description -- in the shape
 * CustomExForm writes, minus the recipient-side custom/sm fields mergeWeek adds.
 */
private fun cleanCustom(c: JsonObject): JsonObject {
    var o = js("id" to c.str("id"), "n" to c.str("n"), "bp" to c.str("bp"))
    c.str("desc")?.takeIf { it.isNotEmpty() }?.let { o = o.with("desc", it) }
    c.str("eq")?.takeIf { it.isNotEmpty() }?.let { o = o.with("eq", it) }
    val prim = muscleList(c["primaries"])
    val sm = muscleList(c["secondaries"]).filter { !prim.contains(it) }
    // tg is the legacy single primary; the form keeps it equal to the first primary.
    val tg = prim.firstOrNull() ?: c.str("tg")?.takeIf { MUSCLES.contains(it) }.orEmpty()
    if (tg.isNotEmpty()) o = o.with("tg", tg)
    if (prim.isNotEmpty()) o = o.with("primaries", prim)
    if (sm.isNotEmpty()) o = o.with("secondaries", sm)
    if (prim.isNotEmpty() || sm.isNotEmpty()) o = o.with("muscleGroups", prim + sm)
    return o
}

/**
 * Reuse a custom you already have under the same name + body part, else add it fresh, and return
 * the old-id -> new-id map the exercises are remapped through. Stored exactly as the form would
 * have created it: custom true is what lets the recipient edit or delete it, and sm mirrors the
 * secondaries the way the form writes them.
 */
private fun addCustomEx(
    s: JsonObject,
    customs: List<JsonObject>,
): Pair<JsonObject, Map<String, String>> {
    val existing = s.arr("customEx").mapNotNull { it.asObj() }.toMutableList()
    val map = HashMap<String, String>()
    val added = mutableListOf<JsonObject>()
    customs.forEach { c ->
        val name = (c.str("n") ?: "").lowercase()
        val same = (existing + added).firstOrNull {
            (it.str("n") ?: "").lowercase() == name && it.str("bp") == c.str("bp")
        }
        val oldId = c.str("id")
        if (same != null) {
            if (oldId != null) map[oldId] = same.str("id").orEmpty()
            return@forEach
        }
        val nid = uid()
        if (oldId != null) map[oldId] = nid
        val clean = cleanCustom(c)
        var row = clean.with("id", nid)
        clean.arr("secondaries").takeIf { it.isNotEmpty() }?.let { row = row.with("sm", it) }
        added.add(row.with("custom", true))
    }
    return if (added.isEmpty()) s to map else s.with("customEx", existing + added) to map
}

/** Append one week to s, with a fresh id and every exercise pointed at the merged customs. */
private fun pushWeek(s: JsonObject, week: JsonObject, exIdMap: Map<String, String>): JsonObject {
    val days = week.arr("days").mapNotNull { element ->
        val day = element.asObj() ?: return@mapNotNull null
        val ex = day.arr("ex").mapNotNull { item ->
            val entry = item.asObj() ?: return@mapNotNull null
            val mapped = exIdMap[entry.str("id").orEmpty()]
            if (mapped != null) entry.with("id", mapped) else entry
        }
        var out = js("dow" to day.num("dow")?.toInt(), "name" to (day.str("name") ?: ""), "ex" to ex)
        if (day.bool("excludeFromProgression") == true) out = out.with("excludeFromProgression", true)
        out
    }
    val pushed = js(
        "id" to uid(),
        "startIso" to (week.str("startIso")?.takeIf { isIsoDay(it) } ?: currentMonday()),
        "name" to (week.str("name") ?: ""),
        "days" to days,
    )
    return s.with("weeks", s.arr("weeks") + pushed)
}

/**
 * Merge one already-local week (the coach's reviewed sheet) into state.
 *  - customs: reuse one you already have with the same name + body part, else add it fresh
 *  - the week: appended as a NEW week (fresh id) -- never overwrites yours
 *
 * A week built for this path carries its own customEx (see ImportPlan.kt): the id remap is what
 * keeps them from doubling.
 *
 * Null when the account is not in kilos. The web throws there (unitError); a screen that writes
 * through the store cannot catch a throw from inside the update, so the same signal comes back as
 * a value and the caller says so on screen.
 */
fun mergeWeek(s: JsonObject, week: JsonObject): JsonObject? {
    if (planUnit(s.str("unit") ?: "kg") == null) return null
    val (withCustoms, exIdMap) = addCustomEx(s, week.arr("customEx").mapNotNull { it.asObj() })
    return pushWeek(withCustoms, week, exIdMap)
}

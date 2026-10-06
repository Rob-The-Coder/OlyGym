package olygym.app.lib

import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.toJson
import olygym.app.data.with

/*
 * Converting a profile's stored weights from lb to kg, once, on the way in — a port of
 * frontend/src/lib/units.js.
 *
 * The app logs kilos only now. A profile that was written while it still offered pounds is
 * converted exactly once (see useStore's load path) and its unit pinned to 'kg', so an old
 * number is never silently relabelled. Rounded to what a gym can load: kg to the nearest
 * 0.25, body weight to 0.1.
 */

private const val LB_PER_KG = 2.2046226218

/** JS truthiness, private to this file (PORTING.md notes the duplication). */
private fun truthy(value: JsonElement?): Boolean = when (value) {
    null, is JsonNull -> false
    is JsonArray, is JsonObject -> true
    is JsonPrimitive -> when {
        value.isString -> value.content.isNotEmpty()
        else -> {
            val b = value.booleanOrNull
            if (b != null) b else {
                val d = value.doubleOrNull
                d != null && d != 0.0 && !d.isNaN()
            }
        }
    }
}

/** JS `Math.round`, ties toward +infinity. */
private fun jsRound(value: Double): Double = floor(value + 0.5)

/** JS `Number.isFinite(v)`: no coercion, so a numeric string is not a finite number here. */
private fun isFiniteNumber(value: JsonElement?): Boolean {
    val p = value as? JsonPrimitive ?: return false
    if (p.isString) return false
    return p.doubleOrNull?.isFinite() == true
}

fun convertWeight(value: JsonElement?, from: String?, to: String?): JsonElement? {
    if (from == to || !value.present() || value.asStr() == "") return value
    val v = value.asNum() ?: return value
    if (!v.isFinite()) return value
    return if (to == "lb") {
        JsonPrimitive(jsRound(v * LB_PER_KG * 2.0) / 2.0)
    } else {
        JsonPrimitive(jsRound(v / LB_PER_KG * 4.0) / 4.0)
    }
}

// Body weight is not loaded on a bar: the weigh-in sheet steps and stores it at 0.1, so plate
// rounding would move most weigh-ins on a kg -> lb -> kg round trip (QA C15).
fun convertBodyWeight(value: JsonElement?, from: String?, to: String?): JsonElement? {
    if (from == to || !value.present() || value.asStr() == "") return value
    val v = value.asNum() ?: return value
    if (!v.isFinite()) return value
    val converted = if (to == "lb") v * LB_PER_KG else v / LB_PER_KG
    return JsonPrimitive(jsRound(converted * 10.0) / 10.0)
}

private fun convSet(set: JsonElement?, from: String?, to: String?): JsonElement? {
    val obj = set.asObj()
    if (obj != null) {
        var out = obj
        if (out["w"].present()) out = out.with("w", convertWeight(out["w"], from, to))
        return out
    }
    // JS `typeof set === 'object'` admits an array, whose spread becomes an index-keyed object;
    // JsonObject is immutable, so that is built explicitly.
    if (set is JsonArray) return JsonObject(set.mapIndexed { i, v -> i.toString() to v }.toMap())
    return set
}

private fun convTarget(cfg: JsonElement?, from: String?, to: String?): JsonElement? {
    val obj = cfg.asObj()
    if (obj != null) {
        var out = obj
        if (out["weight"].present()) out = out.with("weight", convertWeight(out["weight"], from, to))
        // A per-exercise increment is a load too — 2.5 kg is 5 lb, not 2.5 lb.
        if ((out.num("inc") ?: 0.0) > 0.0 && (!out["mode"].present() || out.str("mode") == "reps")) {
            out = out.with("inc", convertWeight(out["inc"], from, to))
        }
        val warmup = out["warmup"].asArr()
        if (warmup != null) {
            out = out.with("warmup", warmup.map { w ->
                val wo = w.asObj()
                if (truthy(w) && wo != null && wo["weight"].present()) {
                    JsonObject(wo + ("weight" to convertWeight(wo["weight"], from, to).toJson()))
                } else {
                    w
                }
            })
        }
        return out
    }
    if (cfg is JsonArray) return JsonObject(cfg.mapIndexed { i, v -> i.toString() to v }.toMap())
    return cfg
}

private fun convEntry(e: JsonElement?, from: String?, to: String?): JsonElement? {
    val obj = e.asObj()
    if (obj != null) {
        var out = obj
        if (out["topW"].present()) out = out.with("topW", convertWeight(out["topW"], from, to))
        // JS `e.target ?` is truthiness: a falsy target (0, '', false) is left out of the spread.
        if (truthy(out["target"])) out = out.with("target", convTarget(out["target"], from, to))
        val sets = out["sets"].asArr()
        if (sets != null) out = out.with("sets", sets.map { convSet(it, from, to) })
        return out
    }
    if (e is JsonArray) return JsonObject(e.mapIndexed { i, v -> i.toString() to v }.toMap())
    return e
}

/** A new state object with every weight expressed in `to`, and `unit` set to it. */
fun convertStateUnit(S: JsonElement?, to: String?): JsonElement? {
    val source = S.asObj() ?: return S
    val from = source.str("unit")?.takeIf { it.isNotEmpty() } ?: "kg"
    if (from == to) return S

    // A session carries its body weight of the day and a cached total volume; History rows, the
    // detail header, the month calendar and the heatmap tooltips read those rather than summing
    // sets, so they must move with the sets. The volume is re-added from the converted sets.
    fun convSession(s: JsonObject): JsonObject {
        var out = s.with(
            "entries",
            (s["entries"].asArr() ?: JsonArray(emptyList())).map { convEntry(it, from, to) },
        )
        if (out["bw"].present()) out = out.with("bw", convertBodyWeight(out["bw"], from, to))
        if (isFiniteNumber(out["vol"])) {
            val entries = out.arr("entries").filter { it.asObj()?.get("sets").asArr() != null }
            out = out.with("vol", workoutVolume(js("entries" to entries)))
        }
        return out
    }

    var out = source.with("unit", to)
    val bodyweight = source["bodyweight"].asArr()
    if (bodyweight != null) {
        out = out.with("bodyweight", bodyweight.map { b ->
            val bo = b.asObj()
            if (bo != null) JsonObject(bo + ("w" to convertBodyWeight(bo["w"], from, to).toJson())) else b
        })
    }
    if (source["targetW"].present()) out = out.with("targetW", convertBodyWeight(source["targetW"], from, to))
    source.obj("exWeights")?.let { exWeights ->
        out = out.with("exWeights", JsonObject(exWeights.mapValues { (_, v) ->
            val vo = v.asObj()
            if (vo != null) JsonObject(vo + ("w" to convertWeight(vo["w"], from, to).toJson()))
            else convertWeight(v, from, to).toJson()
        }))
    }
    source.obj("barWeights")?.let { barWeights ->
        out = out.with(
            "barWeights",
            JsonObject(barWeights.mapValues { (_, v) -> convertWeight(v, from, to).toJson() }),
        )
    }
    val weeks = source["weeks"].asArr()
    if (weeks != null) {
        out = out.with("weeks", weeks.map { w ->
            val wo = w.asObj()
            if (wo == null) return@map w
            val days = wo["days"].asArr() ?: JsonArray(emptyList())
            val newDays = days.map { d ->
                val dobj = d.asObj()
                if (dobj == null) return@map d
                val ex = dobj["ex"].asArr() ?: JsonArray(emptyList())
                JsonObject(dobj + ("ex" to ex.map { cfg -> convTarget(cfg, from, to) }.toJson()))
            }
            JsonObject(wo + ("days" to newDays.toJson()))
        })
    }
    val workouts = source["workouts"].asArr()
    if (workouts != null) {
        out = out.with("workouts", workouts.map { convSession(it.asObj() ?: JsonObject(emptyMap())) })
    }
    if (truthy(source["active"])) {
        out = out.with("active", convSession(source.obj("active") ?: JsonObject(emptyMap())))
    }
    return out
}

package olygym.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * JavaScript-shaped reads and writes over JSON.
 *
 * The ported helpers in lib/ are ports of pure functions over plain JS objects, and the JS is
 * deliberately loose about them: Number(x) || 0, typeof x === 'string', x != null, delete x.sg.
 * Modelling those shapes as Kotlin data classes would mean choosing a type for every field and
 * quietly changing behaviour wherever the real file disagrees with the choice — the state file is
 * hand-editable, imported and older-than-this-build in equal measure — and it would re-serialize
 * every key the model did not carry. So the session shapes stay JsonObject, exactly as they arrive
 * from the file, and these functions carry the JS semantics instead.
 *
 * The one thing they do *not* reproduce is JS's absent-vs-null distinction: JsonNull and a missing
 * key both read as null here. Every site in the ported code that cared about it used a loose check
 * (`x != null`, which is false for undefined as well), so the two behave the same.
 */

/** JS `typeof v === 'object' && !Array.isArray(v)`: the object, or null. */
fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

/** JS `Array.isArray(v)`: the array, or null. */
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

/** JS `typeof v === 'string'`: the string, or null for anything else (numbers included). */
fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** JS `v === true`: only a real boolean true, never the string "true". */
fun JsonElement?.asBool(): Boolean? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** JS `Number(v)`, null where it gives NaN. An empty string is NaN here, not 0. */
fun JsonElement?.asNum(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return p.content.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    return p.booleanOrNull?.let { if (it) 1.0 else 0.0 } ?: p.doubleOrNull
}

/** JS `v != null`: the key exists and is not null. */
fun JsonElement?.present(): Boolean = this != null && this !is JsonNull

/** JS `Number(v) | 0` for an index or a count. */
fun JsonElement?.asInt(): Int? = asNum()?.toInt()

fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).asObj()
fun JsonObject?.arr(key: String): JsonArray = this?.get(key).asArr() ?: JsonArray(emptyList())
fun JsonObject?.str(key: String): String? = this?.get(key).asStr()
fun JsonObject?.num(key: String): Double? = this?.get(key).asNum()
fun JsonObject?.int(key: String): Int? = this?.get(key).asInt()
fun JsonObject?.bool(key: String): Boolean? = this?.get(key).asBool()

/** An object with one key replaced, or removed when the value is null — JS `o.k = v` / `delete o.k`. */
fun JsonObject.with(key: String, value: Any?): JsonObject =
    if (value == null) JsonObject(this - key) else JsonObject(this + (key to value.toJson()))

fun JsonObject.without(key: String): JsonObject = JsonObject(this - key)

/** Any Kotlin value as JSON, for building the shapes the app writes. */
fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this.toDouble())
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJson() })
    is Iterable<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(this.toString())
}

/**
 * A JSON object from pairs, dropping the null ones — which is how the JS builds these: a key is
 * written only when it has something to say (`...(cond ? { k: v } : {})`), never as a null.
 */
fun js(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.mapNotNull { (k, v) -> v?.let { k to it.toJson() } }.toMap())

/**
 * JS truthiness, `!!value`: false for null/undefined, false, 0, NaN and the empty string, true for
 * everything else (an empty array or object included). The state file is loosely typed, so a few
 * fields are read with `if (s.done)` rather than `s.done === true` — and the two are not the same
 * thing, which is why this is not a boolean cast.
 *
 * Folded in from four identical private copies in lib/ (see native/PORTING.md).
 */
fun truthy(value: JsonElement?): Boolean = when (value) {
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

/**
 * JS `String(x)` for a JSON value. The one thing that matters here is that an integral number keeps
 * its digits: JsonPrimitive(30.0).toString() is "30.0" where the web's template says "30", and
 * these strings are what the i18n arguments are filled from.
 *
 * ponytail: an object or an array comes out as its JSON text rather than the web's "[object Object]"
 * / "1,2". Nothing stores one as a message argument; this is the honest reading if something does.
 */
fun JsonElement?.jsText(): String = when (this) {
    null, is JsonNull -> ""
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull.toString()
        else -> doubleOrNull?.let { d ->
            if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
        } ?: content
    }
    else -> toString()
}

/* ---------------------------------------------------------------------------
 * The edit combinators.
 *
 * The web app's store update(mut) hands its callback a mutable clone of the state tree and a screen
 * writes straight into it: s.active.entries[i].sets[j].w = 62.5. JSON is immutable here, so the same
 * write is composed out of these instead. That is more verbose at the call site, which is the whole
 * cost — and nothing can be changed behind a helper's back.
 *
 * An index that is gone, or a child of the wrong shape, returns the document unchanged: a screen
 * that races a removal must not corrupt the profile.
 * ------------------------------------------------------------------------- */

/** The child object under key, after f; unchanged when the key is absent or is not an object. */
fun JsonObject.editObject(key: String, f: (JsonObject) -> JsonObject): JsonObject {
    val child = this[key].asObj() ?: return this
    return JsonObject(this + (key to f(child)))
}

/** The child array under key, after f; unchanged when the key is absent or is not an array. */
fun JsonObject.editArray(key: String, f: (JsonArray) -> JsonArray): JsonObject {
    val child = this[key] as? JsonArray ?: return this
    return JsonObject(this + (key to f(child)))
}

fun JsonArray.objectAt(i: Int): JsonObject? = getOrNull(i).asObj()

/** The object at i, after f. */
fun JsonArray.editAt(i: Int, f: (JsonObject) -> JsonObject): JsonArray {
    val child = getOrNull(i).asObj() ?: return this
    return JsonArray(toMutableList().also { it[i] = f(child) })
}

fun JsonArray.append(v: JsonObject): JsonArray = JsonArray(this + v)

fun JsonArray.insertAt(i: Int, v: JsonObject): JsonArray {
    val list = toMutableList()
    list.add(i.coerceIn(0, list.size), v)
    return JsonArray(list)
}

fun JsonArray.removeObjectAt(i: Int): JsonArray {
    if (i < 0 || i >= size) return this
    return JsonArray(toMutableList().also { it.removeAt(i) })
}

/** JS map over an array of objects, leaving anything that is not one alone. */
fun JsonArray.mapObjects(f: (JsonObject) -> JsonObject): JsonArray =
    JsonArray(map { it.asObj()?.let(f) ?: it })


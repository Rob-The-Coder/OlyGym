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

package olygym.app.lib

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.toJson

/*
 * Which muscles an exercise trains, and how hard — a port of
 * frontend/src/lib/muscles.js, the data behind every muscle map.
 *
 * The exercise dataset names muscles in free text and is not consistent about it:
 * "shoulders", "deltoids" and "delts" are the same thing, so are "quads" and
 * "quadriceps", "lats" and "latissimus dorsi", "core" and "abdominals". Nineteen
 * primary and forty secondary spellings collapse onto the eighteen muscles the body
 * map can actually draw, via ALIAS below. Anything genuinely undrawable (hands,
 * ankles, "cardiovascular system") maps to null and is dropped rather than guessed at.
 *
 * The JS reads plain objects, so an exercise here is a JsonObject: the catalogue's
 * EXIDX entry is materialised into one by catalogueJson, the way bar.js's exOf does,
 * while history entries are already JSON.
 */

// The muscles a map can shade, in head-to-toe order — also the order of any list
// built from them, so "what am I neglecting" reads top-down like a body.
val MUSCLES = listOf(
    "trapezius", "deltoids", "chest", "upper-back", "serratus",
    "biceps", "triceps", "forearm",
    "abs", "obliques", "lower-back",
    "gluteal", "quadriceps", "hamstring", "adductors", "hip-flexors",
    "calves", "tibialis",
)

// A picked list in the map's own order rather than the order the chips were tapped in — two
// people building the same exercise get the same exercise. Unknown names keep their place at the end.
fun inMuscleOrder(list: List<String>?): List<String> {
    fun at(m: String): Int {
        val i = MUSCLES.indexOf(m)
        return if (i < 0) MUSCLES.size else i
    }
    return (list ?: emptyList()).sortedBy { at(it) }
}

// Drawn as the silhouette, never shaded: they carry no training load.
val INERT = listOf("head", "hair", "neck", "hands", "feet", "knees", "ankles")

// English display names; these strings are the i18n keys (see lib/i18n.js). The packs know
// the cardio pseudo-muscle only under the dataset's own lowercase spelling, and every place
// that shows it capitalises with CSS — a capitalised key here rendered English everywhere.
val MUSCLE_NAME: Map<String, String> = linkedMapOf(
    "trapezius" to "Traps", "deltoids" to "Shoulders", "chest" to "Chest", "upper-back" to "Upper back",
    "serratus" to "Serratus", "biceps" to "Biceps", "triceps" to "Triceps", "forearm" to "Forearms",
    "abs" to "Abs", "obliques" to "Obliques", "lower-back" to "Lower back", "gluteal" to "Glutes",
    "quadriceps" to "Quads", "hamstring" to "Hamstrings", "adductors" to "Adductors",
    "hip-flexors" to "Hip flexors", "calves" to "Calves", "tibialis" to "Shins",
    "cardiovascular system" to "cardiovascular system",
)

// Every spelling that occurs in the dataset's `tg` and `sm` fields. null = not drawable.
private val ALIAS: Map<String, String?> = mapOf(
    // primaries
    "abs" to "abs", "pectorals" to "chest", "biceps" to "biceps", "glutes" to "gluteal", "delts" to "deltoids",
    "triceps" to "triceps", "upper back" to "upper-back", "lats" to "upper-back", "calves" to "calves",
    "quads" to "quadriceps", "forearms" to "forearm", "hamstrings" to "hamstring", "spine" to "lower-back",
    "traps" to "trapezius", "adductors" to "adductors", "serratus anterior" to "serratus",
    "abductors" to "gluteal", "levator scapulae" to "trapezius", "cardiovascular system" to "cardiovascular system",
    // secondaries
    "shoulders" to "deltoids", "deltoids" to "deltoids", "rear deltoids" to "deltoids",
    "rotator cuff" to "deltoids", "quadriceps" to "quadriceps", "core" to "abs", "abdominals" to "abs",
    "lower abs" to "abs", "chest" to "chest", "upper chest" to "chest", "hip flexors" to "hip-flexors",
    "obliques" to "obliques", "lower back" to "lower-back", "rhomboids" to "upper-back",
    "trapezius" to "trapezius", "back" to "upper-back", "latissimus dorsi" to "upper-back",
    "brachialis" to "biceps", "soleus" to "calves", "shins" to "tibialis", "wrists" to "forearm",
    "wrist flexors" to "forearm", "wrist extensors" to "forearm", "grip muscles" to "forearm",
    "groin" to "adductors", "inner thighs" to "adductors",
    "ankles" to null, "feet" to null, "hands" to null, "ankle stabilizers" to null,
    "sternocleidomastoid" to null,
)

// Custom exercises carry only a body part, so they fall back to it. Weights inside a
// group sum to 1 — "upper legs" spreads over three muscles rather than counting triple.
private val BY_BODYPART: Map<String, Map<String, Double>> = linkedMapOf(
    "chest" to linkedMapOf("chest" to 1.0),
    "back" to linkedMapOf("upper-back" to 0.75, "lower-back" to 0.25),
    "shoulders" to linkedMapOf("deltoids" to 1.0),
    "upper arms" to linkedMapOf("biceps" to 0.5, "triceps" to 0.5),
    "lower arms" to linkedMapOf("forearm" to 1.0),
    "waist" to linkedMapOf("abs" to 0.7, "obliques" to 0.3),
    "upper legs" to linkedMapOf("quadriceps" to 0.4, "hamstring" to 0.35, "gluteal" to 0.25),
    "lower legs" to linkedMapOf("calves" to 0.8, "tibialis" to 0.2),
    "neck" to linkedMapOf("trapezius" to 1.0),
    "full body" to linkedMapOf(
        "chest" to 0.2, "upper-back" to 0.2, "gluteal" to 0.2,
        "quadriceps" to 0.2, "hamstring" to 0.1, "abs" to 0.1,
    ),
    "cardio" to linkedMapOf(),
    // OlyGym catalogue (Catalyst Athletics import): bp carries the movement category rather
    // than an anatomical region, so entries without a specific tg/sm heuristic match fall back
    // to a category-level distribution reflecting that movement's typical demand.
    "Snatch" to linkedMapOf("trapezius" to 0.2, "quadriceps" to 0.25, "gluteal" to 0.25, "hamstring" to 0.15, "deltoids" to 0.15),
    "Clean" to linkedMapOf("trapezius" to 0.2, "quadriceps" to 0.25, "gluteal" to 0.25, "hamstring" to 0.15, "upper-back" to 0.15),
    "Jerk" to linkedMapOf("deltoids" to 0.35, "quadriceps" to 0.3, "triceps" to 0.2, "gluteal" to 0.15),
    "General Exercises" to linkedMapOf("quadriceps" to 0.35, "gluteal" to 0.3, "hamstring" to 0.25, "lower-back" to 0.1),
    "Trunk (Ab & Back)" to linkedMapOf("abs" to 0.45, "obliques" to 0.3, "lower-back" to 0.25),
    "Jumping & Plyometrics" to linkedMapOf("quadriceps" to 0.35, "gluteal" to 0.3, "hamstring" to 0.2, "calves" to 0.1, "tibialis" to 0.05),
    "Accessory - Lower/Whole Body" to linkedMapOf("quadriceps" to 0.28, "gluteal" to 0.28, "hamstring" to 0.22, "calves" to 0.12, "adductors" to 0.06, "tibialis" to 0.04),
    "Accessory - Prep & Prehab" to linkedMapOf("deltoids" to 0.26, "upper-back" to 0.2, "hip-flexors" to 0.16, "abs" to 0.2, "serratus" to 0.18),
    "Accessory - Upper Body" to linkedMapOf("chest" to 0.25, "upper-back" to 0.25, "deltoids" to 0.25, "biceps" to 0.125, "triceps" to 0.125),
    "Carries" to linkedMapOf("forearm" to 0.3, "trapezius" to 0.3, "abs" to 0.2, "gluteal" to 0.2),
)

private const val SECONDARY = 0.4   // a supporting muscle counts this much against a primary

// JS `Array.isArray(value) ? value : value == null || value === '' ? [] : [value]`.
private fun arrayOfValue(value: JsonElement?): List<JsonElement> = when {
    value is JsonArray -> value.toList()
    value == null || value is JsonNull -> emptyList()
    value is JsonPrimitive && value.isString && value.content == "" -> emptyList()
    else -> listOf(value)
}

// JS truthiness, which is not `asBool`: "0" and [] are true, 0 and "" are false.
private fun JsonElement?.truthy(): Boolean {
    if (this == null || this is JsonNull) return false
    if (this is JsonArray || this is JsonObject) return true
    val primitive = this as JsonPrimitive
    if (!primitive.isString) {
        primitive.booleanOrNull?.let { return it }
        val number = primitive.doubleOrNull
        if (number != null) return number != 0.0 && !number.isNaN()
        return true
    }
    return primitive.content.isNotEmpty()
}

// JS `String(value || '')`.
private fun jsString(value: JsonElement?): String {
    if (value == null || value is JsonNull) return ""
    val primitive = value as? JsonPrimitive ?: return ""
    if (!primitive.isString) {
        primitive.booleanOrNull?.let { if (!it) return "" }
        val number = primitive.doubleOrNull
        if (number != null && (number == 0.0 || number.isNaN())) return ""
    }
    return primitive.content
}

private fun canonicalMuscle(value: JsonElement?): String? {
    val name = jsString(value).lowercase().trim()
    if (MUSCLES.contains(name)) return name
    return ALIAS[name]
}

private fun canonicalUnique(values: Iterable<JsonElement?>): List<String> {
    val out = mutableListOf<String>()
    for (value in values) {
        val slug = canonicalMuscle(value)
        if (slug != null && !out.contains(slug)) out.add(slug)
    }
    return out
}

// exercises.js's smOf for a JSON object: a non-array sm wraps to one element, additions are
// empty today, and the Set drops duplicates while keeping order.
private fun smOfJson(ex: JsonObject?): List<JsonElement> {
    val sm = ex?.get("sm")
    val base = when {
        sm is JsonArray -> sm.toList()
        sm.truthy() -> listOf(sm!!)
        else -> emptyList()
    }
    return base.distinct()
}

// JS `Object.prototype.hasOwnProperty.call(object, key)`: the key exists, even as null.
private fun firstPresent(obj: JsonObject, keys: List<String>): JsonElement? {
    for (key in keys) {
        if (obj.containsKey(key)) return obj[key]
    }
    return null
}

// Completed history entries may retain a nested snapshot after their custom exercise is
// deleted from the profile catalogue. Prefer that snapshot when the outer entry has no muscle
// metadata of its own, while keeping direct/legacy entry fields authoritative when present.
private fun metadataOf(ex: JsonElement?): JsonElement? {
    val obj = ex as? JsonObject ?: return ex
    val directKeys = listOf(
        "muscleGroups", "muscles", "targetMuscles", "muscleWeights",
        "primaries", "primaryMuscles", "primary", "secondaries", "secondaryMuscles", "secondary",
    )
    val hasDirect = directKeys.any { obj.containsKey(it) } ||
        (listOf(obj["tg"], obj["mg"]) + arrayOfValue(obj["sm"])).any { it.present() && it.asStr() != "" }
    if (!hasDirect) {
        val snapshot = obj["muscleSnapshot"].asObj()
        if (snapshot != null) return snapshot
    }
    return obj
}

private fun explicitPartsOf(ex: JsonElement?): Pair<List<JsonElement>, List<JsonElement>>? {
    val obj = ex as? JsonObject ?: return null
    val primary = firstPresent(obj, listOf("primaries", "primaryMuscles", "primary"))
    val secondary = firstPresent(obj, listOf("secondaries", "secondaryMuscles", "secondary"))
    if (primary.present() || secondary.present()) {
        return arrayOfValue(primary) to arrayOfValue(secondary)
    }
    return null
}

private fun explicitGroupsOf(ex: JsonElement?): List<JsonElement>? {
    val obj = ex as? JsonObject ?: return null
    for (key in listOf("muscleGroups", "muscles", "targetMuscles")) {
        if (obj.containsKey(key)) {
            val groups = arrayOfValue(obj[key])
            return if (groups.isNotEmpty()) groups else null
        }
    }
    return null
}

// JS `EXIDX[id]`: the catalogue entry's muscle fields as the plain object muscles.js reads.
private fun catalogueJson(id: String?): JsonElement? {
    val exercise = id?.let { Catalogue[it] } ?: return null
    return js(
        "id" to exercise.id,
        "n" to exercise.n,
        "bp" to exercise.bp,
        "tg" to exercise.tg,
        "sm" to smOf(exercise),
    )
}

/** True only when the catalogue explicitly supplied muscle groups, not a body-part fallback. */
fun hasExplicitMuscleMetadata(entry: JsonElement?): Boolean {
    val ex = metadataOf(entry)
    if (ex !is JsonObject) return false
    val parts = explicitPartsOf(ex)
    if (parts != null && (parts.first + parts.second).any { canonicalMuscle(it) != null }) return true
    val groups = explicitGroupsOf(ex)
    if (groups != null && groups.any { canonicalMuscle(it) != null }) return true
    return (listOf(ex["tg"], ex["mg"]) + smOfJson(ex)).any { canonicalMuscle(it) != null }
}

/** Canonical unique muscle groups, accepting new primary/secondary arrays and legacy fields. */
fun muscleGroupsOf(entry: JsonElement?): List<String> {
    val ex = metadataOf(entry)
    val obj = ex as? JsonObject
    val parts = explicitPartsOf(ex)
    val explicit = explicitGroupsOf(ex)
    val useParts = parts != null && (parts.first + parts.second).any { canonicalMuscle(it) != null }
    val source: List<JsonElement?> = if (useParts) {
        val (primary, secondary) = parts
        primary + secondary
    } else {
        explicit ?: (listOf(obj?.get("tg"), obj?.get("mg")) + smOfJson(obj))
    }
    val out = canonicalUnique(source).toMutableList()
    if (out.isEmpty() && !useParts) {
        val bp = obj?.str("bp")
        val fallback = bp?.let { BY_BODYPART[it] } ?: emptyMap()
        canonicalUnique(fallback.keys.map { JsonPrimitive(it) }).forEach { out.add(it) }
    }
    return out
}

fun normalizeMuscleGroups(entry: JsonElement?): List<String> = muscleGroupsOf(entry)

/** True when any requested group matches; an empty request is an intentionally unfiltered query. */
fun matchesMuscleGroups(ex: JsonElement?, requested: JsonElement?): Boolean {
    val wanted = arrayOfValue(requested).mapNotNull { canonicalMuscle(it) }
    if (wanted.isEmpty()) return true
    val groups = muscleGroupsOf(ex).toSet()
    return wanted.any { groups.contains(it) }
}

/** Muscles one exercise trains: { slug: 0…1 }. Duplicate metadata never adds load twice. */
fun musclesOf(ex: JsonElement?): JsonObject {
    if (ex == null || ex is JsonNull) return JsonObject(emptyMap())
    val sourceEx = metadataOf(ex)
    if (sourceEx != ex) return musclesOf(sourceEx)
    val obj = ex as? JsonObject ?: return JsonObject(emptyMap())
    val muscleWeights = obj["muscleWeights"].asObj()
    if (muscleWeights != null) {
        val snapshot = linkedMapOf<String, JsonElement>()
        MUSCLES.forEach { slug ->
            val weight = muscleWeights[slug].asNum()
            if (weight != null && weight.isFinite() && weight > 0.0) snapshot[slug] = JsonPrimitive(weight)
        }
        if (snapshot.isNotEmpty()) return JsonObject(snapshot)
    }
    val out = linkedMapOf<String, JsonElement>()
    fun add(name: JsonElement?, w: Double) {
        val slug = canonicalMuscle(name) ?: return
        val previous = out[slug]?.asNum() ?: 0.0
        out[slug] = JsonPrimitive(maxOf(previous, w))
    }
    val parts = explicitPartsOf(ex)
    val explicit = explicitGroupsOf(ex)
    val useParts = parts != null && (parts.first + parts.second).any { canonicalMuscle(it) != null }
    if (useParts) {
        val (primary, secondary) = parts
        primary.forEach { add(it, 1.0) }
        secondary.forEach { add(it, SECONDARY) }
    } else if (explicit != null) {
        explicit.forEach { add(it, 1.0) }
    } else {
        add(obj["tg"], 1.0)
        add(obj["mg"], SECONDARY)
        smOfJson(obj).forEach { add(it, SECONDARY) }
    }
    // Nothing recognised (custom exercises, or a target we don't draw) — use the body part.
    if (out.isEmpty()) {
        val bp = obj.str("bp")
        bp?.let { BY_BODYPART[it] }?.forEach { (slug, weight) -> out[slug] = JsonPrimitive(weight) }
    }
    return JsonObject(out)
}

/** Snapshot display and weighted muscle metadata into a completed history entry. */
fun exerciseMuscleSnapshot(ex: JsonElement?): JsonObject {
    if (ex !is JsonObject) return JsonObject(emptyMap())
    val out = linkedMapOf<String, JsonElement>()
    ex["n"]?.takeIf { it.present() }?.let { out["n"] = it }
    ex["bp"]?.takeIf { it.present() }?.let { out["bp"] = it }
    val weights = musclesOf(ex)
    if (weights.isNotEmpty()) out["muscleWeights"] = weights
    val parts = explicitPartsOf(ex)
    if (parts != null) {
        val primaries = canonicalUnique(parts.first)
        val secondaries = canonicalUnique(parts.second).filter { !primaries.contains(it) }
        if (primaries.isNotEmpty()) out["primaries"] = primaries.toJson()
        if (secondaries.isNotEmpty()) out["secondaries"] = secondaries.toJson()
    }
    if (hasExplicitMuscleMetadata(ex)) out["muscleGroups"] = muscleGroupsOf(ex).toJson()
    return JsonObject(out)
}

/**
 * Training load per muscle, in "effective sets".
 * `items` is [{ id, sets }] — sets being a count, so a 4×8 bench press weighs four
 * times a single set. Volume in kg is deliberately not used: 100 kg of leg press
 * against 12 kg of lateral raise says nothing about which muscle worked harder.
 */
fun loadOf(items: JsonElement?): JsonObject {
    val load = linkedMapOf<String, JsonElement>()
    (items as? JsonArray)?.forEach { element ->
        val item = element.asObj() ?: return@forEach
        val sets = item["sets"].asNum() ?: 0.0
        if (sets == 0.0) return@forEach
        val historical = listOf(item["ex"], item["exercise"]).firstOrNull { it.truthy() }
        val hasHistoricalWeights = historical.asObj()?.get("muscleWeights").truthy() == true
        val source = if (hasHistoricalWeights) historical
        else catalogueJson(item.str("id")) ?: historical ?: item
        val weights = musclesOf(source)
        for ((slug, weight) in weights) {
            val total = (load[slug]?.asNum() ?: 0.0) + (weight.asNum() ?: 0.0) * sets
            load[slug] = JsonPrimitive(total)
        }
    }
    return JsonObject(load)
}

/**
 * Load for finished workouts (only sets actually ticked off count). `pick` narrows that
 * further — the map can then answer "where did the *hard* sets go", which is a different
 * question from where the sets went: a muscle can lead on volume and still never be trained
 * near failure.
 */
fun loadOfWorkouts(workouts: JsonElement?, pick: ((JsonElement) -> Boolean)? = null): JsonObject {
    val items = mutableListOf<JsonElement>()
    ((workouts as? JsonArray) ?: JsonArray(emptyList())).forEach { workoutElement ->
        val workout = workoutElement.asObj() ?: return@forEach
        workout.arr("entries").forEach { entryElement ->
            val entry = entryElement.asObj() ?: return@forEach
            val sets = entry.arr("sets").count { set ->
                set.asObj()?.get("done").truthy() == true && !isWarmupRow(set) && (pick == null || pick(set))
            }
            val exercise = if (entry["exercise"].truthy()) entry["exercise"] else entry
            items.add(js("id" to entry["id"], "ex" to exercise, "sets" to sets))
        }
    }
    return loadOf(JsonArray(items))
}

/**
 * Workouts in one existing Muscle balance range, with time injected for deterministic tests.
 *
 * The 7-day range is "this week", not "the last seven days", so it moves with the profile's
 * first weekday — the caller passes it since this takes workouts rather than the whole state.
 */
fun muscleBalanceWindow(
    workouts: JsonElement?,
    win: Int,
    now: Double = System.currentTimeMillis().toDouble(),
    today: String = todayISO(),
    ws: Int = MONDAY,
): JsonArray {
    val all = (workouts as? JsonArray) ?: JsonArray(emptyList())
    return JsonArray(
        all.filter { element ->
            val workout = element.asObj()
            when (win) {
                0 -> true
                7 -> weekKey(workout?.str("d") ?: "", ws) == weekKey(today, ws)
                else -> {
                    val d = workout?.str("d")
                    val rawStart = workout?.get("start")
                    val start = if (rawStart.truthy()) rawStart.asNum() ?: dateMillis(d) else dateMillis(d)
                    start > now - win.toDouble() * 86400000.0
                }
            }
        },
    )
}

/** JS `new Date(iso).getTime()` for an ISO yyyy-MM-dd date, which is UTC midnight. */
private fun dateMillis(iso: String?): Double {
    if (iso.isNullOrEmpty()) return Double.NaN
    return runCatching {
        LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toDouble()
    }.getOrDefault(Double.NaN)
}

/** Load a routine *would* produce, from its planned set counts. */
fun loadOfRoutine(routine: JsonElement?): JsonObject {
    val exercises = routine.asObj()?.arr("ex") ?: JsonArray(emptyList())
    val items = exercises.map { element ->
        val c = element.asObj() ?: JsonObject(emptyMap())
        val sets = c["sets"].asNum()?.takeIf { it != 0.0 } ?: 1.0
        js("id" to c["id"], "ex" to element, "sets" to sets)
    }
    return loadOf(JsonArray(items))
}

/** Load for a workout still in progress — the sets ticked so far. */
fun loadOfActive(active: JsonElement?): JsonObject {
    val entries = active.asObj()?.arr("entries") ?: JsonArray(emptyList())
    val items = entries.map { element ->
        val entry = element.asObj() ?: JsonObject(emptyMap())
        val sets = entry.arr("sets").count { set ->
            set.asObj()?.get("done").truthy() == true && !isWarmupRow(set)
        }
        val exercise = if (entry["exercise"].truthy()) entry["exercise"] else element
        js("id" to entry["id"], "ex" to exercise, "sets" to sets)
    }
    return loadOf(JsonArray(items))
}

/**
 * Shade buckets 0–4 per muscle.
 *
 * With no `thresholds`, levels remain relative to the hardest-worked muscle in the same
 * window. This is the original balance-map behavior. When `thresholds` is supplied, levels
 * use an absolute scale instead: it is an ordered array of `{ at, level, exclusive? }` rules,
 * and the last matching rule wins. An exclusive rule matches values strictly greater than
 * `at`; otherwise the boundary is inclusive. Values below the first matching rule are l0.
 * Absolute rules let recovery views keep fixed semantic bands instead of renormalizing to the
 * strongest muscle on screen.
 */
fun levelsOf(load: JsonElement?, thresholds: JsonElement? = null): JsonObject {
    val levels = linkedMapOf<String, JsonElement>()
    if (thresholds.truthy()) {
        val rules = (thresholds as? JsonArray)?.toList() ?: emptyList()
        MUSCLES.forEach { slug ->
            val value = loadValue(load, slug)
            var level = 0
            rules.forEach { ruleElement ->
                val rule = ruleElement.asObj()
                val at = rule?.num("at") ?: 0.0
                val matches = if (rule?.get("exclusive").truthy() == true) value > at else value >= at
                if (matches) level = maxOf(0, minOf(4, (rule?.num("level") ?: 0.0).toInt()))
            }
            levels[slug] = JsonPrimitive(level)
        }
        return JsonObject(levels)
    }
    val max = maxOf(0.0, MUSCLES.maxOfOrNull { loadValue(load, it) } ?: 0.0)
    MUSCLES.forEach { slug ->
        val value = loadValue(load, slug)
        val level = if (value == 0.0 || max <= 0.0) 0
        else maxOf(1, minOf(4, ceil(value / max * 4.0).toInt()))
        levels[slug] = JsonPrimitive(level)
    }
    return JsonObject(levels)
}

private fun loadValue(load: JsonElement?, slug: String): Double =
    (load as? JsonObject)?.get(slug).asNum() ?: 0.0

/** Muscles sorted hardest-worked first; untrained ones last, in body order. */
fun rankOf(load: JsonElement?): JsonObject {
    fun value(slug: String): Double = loadValue(load, slug)
    val worked = MUSCLES.filter { value(it) > 0.0 }
        .sortedWith(compareByDescending<String> { value(it) }.thenBy { MUSCLES.indexOf(it) })
    val missed = MUSCLES.filter { !(value(it) > 0.0) }
    return js("worked" to worked, "missed" to missed)
}

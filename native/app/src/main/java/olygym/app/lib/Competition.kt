package olygym.app.lib

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.arr
import olygym.app.data.asBool
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.jsText
import olygym.app.data.truthy

/*
 * Competition (meet) helpers — a port of frontend/src/lib/competition.js.
 *
 * A competition is not a training session. It is a dated event with a bodyweight category and
 * up to three snatch and three clean & jerk attempts, each judged good or no lift, and a total
 * that only exists once something was made in both lifts. It is kept apart from the training log
 * on purpose: a competition lift is not a workout set, and folding the two together would make
 * every training curve answer a question it was never asked.
 *
 * Everything here is pure so the decisions (what a total is, which meet is next) are verifiable.
 */

const val MAX_ATTEMPTS = 3

// The categories a federation runs are the federation's, and they change every few years —
// this is only the list the app starts from. They live in S.classes so they can be edited
// without a code change (Settings → Competition), and a logged meet keeps the string it was
// saved with, so a new set of categories never relabels an old result.
val DEFAULT_CLASSES: JsonObject = js(
    "male" to listOf("60", "65", "70", "75", "85", "95", "110", "110+"),
    "female" to emptyList<String>(),
)

data class ClassLists(val male: List<String>, val female: List<String>)
data class ClassOption(val value: String, val label: String)

private fun defaultMale(): List<String> =
    (DEFAULT_CLASSES["male"] as? JsonArray)?.map { it.jsText() } ?: emptyList()

/** The profile's two lists, normalised. A profile from before the split carries one flat list,
 *  which reads as the male one; a missing shape falls back to the shipped default. */
fun classLists(classes: JsonElement?): ClassLists {
    val flat = classes as? JsonArray
    if (flat != null) return ClassLists(cleanClasses(flat), emptyList())
    val obj = classes as? JsonObject
    val male = (obj?.get("male") as? JsonArray)?.map { it.jsText() } ?: defaultMale()
    val female = (obj?.get("female") as? JsonArray)?.map { it.jsText() } ?: emptyList()
    return ClassLists(male, female)
}

/** The categories the current body competes in. */
fun listFor(S: JsonObject?): List<String> {
    val lists = classLists(S?.get("classes"))
    return if (S?.get("body").asStr() == "female") lists.female else lists.male
}

/** The category choices for a SelectRow, each already labelled. */
fun classOptions(list: List<String>?): List<ClassOption> =
    (list ?: emptyList()).map { ClassOption(it, "$it kg") }

/** The list as saved: trimmed, empty entries dropped, duplicates removed, order kept. */
fun cleanClasses(list: List<JsonElement?>?): List<String> {
    val out = mutableListOf<String>()
    for (raw in list ?: emptyList()) {
        val c = raw.jsText().trim()
        if (c.isNotEmpty() && !out.contains(c)) out.add(c)
    }
    return out
}

// An attempt counts only when it was both made and carried a weight. A no-lift opener that was
// heavier than the made attempt must never leak into a best (the classic "opened at 120, missed,
// made 115" case).
fun attemptMade(a: JsonElement?): Boolean {
    if (!truthy(a)) return false
    val obj = a.asObj() ?: return false
    if (!truthy(obj["made"])) return false
    val w = obj["w"].asNum() ?: return false
    return w > 0.0
}

/** Heaviest made attempt, or null when the lift had none. */
fun bestAttempt(attempts: JsonElement?): Double? {
    var best: Double? = null
    for (a in (attempts as? JsonArray) ?: JsonArray(emptyList())) {
        if (!attemptMade(a)) continue
        val w = a.asObj()?.get("w").asNum() ?: continue
        best = if (best == null) w else maxOf(best, w)
    }
    return best
}

/** Best snatch + best clean & jerk, or null unless something was made in both lifts. */
fun totalOf(meet: JsonElement?): Double? {
    val s = bestAttempt(meet.asObj()?.get("snatch"))
    val c = bestAttempt(meet.asObj()?.get("cj"))
    return if (s != null && c != null) Math.round((s + c) * 100) / 100.0 else null
}

fun hasResult(meet: JsonElement?): Boolean = totalOf(meet) != null

fun hasAttempts(meet: JsonElement?): Boolean {
    val obj = meet.asObj()
    return obj.arr("snatch").size + obj.arr("cj").size > 0
}

// The JS compares the two date strings with localeCompare; for the stored ISO dates that is a
// plain string compare, and sortedBy is stable the way Array.prototype.sort is.
private fun dateOf(m: JsonObject): String {
    val d = m["d"]
    return if (truthy(d)) d.jsText() else ""
}

/** Every meet, oldest first. */
fun sortedMeets(list: JsonElement?): List<JsonObject> =
    ((list as? JsonArray) ?: JsonArray(emptyList()))
        .mapNotNull { it.asObj() }
        .sortedBy { dateOf(it) }

/** Meets dated today or later, soonest first. */
fun upcomingMeets(list: JsonElement?, today: String?): List<JsonObject> =
    sortedMeets(list).filter { dateOf(it) >= (today ?: "") }

/** Meets already gone, most recent first. */
fun pastMeets(list: JsonElement?, today: String?): List<JsonObject> =
    sortedMeets(list).filter { dateOf(it) < (today ?: "") }.reversed()

/** The soonest meet still ahead, or null. */
fun nextMeet(list: JsonElement?, today: String?): JsonObject? =
    upcomingMeets(list, today).firstOrNull()

/** Whole days from `today` to the meet; negative once it is behind you, null when unreadable. */
fun daysUntil(meet: JsonElement?, today: String?): Int? {
    // Noon local, so a DST change cannot round the count off by a day. java.time has no such
    // problem, so the two dates are read as LocalDate and the difference is exact.
    val d = meet.asObj()?.get("d")
    if (!truthy(d)) return null
    if (today == null) return null
    val a = runCatching { LocalDate.parse(d.jsText()) }.getOrNull() ?: return null
    val b = runCatching { LocalDate.parse(today) }.getOrNull() ?: return null
    return ChronoUnit.DAYS.between(b, a).toInt()
}

/** The best made snatch, clean & jerk and total across every meet, each null when never done. */
data class CompetitionBests(val snatch: Double?, val cj: Double?, val total: Double?)

fun competitionBests(meets: JsonElement?): CompetitionBests {
    var snatch: Double? = null
    var cj: Double? = null
    var total: Double? = null
    for (m in (meets as? JsonArray) ?: JsonArray(emptyList())) {
        val s = bestAttempt(m.asObj()?.get("snatch"))
        if (s != null && (snatch == null || s > snatch!!)) snatch = s
        val c = bestAttempt(m.asObj()?.get("cj"))
        if (c != null && (cj == null || c > cj!!)) cj = c
        val t = totalOf(m)
        if (t != null && (total == null || t > total!!)) total = t
    }
    return CompetitionBests(snatch, cj, total)
}

/** Three rows for the editor, whatever the meet holds — the shape the form always shows. */
fun attemptRows(meet: JsonElement?, lift: String): List<JsonObject> =
    (0 until MAX_ATTEMPTS).map { i ->
        val a = (meet.asObj()?.get(lift) as? JsonArray)?.getOrNull(i)
        if (!truthy(a)) {
            // An attempted-but-unjudged row starts a good lift. Most attempts are made, so
            // defaulting to "no lift" made the whole form read as failure; and a row with no
            // weight never counts anyway (attemptMade), so the default is safe until a number
            // lands on it.
            js("w" to 0.0, "made" to true)
        } else {
            val w = a.asObj()?.get("w").asNum() ?: 0.0
            // `a.made !== false`: only an explicit false is a miss; an absent flag is a make.
            val made = a.asObj()?.get("made").asBool() != false
            js("w" to w, "made" to made)
        }
    }

/** Drop empty rows and keep only the fields the app reads back. A weightless row is not an attempt. */
fun cleanAttempts(attempts: List<JsonObject>?): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    for (raw in attempts ?: emptyList()) {
        // The JS list may hold a null row (`a && a.w`); the declared element type is non-null, so
        // read through a nullable view rather than assuming the row is there.
        val a: JsonElement? = raw
        val w = Math.round((a.asObj()?.get("w").asNum() ?: 0.0) * 10) / 10.0
        val made = truthy(a.asObj()?.get("made"))
        if (w > 0.0) out.add(js("w" to w, "made" to made))
    }
    return out
}

/** A new, empty meet dated to today. */
fun blankMeet(today: String?): JsonObject = JsonObject(
    linkedMapOf<String, JsonElement>(
        "id" to JsonPrimitive(uid()),
        "d" to JsonPrimitive(today ?: ""),
        "name" to JsonPrimitive(""),
        "place" to JsonPrimitive(""),
        "class" to JsonNull,
        "bw" to JsonNull,
        "snatch" to JsonArray(emptyList()),
        "cj" to JsonArray(emptyList()),
        "placing" to JsonNull,
        "note" to JsonPrimitive(""),
    ),
)

/** Put a meet back in the list, replacing the one with its id, kept in date order. */
fun upsertMeet(list: JsonElement?, meet: JsonObject): List<JsonObject> {
    val kept = ((list as? JsonArray) ?: JsonArray(emptyList()))
        .mapNotNull { it.asObj() }
        .filter { it["id"] != meet["id"] }
    return sortedMeets(JsonArray(kept + meet))
}

fun removeMeet(list: JsonElement?, id: String?): List<JsonObject> {
    // JS `m.id !== id`: a missing key is undefined, which is never equal to a null id (and a
    // stored null is only equal to a null id), so the two are told apart with containsKey.
    val wanted: JsonElement = if (id == null) JsonNull else JsonPrimitive(id)
    return ((list as? JsonArray) ?: JsonArray(emptyList()))
        .mapNotNull { it.asObj() }
        .filter { m -> !m.containsKey("id") || m["id"] != wanted }
}

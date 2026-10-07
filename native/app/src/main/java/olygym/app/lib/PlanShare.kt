package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.jsText
import olygym.app.data.num
import olygym.app.data.present
import olygym.app.data.str
import olygym.app.data.truthy
import olygym.app.data.with

/*
 * Sharing a plan. A port of frontend/src/lib/plan-share.js in full: a reviewed week lands in the
 * plan through mergeWeek, and the plan leaves the phone as a printable page through planPrintHTML.
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

/* ------------------------------- printable PDF ------------------------------- */

/**
 * The printable page behind "Print / Save as PDF": one self-contained HTML document, laid out so an
 * exercise -- and the day it sits in -- never splits across a page break.
 *
 * The web hands the same string to window.print(). Here it goes to the platform's print flow
 * (platform/PlanPrint.kt), which is where "Save as PDF" comes from: no PDF library rides in the app.
 */

private fun esc(value: String?): String = (value ?: "")
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/** One exercise's scheme, e.g. "3 × 10 · 60 kg" or "3 × 0:45". */
private fun scheme(e: JsonObject, unit: String): String {
    val sets = if (truthy(e["sets"])) e["sets"].jsText() else "1"
    val sec = (e.num("sec") ?: 0.0).takeIf { it != 0.0 } ?: 45.0
    var s = if (modeOf(e) == "time") sets + " × " + fmtSec(sec)
    else sets + " × " + (if (e["reps"].present()) e["reps"].jsText() else "10")
    if (truthy(e["weight"])) {
        s += " · " + (if (isBw(e)) "+" else "") + fmtNum(e.num("weight") ?: 0.0) + " " + unit
    }
    return s
}

/** Group consecutive exercises sharing a superset id into rendered units. */
private fun exerciseUnits(ex: List<JsonObject>): List<List<JsonObject>> {
    val out = mutableListOf<MutableList<JsonObject>>()
    ex.forEachIndexed { i, e ->
        val sg = e.str("sg")?.takeIf { it.isNotEmpty() }
        if (i > 0 && sg != null && ex.getOrNull(i - 1)?.str("sg") == sg) out.last().add(e)
        else out.add(mutableListOf(e))
    }
    return out
}

private fun dayHtml(day: JsonObject, unit: String): String {
    val rows = exerciseUnits(day.arr("ex").mapNotNull { it.asObj() }).joinToString("") { u ->
        val items = u.joinToString("") { e ->
            val ex = Catalogue[e.str("id").orEmpty()]
            val name = if (ex != null) I18nCore.exerciseNameFor(ex.id, ex.n) else I18nCore.t("Unknown exercise")
            val part = ex?.bp?.takeIf { it.isNotEmpty() }
                ?.let { """<span class="part">""" + esc(it) + "</span>" }.orEmpty()
            val note = e.str("note")?.takeIf { it.isNotEmpty() }
                ?.let { """<div class="ex-note">""" + esc(it) + "</div>" }.orEmpty()
            """<div class="ex"><div class="ex-row"><div class="ex-n">""" + esc(name) + part +
                """</div><div class="ex-s">""" + esc(scheme(e, unit)) + """</div></div>""" + note + "</div>"
        }
        if (u.size > 1) {
            """<div class="ss"><div class="ss-tag">""" + esc(I18nCore.t("Complex")) +
                """</div><div class="ss-items">""" + items + "</div></div>"
        } else {
            items
        }
    }
    val count = exCount(day.arr("ex").size)
    val named = day.str("name")
    val dow = day.num("dow")?.toInt() ?: -1
    val title = if (!named.isNullOrEmpty()) named else I18nCore.t(DAYN.getOrElse(dow) { "" })
    val list = if (rows.isEmpty()) """<div class="ex empty">""" + esc(I18nCore.t("No exercises yet.")) + "</div>" else rows
    return """<section class="routine">
        <div class="r-head"><h2>""" + esc(title) + """</h2><span class="r-count">""" + esc(count) + """</span></div>
        <div class="ex-list">""" + list + """</div>
    </section>"""
}

/** One dated week: its own name (or the date it starts on), then each of its days. */
private fun weekHtml(week: JsonObject, unit: String): String {
    val days = week.arr("days").mapNotNull { it.asObj() }.filter { it.arr("ex").isNotEmpty() }
    val date = fmtDate(week.str("startIso").orEmpty(), long = false, withYear = true)
    val named = week.str("name")
    val title = if (!named.isNullOrEmpty()) named else I18nCore.t("Week of {0}", date)
    val body = if (days.isEmpty()) {
        """<p class="none">""" + esc(I18nCore.t("No workouts planned this week.")) + "</p>"
    } else {
        days.joinToString("") { dayHtml(it, unit) }
    }
    return """<section class="week-block">
        <div class="wk-head"><h2>""" + esc(title) + """</h2><span class="wk-date">""" + esc(date) + """</span></div>
        """ + body + """
    </section>"""
}

/**
 * The whole document, ready for the printer. An empty plan is a page that says so rather than a blank
 * sheet: "No routines yet."
 */
fun planPrintHTML(S: JsonObject, owner: String): String {
    val unit = S.str("unit")?.takeIf { it.isNotEmpty() } ?: "kg"
    val weeks = S.arr("weeks").mapNotNull { it.asObj() }
        .filter { w -> w.arr("days").any { it.asObj()?.arr("ex")?.isNotEmpty() == true } }
    val body = if (weeks.isEmpty()) {
        """<p class="none">""" + esc(I18nCore.t("No routines yet.")) + "</p>"
    } else {
        weeks.joinToString("") { weekHtml(it, unit) }
    }
    val sub = listOfNotNull(owner.takeIf { it.isNotEmpty() }, todayISO()).joinToString(" · ") { esc(it) }
    return """<!doctype html><html><head><meta charset="utf-8">
<title>""" + esc(I18nCore.t("Weekly Training Plan")) + """</title>
<style>""" + PRINT_CSS + """</style></head>
<body><div class="doc">
  <header>
    <div class="kicker">OlyGym</div>
    <h1>""" + esc(I18nCore.t("Weekly Training Plan")) + """</h1>
""" + (if (sub.isNotEmpty()) """    <div class="sub">""" + sub + """</div>
""" else "") + """  </header>
  <h3 class="block">""" + esc(I18nCore.t("Your weeks")) + """</h3>
  """ + body + """
  <footer>""" + esc(I18nCore.t("Made with OlyGym")) + """ · opengym.duarte-santos.ch</footer>
</div></body></html>"""
}

/**
 * The page's own stylesheet, verbatim from plan-share.js. Paper is white and ink is black whatever
 * the app's theme is, and break-inside keeps an exercise, a routine and a week heading together.
 */
private const val PRINT_CSS = """
  @page { margin: 16mm 15mm; }
  * { box-sizing: border-box; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body {
    margin: 0; color: #16181d; background: #fff;
    font: 14px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-variant-numeric: tabular-nums;
  }
  .doc { max-width: 720px; margin: 0 auto; }
  header { border-bottom: 2px solid #16181d; padding-bottom: 12px; margin-bottom: 20px; }
  header .kicker { font-size: 11px; letter-spacing: .14em; text-transform: uppercase; color: #6a7a3a; font-weight: 700; }
  header h1 { font-size: 27px; letter-spacing: -.02em; margin: 3px 0 0; }
  header .sub { color: #6b7180; font-size: 13px; margin-top: 4px; }

  h3.block { font-size: 12px; letter-spacing: .1em; text-transform: uppercase; color: #8a90a0; margin: 0 0 8px; font-weight: 700; }

  .week-block { margin-bottom: 26px; }
  .wk-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; border-bottom: 1px solid #e4e6ec; padding-bottom: 6px; margin-bottom: 12px; break-after: avoid; page-break-after: avoid; }
  .wk-head h2 { font-size: 19px; letter-spacing: -.01em; margin: 0; text-transform: capitalize; }
  .wk-date { font-size: 12px; color: #8a90a0; white-space: nowrap; }

  .routine { break-inside: avoid; page-break-inside: avoid; margin-bottom: 20px; padding: 14px 16px; border: 1px solid #e4e6ec; border-radius: 12px; }
  .r-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; border-bottom: 1px solid #eef0f4; padding-bottom: 8px; margin-bottom: 8px; break-after: avoid; page-break-after: avoid; }
  .r-head h2 { font-size: 18px; letter-spacing: -.01em; margin: 0; text-transform: capitalize; }
  .r-count { font-size: 12px; color: #8a90a0; white-space: nowrap; }

  .ex-list { display: flex; flex-direction: column; }
  .ex { display: flex; flex-direction: column; padding: 6px 0; break-inside: avoid; page-break-inside: avoid; }
  .ex + .ex, .ss + .ex, .ex + .ss { border-top: 1px solid #f2f3f6; }
  .ex-row { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; }
  .ex-n { text-transform: capitalize; font-weight: 500; }
  .ex-n .part { text-transform: capitalize; color: #9aa0ae; font-weight: 400; font-size: 12px; margin-left: 8px; }
  .ex-s { color: #3d424e; white-space: nowrap; font-variant-numeric: tabular-nums; }
  .ex-note { color: #6a7080; font-size: 12px; margin-top: 2px; }
  .ex.empty, .none { color: #a2a8b6; }

  .ss { break-inside: avoid; page-break-inside: avoid; border-left: 3px solid #cfe08a; padding-left: 12px; margin: 4px 0; }
  .ss-tag { font-size: 10px; letter-spacing: .08em; text-transform: uppercase; color: #6a7a3a; font-weight: 700; padding-top: 4px; }
  .ss .ex:first-of-type { padding-top: 2px; }

  footer { margin-top: 26px; padding-top: 10px; border-top: 1px solid #eef0f4; color: #a2a8b6; font-size: 11px; text-align: center; }
"""

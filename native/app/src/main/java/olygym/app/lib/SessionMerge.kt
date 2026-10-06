package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.str

/*
 * Building one session's entries from a day of the dated-weeks plan — a port of
 * frontend/src/lib/session-merge.js.
 *
 * A day already holds the whole session (S.weeks[].days[].ex), so the old "combine several routines
 * into one session" machinery collapses: one day is one session, and there is nothing to
 * concatenate. This module only decides what a session with no day behind it is (freestyle) and
 * what it is called.
 */

/** A session's entries and its name. */
data class DayEntries(val entries: List<JsonObject>, val name: String)

/**
 * Build a session's entries and name from one day.
 *
 * `day` is a day object, or null for freestyle — which is an empty session the user fills as they
 * go, not a named plan. The JS reads the name as `day.name || t('Freestyle')`, a truthiness test, so
 * a non-string name reads as Freestyle here where JS would print the value; no writer stores one.
 */
fun buildDayEntries(st: JsonObject?, day: JsonObject?): DayEntries = DayEntries(
    entries = if (day == null) emptyList() else buildSessionEntries(st, day),
    name = day?.str("name")?.takeIf { it.isNotEmpty() } ?: I18nCore.t("Freestyle"),
)

/**
 * The name for a day that holds several planned sessions, from their names in order (ENG-12 rule):
 * 1-3 names join with " + ", 4 or more collapse to the first two and a count. An empty list is null.
 *
 * Nothing in the session path needs this any more (a day names itself), but the printed plan still
 * labels a legacy weekday that holds several routines.
 */
fun deriveSessionName(names: List<String>): String? = when {
    names.isEmpty() -> null
    names.size <= 3 -> names.joinToString(" + ")
    else -> "${names[0]} + ${names[1]} + ${names.size - 2} more"
}

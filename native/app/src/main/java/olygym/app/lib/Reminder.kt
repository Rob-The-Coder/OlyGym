package olygym.app.lib

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.obj
import olygym.app.data.str

/*
 * The workout-day reminder — a port of buildReminderNotifications in frontend/src/lib/mobile.js.
 *
 * The web queues one notification per planned calendar date for the next sixty days, because
 * Capacitor's Local Notifications plugin has no recurrence. Android does not need the queue: one
 * alarm is set for the next reminder and the receiver asks for the following one once it has posted.
 * So this is the same walk over the calendar, stopped at the first date that counts.
 */

/** The handful of times the reminder offers; a session start keeps its own list. */
val REMINDER_TIMES = listOf("07:00", "08:00", "12:00", "18:00", "20:00")

const val REMINDER_DEFAULT_TIME = "08:00"

/** How far ahead the walk looks. The web cancels and re-queues this whole window on every sync. */
const val REMINDER_WINDOW_DAYS = 60

/** One reminder: the date it belongs to, when to fire, and what it says. */
data class ReminderAt(val iso: String, val atMillis: Long, val title: String, val body: String)

/**
 * The next workout-day reminder, or null when it is off, the time is unusable, or nothing is planned
 * inside the window.
 *
 * [nowMillis] and [zone] are parameters rather than the clock so the calendar boundary, the
 * already-trained rule and today's past-time rule are deterministic in tests.
 */
fun nextReminder(S: JsonObject, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderAt? {
    val reminder = S.obj("reminder")
    if (reminder?.bool("on") != true) return null
    val parts = (reminder.str("time") ?: REMINDER_DEFAULT_TIME).split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    // The web lets a nonsense hour roll the date over instead; the picker cannot produce one.
    if (hour !in 0..23 || minute !in 0..59) return null

    val trained = S.arr("workouts").mapNotNull { it.asObj()?.str("d") }.toSet()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    for (offset in 0 until REMINDER_WINDOW_DAYS) {
        val date = today.plusDays(offset.toLong())
        val iso = isoOf(date)
        if (trained.contains(iso)) continue
        // One day is the whole session, so the reminder names that day (a rest day gets none).
        val planned = effectiveDay(S, iso) ?: continue
        val at = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (at <= nowMillis) continue
        val label = planned.name.ifBlank { I18nCore.t("Workout") }
        return ReminderAt(
            iso = iso,
            atMillis = at,
            title = I18nCore.t("Workout day"),
            body = I18nCore.t("{0} is on the plan today — let’s go!", label),
        )
    }
    return null
}

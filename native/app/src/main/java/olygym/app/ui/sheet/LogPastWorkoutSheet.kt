package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.lib.effectiveDay
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtDur
import olygym.app.lib.fmtVol
import olygym.app.lib.todayISO
import olygym.app.lib.workoutVolume
import olygym.app.lib.workoutsOn
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.components.Stepper
import olygym.app.ui.currentProfile
import olygym.app.ui.profileNow
import olygym.app.ui.t
import olygym.app.ui.ui
import olygym.app.ui.workout.beginBackfill
import java.time.LocalDate

/**
 * Log a workout after the fact — a port of LogPastWorkout in frontend/src/sheets.jsx. The date
 * decides what the session opens with: whatever the dated plan has on it, or an empty session to
 * fill in by hand. The session itself is then logged on the ordinary workout screen, without timers.
 */
fun logPastWorkoutSheet(initialDate: String? = null) {
    if (profileNow()?.active != null) {
        ui.toast(t("Finish the current workout first."))
        return
    }
    ui.openSheet { close -> LogPastWorkout(initialDate, close) }
}

@Composable
private fun LogPastWorkout(initialDate: String?, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val today = todayISO()
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now().minusDays(1).toString()) }
    var time by remember { mutableStateOf("18:00") }
    var dur by remember { mutableStateOf(60) }
    val planned = effectiveDay(profile.raw, date)
    val existing = workoutsOn(profile.raw, date).mapNotNull { it.asObj() }

    // Deciding what to do about a day that is already spoken for is a list of actions, not a centred
    // alert with two full-width buttons: the destructive one carries the trash and the danger role,
    // the one that adds a second session reads first, and dismissing cancels.
    val go = { replaceId: String? ->
        close()
        beginBackfill(date, time, dur, replaceId)
    }
    val submit = {
        if (date.isEmpty() || date > today) {
            ui.toast(t("Pick a day up to today"))
        } else if (existing.isEmpty()) {
            go(null)
        } else {
            menuSheet(
                title = fmtDate(date, long = true),
                subtitle = t("There is already a workout on that day."),
                items = buildList {
                    add(
                        MenuItem(
                            label = t("Add as second workout"),
                            icon = Glyph.PLUS,
                            onClick = { go(null) },
                        ),
                    )
                    existing.forEach { w ->
                        add(
                            MenuItem(
                                label = if (existing.size > 1) {
                                    t("Replace {0}", w.str("name").orEmpty())
                                } else {
                                    t("Replace")
                                },
                                icon = Glyph.TRASH,
                                danger = true,
                                onClick = { go(w.str("id")) },
                            ),
                        )
                    }
                },
            )
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Log a past workout"))
        SheetNote(t("Logged on the usual workout screen, without timers."))
        Section {
            ListRow(
                title = t("Date"),
                icon = Glyph.CALENDAR,
                value = fmtDate(date, long = true),
                accessory = Accessory.CHEVRON,
                onClick = { datePickerSheet(date, today) { date = it } },
            )
            RowDivider()
            ListRow(
                title = t("Start time"),
                icon = Glyph.CLOCK,
                value = time,
                accessory = Accessory.CHEVRON,
                onClick = { timePickerSheet(time, onPick = { time = it }) },
            )
        }
        Stepper(
            label = t("Duration"),
            value = dur.toDouble(),
            step = 5.0,
            decimal = false,
            unit = "min",
            onChange = { dur = maxOf(1, it.toInt()) },
            modifier = Modifier.padding(top = 12.dp),
        )
        ListRow(
            title = t("Plan"),
            icon = Glyph.DUMBBELL,
            value = planned?.name ?: t("Freestyle"),
            modifier = Modifier.padding(top = 10.dp),
        )
        if (existing.isNotEmpty()) {
            Overline(t("Already on this day"), Modifier.padding(top = 14.dp, bottom = 6.dp))
            Section {
                existing.forEachIndexed { index, w ->
                    if (index > 0) RowDivider()
                    val start = w.num("start") ?: 0.0
                    val end = w.num("end") ?: start
                    ListRow(
                        title = w.str("name").orEmpty(),
                        icon = Glyph.DUMBBELL,
                        subtitle = listOf(
                            fmtDur((end - start).toLong()),
                            fmtVol(w.num("vol") ?: workoutVolume(w), unit),
                        ).joinToString(" · "),
                    )
                }
            }
        }
        Button(
            text = t("Continue"),
            onClick = submit,
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

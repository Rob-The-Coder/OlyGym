package olygym.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import olygym.app.data.arr
import olygym.app.lib.DAYS
import olygym.app.lib.MONTHS_LONG
import olygym.app.lib.monthCells
import olygym.app.lib.monthLabel
import olygym.app.lib.todayISO
import olygym.app.lib.weekOrder
import olygym.app.lib.weekStartOf
import olygym.app.lib.workoutDates
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.components.Stepper
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.theme.FullShape
import olygym.app.ui.ui
import java.time.LocalDate

/*
 * The date and the start time of a past workout — a port of DatePicker and TimePicker in
 * frontend/src/sheets.jsx. Both were platform widgets on the web (<input type="date"> and
 * <input type="time">), drawn in the platform's colours and its own format against everything else
 * the app does; the date is the same month grid the calendar uses, so the days that already have a
 * session are visible while you pick one.
 */

/** The handful of times a session actually starts at. */
val START_TIMES = listOf("06:30", "12:00", "17:30", "18:00", "19:30")

fun datePickerSheet(value: String?, max: String? = todayISO(), onPick: (String) -> Unit): Long =
    ui.openSheet { close -> DatePicker(value, max, onPick, close) }

fun timePickerSheet(
    value: String?,
    onPick: (String) -> Unit,
    title: String = t("Start time"),
    presets: List<String> = START_TIMES,
): Long = ui.openSheet { close -> TimePicker(value, onPick, title, presets, close) }

@Composable
private fun DatePicker(value: String?, max: String?, onPick: (String) -> Unit, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val weekStart = weekStartOf(profile.settings.weekStart)
    val thisMonth = LocalDate.now().withDayOfMonth(1)
    var cursor by remember { mutableStateOf((value?.let { LocalDate.parse(it) } ?: LocalDate.now()).withDayOfMonth(1)) }
    val withWorkouts = workoutDates(profile.raw.arr("workouts"))
    val planned = { iso: String -> olygym.app.lib.effectiveDay(profile.raw, iso) != null }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(Glyph.CHEVRON_LEFT, t("Previous month"), onClick = { cursor = cursor.minusMonths(1) })
            Text(
                text = t(MONTHS_LONG[cursor.monthValue - 1]) + " " + cursor.year.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(Glyph.CHEVRON_RIGHT, t("Next month"), onClick = { cursor = cursor.plusMonths(1) })
        }
        MonthGrid(
            year = cursor.year,
            month = cursor.monthValue,
            weekStart = weekStart,
            withWorkouts = withWorkouts,
            planned = planned,
            value = value,
            disabledAfter = max,
            onPick = { iso ->
                onPick(iso)
                close()
            },
        )
        GridLegend()
        Button(
            text = t("Back to this month"),
            onClick = { cursor = thisMonth },
            variant = ButtonVariant.GHOST,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The month laid out in the profile's weeks, with a dot under the days that were trained. */
@Composable
internal fun MonthGrid(
    year: Int,
    month: Int,
    weekStart: Int,
    withWorkouts: Set<String>,
    planned: (String) -> Boolean,
    value: String? = null,
    disabledAfter: String? = null,
    onPick: (String) -> Unit,
) {
    val cells = monthCells(
        year = year,
        month = month,
        weekStart = weekStart,
        todayIso = todayISO(),
        selected = value,
        disabledAfter = disabledAfter,
        withWorkouts = withWorkouts,
        planned = planned,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekOrder(weekStart).forEach { dow ->
                Text(
                    text = t(DAYS[dow]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    if (cell == null) {
                        Box(Modifier.weight(1f).height(46.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .padding(2.dp)
                                .clip(FullShape)
                                .background(
                                    when {
                                        cell.selected -> MaterialTheme.colorScheme.primary
                                        cell.today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .then(
                                    if (cell.disabled) {
                                        Modifier
                                    } else {
                                        Modifier.clickable { onPick(cell.iso) }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cell.day.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    cell.disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    cell.selected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (cell.hasWorkouts || cell.planned) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 5.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                cell.selected -> MaterialTheme.colorScheme.onPrimary
                                                cell.hasWorkouts -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
                // A short last week still keeps the columns aligned.
                repeat(7 - week.size) { Box(Modifier.weight(1f).height(46.dp)) }
            }
        }
    }
}

@Composable
internal fun GridLegend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        legendItem(MaterialTheme.colorScheme.primary, t("Trained"))
        legendItem(MaterialTheme.colorScheme.onSurfaceVariant, t("Planned"))
    }
}

@Composable
private fun legendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimePicker(
    value: String?,
    onPick: (String) -> Unit,
    title: String,
    presets: List<String>,
    close: () -> Unit,
) {
    val parts = (value ?: "18:00").split(":")
    var hour by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 18) }
    var minute by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    val now = pad(hour) + ":" + pad(minute)
    val pick = { x: String ->
        val p = x.split(":")
        hour = p.getOrNull(0)?.toIntOrNull() ?: hour
        minute = p.getOrNull(1)?.toIntOrNull() ?: minute
    }
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(title)
        Stepper(
            label = t("Hour"),
            value = hour.toDouble(),
            step = 1.0,
            decimal = false,
            onChange = { hour = it.toInt().coerceIn(0, 23) },
        )
        Stepper(
            label = t("Minute"),
            value = minute.toDouble(),
            step = 5.0,
            decimal = false,
            onChange = { minute = it.toInt().coerceIn(0, 59) },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { x ->
                val on = x == now
                Box(
                    modifier = Modifier
                        .clip(FullShape)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable { pick(x) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = x,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Button(
            text = t("Done"),
            onClick = {
                onPick(now)
                close()
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun pad(n: Int): String = if (n < 10) "0" + n.toString() else n.toString()

/** The month grid's own reading of the profile's trained days, for a caller outside this file. */
internal fun trainedDates(profile: olygym.app.data.Profile): Set<String> = workoutDates(profile.raw.arr("workouts"))

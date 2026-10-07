package olygym.app.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.asObj
import olygym.app.data.obj
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.ReviewEntry
import olygym.app.lib.WEEK_PLAN
import olygym.app.lib.XlsxSheet
import olygym.app.lib.bundleFromWeek
import olygym.app.lib.exLine
import olygym.app.lib.mergeWeek
import olygym.app.lib.normName
import olygym.app.lib.reviewWeek
import olygym.app.lib.DAYS
import olygym.app.ui.Nav
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Segmented
import olygym.app.ui.components.Tag
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.profileNow
import olygym.app.ui.t
import olygym.app.ui.theme.extraColors
import olygym.app.ui.ui

/**
 * Review the coach's week before any of it becomes a routine. A port of
 * frontend/src/components/CoachImport.jsx.
 *
 * The reading is a guess in places -- an Italian gym phrase the catalogue has no word for, a load
 * written as a sentence, a "1+1" that is one clean and jerk and not two exercises -- so every row of
 * his sheet is on screen with the exercise it was read as, and the rows where something stayed in
 * the note are marked. Tapping a row picks a different exercise, and the choice is remembered in
 * S.planAliases under the coach's own words: the next week's sheet repeats the same phrases, so it
 * arrives already corrected.
 */
fun coachImportSheet(sheets: List<XlsxSheet>) {
    ui.openSheet { close -> CoachImport(sheets, close) }
}

@Composable
private fun CoachImport(sheets: List<XlsxSheet>, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val S = profile.raw
    val unit = profile.settings.unit
    var week by remember { mutableStateOf(0) }
    var dayIdx by remember { mutableStateOf(0) }

    // The review is derived, never stored: correcting a row rewrites S.planAliases and the whole week
    // is read again, so one fix covers every row that repeats those words.
    val aliases = S.obj("planAliases")
    val sheet = sheets.getOrNull(week) ?: return
    val review = reviewWeek(sheet, aliases)
    val day = review.days.getOrNull(dayIdx.coerceAtMost(review.days.size - 1)) ?: review.days.firstOrNull()

    fun correct(entry: ReviewEntry, id: String?) {
        editProfile { raw ->
            val key = normName(entry.part)
            val current = raw.obj("planAliases") ?: JsonObject(emptyMap())
            raw.with(
                "planAliases",
                if (id.isNullOrEmpty()) current.without(key) else current.with(key, id),
            )
        }
    }

    fun rowMenu(entry: ReviewEntry) {
        val chosen = if (entry.custom) entry.name else Catalogue.nameOf(entry.id)
        val corrected = aliases?.str(normName(entry.part)) != null
        menuSheet(
            title = entry.part,
            subtitle = t("Read as “{0}”", chosen),
            items = listOfNotNull(
                MenuItem(
                    label = t("Pick a different exercise"),
                    icon = Glyph.SHUFFLE,
                    onClick = { exercisePicker { ex, _ -> correct(entry, ex.id) } },
                ),
                if (corrected) {
                    MenuItem(
                        label = t("Back to what it read"),
                        icon = Glyph.RESET,
                        onClick = { correct(entry, null) },
                    )
                } else {
                    null
                },
            ),
        )
    }

    fun apply() {
        // The sheet is in kilos whatever unit the account is in: bundleFromWeek converts on the way in.
        val built = bundleFromWeek(review, unit = unit)
        val merged = profileNow()?.raw?.let { mergeWeek(it, built) }
        if (merged == null) {
            // The web throws through unitError here; a screen cannot catch that through the store.
            ui.toast(t("this isn’t an OlyGym plan file"))
            return
        }
        editProfile { merged }
        close()
        ui.toast(t("Added the week to your plan"))
        Nav.goHome()
        Nav.tabHost?.invoke(1)
    }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("The coach’s plan"))
        Text(
            text = t("Read out of his spreadsheet, one week at a time. Nothing reaches your plan until you say so."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (sheets.size > 1) {
            ListRow(
                title = sheet.name,
                subtitle = t("{0} weeks in this file", sheets.size),
                accessory = Accessory.CHEVRON,
                onClick = {
                    menuSheet(
                        title = t("Which week"),
                        items = sheets.mapIndexed { i, s ->
                            MenuItem(label = s.name, on = i == week, onClick = {
                                week = i
                                dayIdx = 0
                            })
                        },
                    )
                },
            )
        }

        // The counts were four equal cards and only "to check" asked for anything. As a line of
        // supporting text they stop outranking the rows this screen exists to show.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t(
                    "{0} days · {1} exercises · {2} new",
                    review.days.size,
                    review.stats.exercises,
                    review.stats.custom,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (review.stats.fuzzy > 0) {
                Text(
                    text = " · " + t("{0} to check", review.stats.fuzzy),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.extraColors.yellow,
                )
            }
        }

        // One cell per day the coach wrote, so the labels are numbers: six "Giorno N" cells wrap onto
        // two lines at 360px, and a sheet is not always five days.
        if (review.days.size > 1) {
            Segmented(
                options = review.days.indices.map { it.toString() },
                value = dayIdx.coerceAtMost(review.days.size - 1).toString(),
                onChange = { dayIdx = it.toIntOrNull() ?: 0 },
                labels = review.days.mapIndexed { i, d -> (i + 1).toString() },
            )
        }

        if (day != null) {
            Overline(day.name, Modifier.padding(top = 12.dp, bottom = 6.dp))
        }
        day?.entries?.forEach { entry ->
            ReviewRow(entry, unit) { rowMenu(entry) }
        }
        if (day != null && day.entries.isEmpty()) {
            Text(
                text = t("Nothing in this day."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = t(
                            "Train it {0}",
                            WEEK_PLAN.take(maxOf(1, review.days.size)).joinToString(" / ") { DAYS[it] },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = t("The week lands on those days of this week’s plan."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            text = t("Add the week to my plan"),
            onClick = { apply() },
            variant = ButtonVariant.PRIMARY,
            enabled = review.days.isNotEmpty(),
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            text = t("Cancel"),
            onClick = close,
            variant = ButtonVariant.GHOST,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** One proposed row: what it was read as, the scheme, the note, and the marks worth checking. */
@Composable
private fun ReviewRow(
    entry: ReviewEntry,
    unit: String,
    onClick: () -> Unit,
) {
    val config = olygym.app.data.js(
        "id" to entry.id,
        "sets" to entry.sets,
        "reps" to entry.reps,
        "mode" to entry.mode,
        "sec" to entry.sec,
        "side" to entry.side,
        "weight" to entry.weight,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (entry.custom) entry.name else Catalogue.nameOf(entry.id),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = exLine(config, unit) + (if (entry.sg != null) " · " + t("Complex") else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.note.isNotEmpty()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (entry.tier == 3) Tag(t("New exercise"))
                if (entry.tier == 2) Tag(t("His words in the note"))
                if (entry.tier == 1 && !entry.exact) Tag(t("Check"))
                if (entry.warns.contains("sets")) Tag(t("no sets given"))
                if (entry.warns.contains("reps")) Tag(t("no reps given"))
            }
        }
        GlyphIcon(
            Glyph.SHUFFLE,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

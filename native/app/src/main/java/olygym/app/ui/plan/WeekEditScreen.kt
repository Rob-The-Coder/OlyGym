package olygym.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Day
import olygym.app.data.Profile
import olygym.app.data.Week
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.asObj
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.objectAt
import olygym.app.data.str
import olygym.app.data.toJsonObject
import olygym.app.data.truthy
import olygym.app.data.with
import olygym.app.lib.DAYN
import olygym.app.lib.DAYS
import olygym.app.lib.addDay
import olygym.app.lib.addExToDay
import olygym.app.lib.capWords
import olygym.app.lib.dayCount
import olygym.app.lib.dayEntries
import olygym.app.lib.defaultConfig
import olygym.app.lib.deleteDay
import olygym.app.lib.deleteWeek
import olygym.app.lib.exCount
import olygym.app.lib.exLine
import olygym.app.lib.exOr
import olygym.app.lib.firstFreeDow
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.moveUnitAt
import olygym.app.lib.removeExAt
import olygym.app.lib.setDayDow
import olygym.app.lib.setDayName
import olygym.app.lib.setExAt
import olygym.app.lib.setWeekName
import olygym.app.lib.supersetUnits
import olygym.app.lib.toggleLinkAt
import olygym.app.lib.weekOrder
import olygym.app.lib.weekStartOf
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.LineField
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Segmented
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.sheet.MenuItem
import olygym.app.ui.sheet.complexConfigSheet
import olygym.app.ui.sheet.confirmSheet
import olygym.app.ui.sheet.exConfigSheet
import olygym.app.ui.sheet.exercisePicker
import olygym.app.ui.sheet.menuSheet
import olygym.app.ui.t
import olygym.app.ui.theme.CardShape
import olygym.app.ui.theme.emphasizedWeight
import olygym.app.ui.ui

/**
 * One dated week, edited in place — a port of frontend/src/views/WeekEdit.jsx: its name, and one
 * card per weekday it plans. A day's exercises are edited inside the day itself, because a day is a
 * routine that belongs to a concrete week instead of repeating.
 *
 * Not ported: swipe-to-delete on a row (the config sheet's own Remove from routine is the way out
 * here) and the demo thumbnail (media is its own phase, so a row gets the app's own glyph).
 */
class WeekEditScreen(private val weekId: String) : AppScreen() {
    /** Voyager keys the stack by this: two weeks must not share one entry. */
    override val key: String get() = this::class.java.name + ":" + weekId

    @Composable
    override fun Content() {
        val profile = currentProfile() ?: return
        val week = profile.weeks.firstOrNull { it.id == weekId }
        if (week == null) {
            // Deleted from under us, or replaced by another writer: there is nothing to edit.
            LaunchedEffect(weekId) { Nav.back() }
            return
        }
        WeekEdit(profile, week)
    }
}

/** "Week of 5 Oct 2026" when the week has no name of its own. */
private fun weekLabel(week: Week): String =
    week.name.ifBlank { t("Week of {0}", fmtDate(week.startIso, long = false, withYear = true)) }

@Composable
private fun WeekEdit(profile: Profile, week: Week) {
    val id = week.id
    val weekStart = weekStartOf(profile.settings.weekStart)
    val unit = profile.settings.unit
    val entries = dayEntries(week, weekStart)
    // Which day is open, by its live index in week.days, so an edit still finds the right one after
    // the order changes.
    var open by remember(id) { mutableStateOf<Int?>(null) }
    val label = weekLabel(week)

    val onDeleteWeek = {
        confirmSheet(
            title = t("Delete week?"),
            message = t("“{0}” and its exercises will be removed.", label),
            confirmText = t("Delete"),
            danger = true,
            onConfirm = {
                editProfile { raw -> deleteWeek(raw, id) }
                open = null
                Nav.back()
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // A small top bar built here rather than the app's large one: its title is a field, and a
        // field cannot ride inside a collapsing large app bar.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(Glyph.CHEVRON_LEFT, onClick = { Nav.back() })
            LineField(
                value = week.name,
                onChange = { value -> editProfile { raw -> setWeekName(raw, id, value) } },
                placeholder = t("Week of {0}", fmtDate(week.startIso, long = false, withYear = true)),
                // The web's input carries .ab-title too, so it takes the same Emphasized weight.
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = emphasizedWeight(MaterialTheme.typography.headlineSmall.fontWeight),
                ),
                dashed = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(Glyph.MORE, onClick = {
                menuSheet(
                    title = label,
                    subtitle = fmtDate(week.startIso, long = true, withYear = true),
                    items = listOf(
                        MenuItem(
                            label = t("Delete this week"),
                            icon = Glyph.TRASH,
                            danger = true,
                            onClick = onDeleteWeek,
                        ),
                    ),
                )
            })
        }
        Text(
            text = listOf(
                fmtDate(week.startIso, long = true, withYear = true),
                if (week.days.isEmpty()) null else dayCount(week.days.size),
                if (week.days.isEmpty()) null else exCount(week.days.sumOf { it.ex.size }),
            ).filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Overline(t("Weekdays")) }
            items(entries.size) { i ->
                val (day, index) = entries[i]
                DayCard(
                    weekId = id,
                    weekStart = weekStart,
                    unit = unit,
                    day = day,
                    index = index,
                    open = open == index,
                    onToggle = { open = if (open == index) null else index },
                )
            }
            item {
                Button(
                    text = t("Add day"),
                    onClick = {
                        val index = week.days.size
                        editProfile { raw -> addDay(raw, id, firstFreeDow(week, weekStart), t("New day")) }
                        open = index
                    },
                    icon = Glyph.PLUS,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    weekId: String,
    weekStart: Int,
    unit: String,
    day: Day,
    index: Int,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val order = weekOrder(weekStart)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Segmented(
                        options = order.map { it.toString() },
                        labels = order.map { t(DAYS[it]) },
                        value = day.dow.toString(),
                        onChange = { value ->
                            editProfile { raw -> setDayDow(raw, weekId, index, value.toIntOrNull() ?: 0) }
                        },
                    )
                }
                IconButton(Glyph.MORE, onClick = {
                    menuSheet(
                        title = day.name.ifBlank { t(DAYN[day.dow.coerceIn(0, 6)]) },
                        items = listOf(
                            MenuItem(
                                label = t("Remove this day"),
                                icon = Glyph.TRASH,
                                danger = true,
                                onClick = {
                                    confirmSheet(
                                        title = t("Delete day?"),
                                        message = t(
                                            "“{0}” and its exercises will be removed.",
                                            day.name.ifBlank { t(DAYN[day.dow.coerceIn(0, 6)]) },
                                        ),
                                        confirmText = t("Delete"),
                                        danger = true,
                                        onConfirm = {
                                            editProfile { raw -> deleteDay(raw, weekId, index) }
                                        },
                                    )
                                },
                            ),
                        ),
                    )
                })
            }
            // An editable title that does not look like one: the dashed rule under the text is the
            // whole affordance, so the day keeps reading as a name and not as a form.
            LineField(
                value = day.name,
                onChange = { value -> editProfile { raw -> setDayName(raw, weekId, index, value) } },
                placeholder = t(DAYN[day.dow.coerceIn(0, 6)]),
                textStyle = MaterialTheme.typography.titleMedium,
                dashed = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // What the day holds, before it is opened: a collapsed day used to say only how many
            // exercises it had, which is the one thing about a plan you can already guess.
            if (!open && day.ex.isNotEmpty()) {
                Text(
                    text = day.ex.joinToString(" · ") { capWords(Catalogue.nameOf(it.str("id").orEmpty())) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Button(
                text = exCount(day.ex.size),
                onClick = onToggle,
                variant = ButtonVariant.GHOST,
                size = ButtonSize.SM,
                trailingIcon = if (open) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (open) DayExercises(weekId, day, index, unit)
        }
    }
}

/* ----------------------------------------------------------------- the day's exercises -- */

@Composable
private fun DayExercises(weekId: String, day: Day, dayIndex: Int, unit: String) {
    val list = JsonArray(day.ex)
    val units = supersetUnits(list)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (day.ex.isEmpty()) {
            Text(
                text = t("No exercises yet — add your first one."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            units.forEachIndexed { unitIndex, element ->
                val indices = element.asArr()?.mapNotNull { it.asInt() }.orEmpty()
                if (indices.size > 1) {
                    ComplexCard(weekId, day, dayIndex, unitIndex, list, indices, unit)
                } else {
                    indices.forEachIndexed { k, i ->
                        ExRow(
                            weekId = weekId,
                            day = day,
                            dayIndex = dayIndex,
                            list = list,
                            index = i,
                            unit = unit,
                            first = k == 0,
                            last = k == indices.size - 1,
                        )
                    }
                }
            }
        }
        Button(
            text = t("Add exercise"),
            onClick = { addExercise(weekId, day, dayIndex) },
            variant = ButtonVariant.PRIMARY,
            icon = Glyph.PLUS,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

/**
 * A complex is one card: its sets and load belong to the whole thing, and each row keeps its own
 * reps. The heading writes the two shared numbers, into this day's own ex — a day is what a session
 * is built from, so this is the live plan.
 */
@Composable
private fun ComplexCard(
    weekId: String,
    day: Day,
    dayIndex: Int,
    unitIndex: Int,
    list: JsonArray,
    indices: List<Int>,
    unit: String,
) {
    val head = list.objectAt(indices.firstOrNull() ?: return) ?: return
    val sets = head.int("sets") ?: 1
    val weight = head.num("weight") ?: 0.0
    val shared = indices.all { i ->
        val row = list.objectAt(i) ?: return@all false
        (row.int("sets") ?: 1) == sets && (row.num("weight") ?: 0.0) == weight
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { complexConfigSheet(weekId, dayIndex, unitIndex) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(
                    Glyph.LINK,
                    Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = t("Complex"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (shared) {
                        listOf(
                            t(if (sets == 1) "{0} set" else "{0} sets", sets),
                            if (weight > 0.0) fmtNum(weight) + " " + unit else null,
                        ).filterNotNull().joinToString(" · ")
                    } else {
                        t("{0} exercises", indices.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            indices.forEachIndexed { k, i ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (k + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        ExRow(weekId, day, dayIndex, list, i, unit, first = false, last = false)
                    }
                }
            }
        }
    }
}

/**
 * One prescribed exercise. The three actions stay on the row: this is the screen where order is
 * worked out, so a reorder is one tap here rather than a menu.
 */
@Composable
private fun ExRow(
    weekId: String,
    day: Day,
    dayIndex: Int,
    list: JsonArray,
    index: Int,
    unit: String,
    first: Boolean,
    last: Boolean,
) {
    val entry = list.objectAt(index) ?: return
    val id = entry.str("id").orEmpty()
    val sg = entry["sg"]
    val prevSg = list.objectAt(index - 1)?.get("sg")
    val linked = truthy(sg) && truthy(prevSg) && sg == prevSg
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The demo thumbnail belongs to the media phase; the row gets the app's own glyph.
        GlyphIcon(
            Glyph.DUMBBELL,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .clickable { openExConfig(weekId, day, dayIndex, index, entry) },
        ) {
            Text(
                text = capWords(Catalogue.nameOf(id)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exLine(entry, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val note = entry.str("note")
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (index > 0) {
            RowIcon(
                Glyph.LINK,
                tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { editProfile { raw -> toggleLinkAt(raw, weekId, dayIndex, index) } },
            )
        }
        RowIcon(
            Glyph.CHEVRON_UP,
            enabled = !first,
            onClick = { editProfile { raw -> moveUnitAt(raw, weekId, dayIndex, index, -1) } },
        )
        RowIcon(
            Glyph.CHEVRON_DOWN,
            enabled = !last,
            onClick = { editProfile { raw -> moveUnitAt(raw, weekId, dayIndex, index, 1) } },
        )
    }
}

/**
 * The web's 36px .iconbtn. Three of them share one row here, and the app's IconButton is 44dp — on a
 * 360dp phone, four of those (with the gap) would leave the exercise's own name nothing. This is the
 * same compromise the set rows' step buttons make.
 */
@Composable
private fun RowIcon(
    glyph: Glyph,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph,
            Modifier.size(17.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            stroke = 1.85f,
        )
    }
}

/** The config sheet for one row of a day: save keeps the row's id and its superset id. */
private fun openExConfig(weekId: String, day: Day, dayIndex: Int, index: Int, entry: JsonObject) {
    exConfigSheet(
        ex = Catalogue[entry.str("id").orEmpty()],
        existing = entry,
        onSave = { cfg -> editProfile { raw -> setExAt(raw, weekId, dayIndex, index, cfg) } },
        onDelete = { editProfile { raw -> removeExAt(raw, weekId, dayIndex, index) } },
        routine = day.toJsonObject(),
    )
}

/** Add exercise: the row's plus commits the default config, tapping the row configures it first. */
private fun addExercise(weekId: String, day: Day, dayIndex: Int) {
    val name = day.name.ifBlank { t(DAYN[day.dow.coerceIn(0, 6)]) }
    exercisePicker { picked, quick ->
        if (quick) {
            editProfile { raw ->
                addExToDay(raw, weekId, dayIndex, JsonObject(js("id" to picked.id) + defaultConfig(picked.id)))
            }
            ui.toast(t("“{0}” added to {1}", capWords(Catalogue.nameOf(picked.id)), name))
        } else {
            exConfigSheet(
                ex = picked,
                existing = null,
                onSave = { cfg ->
                    editProfile { raw ->
                        addExToDay(raw, weekId, dayIndex, JsonObject(js("id" to picked.id) + cfg))
                    }
                },
                routine = day.toJsonObject(),
            )
        }
    }
}

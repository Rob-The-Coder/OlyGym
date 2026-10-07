package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.lib.ClassLists
import olygym.app.lib.DEFAULT_CLASSES
import olygym.app.lib.attemptMade
import olygym.app.lib.attemptRows
import olygym.app.lib.bestAttempt
import olygym.app.lib.blankMeet
import olygym.app.lib.classLists
import olygym.app.lib.classOptions
import olygym.app.lib.cleanAttempts
import olygym.app.lib.cleanClasses
import olygym.app.lib.daysUntil
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.lastBW
import olygym.app.lib.listFor
import olygym.app.lib.removeMeet
import olygym.app.lib.todayISO
import olygym.app.lib.totalOf
import olygym.app.lib.uid
import olygym.app.lib.upsertMeet
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Section
import olygym.app.ui.components.Segmented
import olygym.app.ui.components.Stepper
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * The three competition sheets: one meet written down, one meet read back, and the federation's
 * categories. A port of MeetSheet, MeetDetail and WeightClassesSheet in frontend/src/sheets.jsx.
 *
 * A competition is not a training session: it is a dated event with a bodyweight category and up to
 * three snatch and three clean & jerk attempts, each judged good or no lift. It is kept apart from
 * the training log on purpose.
 */

fun meetSheet(meet: JsonObject?) {
    ui.openSheet { close -> MeetSheet(meet, close) }
}

fun meetDetailSheet(meet: JsonObject) {
    ui.openSheet { close -> MeetDetail(meet, close) }
}

fun weightClassesSheet() {
    ui.openSheet { close -> WeightClasses(close) }
}

@Composable
private fun MeetSheet(meet: JsonObject?, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val S = profile.raw
    val unit = profile.settings.unit
    val editing = meet != null

    var name by remember { mutableStateOf(meet?.str("name").orEmpty()) }
    var date by remember { mutableStateOf(meet?.str("d")?.takeIf { it.isNotEmpty() } ?: todayISO()) }
    var place by remember { mutableStateOf(meet?.str("place").orEmpty()) }
    var cls by remember { mutableStateOf(meet?.str("class")) }
    // An imported or older meet may carry no weigh-in; the last recorded bodyweight is the best
    // starting point the app has for one.
    var bw by remember { mutableStateOf(meet?.num("bw") ?: (lastBW(S) as? JsonObject)?.num("w")) }
    var placing by remember { mutableStateOf(meet?.num("placing")?.toInt()) }
    var snatch by remember { mutableStateOf(attemptRows(meet, "snatch")) }
    var cj by remember { mutableStateOf(attemptRows(meet, "cj")) }
    var note by remember { mutableStateOf(meet?.str("note").orEmpty()) }

    // The total reads off the draft as it is typed, so the sheet answers the meet's only number
    // while there is still time to change an attempt.
    val draftSnatch = cleanAttempts(snatch)
    val draftCj = cleanAttempts(cj)
    val draft = js("snatch" to draftSnatch, "cj" to draftCj)
    val bestS = bestAttempt(draft["snatch"])
    val bestC = bestAttempt(draft["cj"])
    val total = totalOf(draft)

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(if (editing) t("Edit competition") else t("Add a competition"))
        Text(
            text = t("A meet is its own record — nothing here changes your training log."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        NoteField(
            value = name,
            onChange = { name = it },
            placeholder = t("Competition name"),
            singleLine = true,
        )

        Section(modifier = Modifier.padding(top = 10.dp)) {
            ListRow(
                title = t("Date"),
                icon = Glyph.CALENDAR,
                value = fmtDate(date, long = true, withYear = true),
                accessory = Accessory.CHEVRON,
                onClick = { datePickerSheet(date, max = null) { date = it } },
            )
            SelectRow(
                title = t("Weight class"),
                icon = Glyph.TROPHY,
                sheetTitle = t("Weight class"),
                value = cls.orEmpty(),
                options = listOf(SelectOption("", t("Not set"))) +
                    classOptions(listFor(S)).map { SelectOption(it.value, it.label) },
                onChange = { cls = it.takeIf { value -> value.isNotEmpty() } },
            )
            ListRow(
                title = t("Edit categories"),
                icon = Glyph.PENCIL,
                accessory = Accessory.CHEVRON,
                onClick = { weightClassesSheet() },
            )
        }

        // Stacked, not side by side: a Stepper's label has one line and half a phone is not enough
        // for "Bodyweight at weigh-in" (the web stacks them too).
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Stepper(
                value = bw ?: 0.0,
                onChange = { bw = if (it > 0.0) it else null },
                step = 0.1,
                label = t("Bodyweight at weigh-in"),
                unit = unit,
            )
            Stepper(
                value = (placing ?: 0).toDouble(),
                onChange = { placing = if (it > 0.0) it.toInt() else null },
                step = 1.0,
                decimal = false,
                label = t("Placing"),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Overline(t("Snatch"), Modifier.padding(top = 16.dp, bottom = 6.dp))
        AttemptSet("Snatch", snatch) { i, row -> snatch = replaceAt(snatch, i, row) }
        Overline(t("Clean & jerk"), Modifier.padding(top = 14.dp, bottom = 6.dp))
        AttemptSet("Clean & jerk", cj) { i, row -> cj = replaceAt(cj, i, row) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeetTile(t("Snatch"), bestS, unit, Modifier.weight(1f))
            MeetTile(t("Clean & jerk"), bestC, unit, Modifier.weight(1f))
            MeetTile(t("Total"), total, unit, Modifier.weight(1f))
        }

        Overline(t("Note"), Modifier.padding(top = 16.dp, bottom = 6.dp))
        NoteField(
            value = note,
            onChange = { note = it },
            placeholder = t("How the meet went…"),
            minLines = 2,
        )

        Button(
            text = if (editing) t("Save competition") else t("Add a competition"),
            onClick = {
                val record = js(
                    "id" to (meet?.str("id") ?: uid()),
                    "d" to date,
                    "name" to name.trim(),
                    "place" to place.trim(),
                    "class" to cls,
                    "bw" to bw,
                    "snatch" to draftSnatch,
                    "cj" to draftCj,
                    "placing" to placing,
                    "note" to note.trim(),
                )
                editProfile { raw -> raw.with("competitions", upsertMeet(raw["competitions"], record)) }
                close()
                ui.toast(if (editing) t("Competition saved") else t("Competition added"))
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/** Three rows of [n][weight][good or no lift], the shape the form always shows. */
@Composable
private fun AttemptSet(lift: String, attempts: List<JsonObject>, onChange: (Int, JsonObject) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        attempts.forEachIndexed { i, attempt ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = (i + 1).toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp),
                )
                Stepper(
                    value = attempt.num("w") ?: 0.0,
                    onChange = { onChange(i, attempt.with("w", it)) },
                    step = 1.0,
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                Segmented(
                    options = listOf("good", "miss"),
                    value = if (attempt.bool("made") != false) "good" else "miss",
                    onChange = { onChange(i, attempt.with("made", it == "good")) },
                    labels = listOf(t("Good"), t("No lift")),
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }
}

@Composable
private fun MeetTile(label: String, value: Double?, unit: String, modifier: Modifier = Modifier) {
    Tile(
        label = label,
        value = if (value == null) "—" else fmtNum(value),
        suffix = if (value == null) null else unit,
        modifier = modifier,
    )
}

private fun replaceAt(list: List<JsonObject>, index: Int, value: JsonObject): List<JsonObject> =
    list.mapIndexed { i, row -> if (i == index) value else row }

@Composable
private fun MeetDetail(meet: JsonObject, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val total = totalOf(meet)
    val bestS = bestAttempt(meet["snatch"])
    val bestC = bestAttempt(meet["cj"])
    val when0 = daysUntil(meet, todayISO())
    val name = meet.str("name")?.takeIf { it.isNotEmpty() } ?: t("Competition")

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(Glyph.MORE, onClick = {
                menuSheet(
                    title = name,
                    items = listOf(
                        MenuItem(label = t("Edit"), icon = Glyph.PENCIL, onClick = {
                            close()
                            meetSheet(meet)
                        }),
                        MenuItem(label = t("Delete"), icon = Glyph.TRASH, danger = true, onClick = {
                            confirmSheet(
                                title = t("Delete this competition?"),
                                message = t("The meet and its attempts are removed. Your training log is untouched."),
                                confirmText = t("Delete"),
                                danger = true,
                                onConfirm = {
                                    editProfile { raw -> raw.with("competitions", removeMeet(raw["competitions"], meet.str("id"))) }
                                    close()
                                    ui.toast(t("Competition deleted"))
                                },
                            )
                        }),
                    ),
                )
            })
        }

        val meta = buildString {
            append(fmtDate(meet.str("d").orEmpty(), long = true, withYear = true))
            meet.str("place")?.takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
            if (when0 != null && when0 >= 0) {
                append(" · ").append(
                    when (when0) {
                        0 -> t("Today")
                        1 -> t("Tomorrow")
                        else -> t("in {0} days", when0)
                    },
                )
            }
        }
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        val cls = meet.str("class")
        if (!cls.isNullOrEmpty()) {
            val line = buildString {
                append(t("Weight class")).append(" ").append(cls).append(" ").append(unit)
                meet.num("bw")?.let { append(" · ").append(t("Bodyweight")).append(" ").append(fmtNum(it)).append(" ").append(unit) }
                meet.num("placing")?.let { append(" · ").append(t("Placing")).append(" ").append(fmtNum(it)) }
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeetTile(t("Snatch"), bestS, unit, Modifier.weight(1f))
            MeetTile(t("Clean & jerk"), bestC, unit, Modifier.weight(1f))
            MeetTile(t("Total"), total, unit, Modifier.weight(1f))
        }

        LiftBlock(t("Snatch"), meet["snatch"], unit)
        LiftBlock(t("Clean & jerk"), meet["cj"], unit)

        val note = meet.str("note").orEmpty()
        if (note.isNotEmpty()) {
            Overline(t("Note"), Modifier.padding(top = 16.dp, bottom = 6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LiftBlock(lift: String, attempts: kotlinx.serialization.json.JsonElement?, unit: String) {
    val rows = (attempts as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    Overline(lift, Modifier.padding(top = 16.dp, bottom = 6.dp))
    if (rows.isEmpty()) {
        Text(
            text = t("No attempts logged."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Section {
        rows.forEachIndexed { i, attempt ->
            val made = attemptMade(attempt)
            ListRow(
                title = t("Attempt {0}", i + 1),
                icon = if (made) Glyph.CHECK else Glyph.XMARK,
                subtitle = fmtNum(attempt.num("w") ?: 0.0) + " " + unit,
                value = if (made) t("Good lift") else t("No lift"),
                danger = !made,
            )
        }
    }
}

/**
 * The federation's categories, editable, one list per body. They change every few years and the app
 * must not need a release to follow; a meet keeps the string it was saved with, so editing this list
 * never rewrites a logged result.
 */
@Composable
private fun WeightClasses(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val S = profile.raw
    var both by remember { mutableStateOf(classLists(S["classes"])) }
    var who by remember { mutableStateOf(if (S.str("body") == "female") "female" else "male") }
    val list = if (who == "female") both.female else both.male
    val setList: ((List<String>) -> List<String>) -> Unit = { f ->
        both = if (who == "female") both.copy(female = f(both.female)) else both.copy(male = f(both.male))
    }

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Weight classes"))
        Text(
            text = t("What your federation runs right now. A meet keeps the category it was logged with."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Segmented(
            options = listOf("male", "female"),
            value = who,
            onChange = { who = it },
            labels = listOf(t("Male"), t("Female")),
        )
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            list.forEachIndexed { i, category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NoteField(
                        value = category,
                        onChange = { typed -> setList { l -> l.mapIndexed { j, x -> if (j == i) typed else x } } },
                        placeholder = "73",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(Glyph.TRASH, onClick = { setList { l -> l.filterIndexed { j, _ -> j != i } } })
                }
            }
            if (list.isEmpty()) {
                Text(
                    text = t("No categories yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            text = t("Add a category"),
            onClick = { setList { it + "" } },
            icon = Glyph.PLUS,
            modifier = Modifier.padding(top = 10.dp),
        )
        Button(
            text = t("Save"),
            onClick = {
                editProfile { raw ->
                    raw.with("classes", js("male" to both.male, "female" to both.female))
                }
                close()
                ui.toast(t("Weight classes saved"))
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

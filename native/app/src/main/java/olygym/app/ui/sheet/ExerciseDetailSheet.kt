package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.bestWeightFor
import olygym.app.lib.capWords
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.isFav
import olygym.app.lib.lastEntryFor
import olygym.app.lib.setLabel
import olygym.app.lib.smOf
import olygym.app.lib.toggledFavs
import olygym.app.lib.usesBar
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.ExerciseMedia
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Section
import olygym.app.ui.components.Tag
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.theme.extraColors
import olygym.app.ui.ui

/**
 * What an exercise is: what it hits, your best and your last, the bar, and how to do it. A port of
 * ExerciseDetail in frontend/src/sheets.jsx.
 *
 * This is where the favourite star lives — the picker reads the list and sorts it, and this sheet
 * is the one place that sets it. Not ported: the demo media at the top, the muscle map at the foot,
 * and the row through to the exercise history sheet, which needs the charts (its own phase).
 */
fun exerciseDetailSheet(ex: Exercise) {
    ui.openSheet { close -> ExerciseDetailSheet(ex, close) }
}

@Composable
private fun ExerciseDetailSheet(ex: Exercise, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val raw = profile.raw
    val unit = profile.settings.unit
    val last = lastEntryFor(raw, ex.id)
    val best = bestWeightFor(raw, ex.id)
    val favourite = isFav(raw, ex.id)
    // The dataset's first instruction is the exercise's own description, which is already above:
    // printing the same sentence twice was this sheet's worst habit.
    val steps = olygym.app.lib.I18nCore.instrFor(ex.id, ex.st)
    val how = if (steps.firstOrNull() == ex.desc) steps.drop(1) else steps
    val lastTop = last?.arr("sets")?.mapNotNull { it.asObj()?.num("w") }?.maxOrNull() ?: 0.0
    val lastLine = last?.arr("sets")?.joinToString(", ") { setLabel(ex.id, it, last.obj("target")) }.orEmpty()
    val actions = if (ex.custom) {
        listOf(
            MenuItem(
                label = t("Edit this exercise"),
                icon = Glyph.PENCIL,
                onClick = {
                    close()
                    customExSheet(ex)
                },
            ),
            MenuItem(
                label = t("Delete this exercise"),
                icon = Glyph.TRASH,
                danger = true,
                onClick = {
                    close()
                    deleteCustomEx(ex)
                },
            ),
        )
    } else {
        emptyList()
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = capWords(Catalogue.nameOf(ex.id)),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                glyph = if (favourite) Glyph.STAR_FILLED else Glyph.STAR,
                onClick = {
                    val next = toggledFavs(raw, ex.id)
                    editProfile { it.with("favEx", next) }
                    ui.toast(
                        if (favourite) t("Removed from favourites") else t("Added to favourites")
                    )
                },
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (actions.isNotEmpty()) {
                IconButton(Glyph.MORE, onClick = {
                    menuSheet(title = Catalogue.nameOf(ex.id), subtitle = t("Your own exercise"), items = actions)
                })
            }
        }

        ExerciseMedia(ex, Modifier.padding(top = 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ex.bp?.let { Tag(t(it), accent = true) }
            (if (ex.primaries.isNotEmpty()) ex.primaries else listOfNotNull(ex.tg))
                .forEach { Tag(t(MUSCLE_NAME[it] ?: it)) }
            ex.eq?.let { Tag(t(it)) }
            (if (ex.secondaries.isNotEmpty()) ex.secondaries else smOf(ex)).take(3)
                .forEach { Tag(t(MUSCLE_NAME[it] ?: it)) }
        }

        ex.desc?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        if (last != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (best > 0) {
                    Tile(
                        label = t("Best"),
                        value = fmtNum(best) + " " + unit,
                        modifier = Modifier.weight(1f),
                    )
                }
                Tile(
                    label = t("Last"),
                    value = if (lastTop > 0) fmtNum(lastTop) + " " + unit else lastLine,
                    suffix = fmtDate(last.str("d").orEmpty(), long = false),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The way to the full history: a row with a chevron rather than a button, and only once
        // there is one, which is also when "your last" above has something to say.
        if (last != null) {
            Section(modifier = Modifier.padding(bottom = 10.dp)) {
                ListRow(
                    title = t("History"),
                    icon = Glyph.HISTORY,
                    accessory = Accessory.CHEVRON,
                    onClick = { exerciseHistorySheet(ex.id) },
                )
            }
        }

        if (usesBar(olygym.app.data.js("id" to ex.id, "eq" to ex.eq))) {
            Overline(t("Bar"), Modifier.padding(bottom = 6.dp))
            BarWeightEditor(
                ex.id,
                t("You still log the total weight — the bar only feeds the per-side plate math."),
            )
        }

        if (how.isNotEmpty()) {
            Overline(t("How to"), Modifier.padding(top = 16.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth()) {
                how.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = (index + 1).toString() + ".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

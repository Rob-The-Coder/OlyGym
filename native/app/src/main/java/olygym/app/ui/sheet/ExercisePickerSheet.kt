package olygym.app.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.lib.LibraryResult
import olygym.app.lib.activeProfile
import olygym.app.lib.allExercises
import olygym.app.lib.exAvailable
import olygym.app.lib.favIds
import olygym.app.lib.isFav
import olygym.app.lib.libraryResults
import olygym.app.lib.sortFavouritesFirst
import olygym.app.lib.usageMap
import olygym.app.ui.components.Button
import olygym.app.ui.components.Chip
import olygym.app.ui.components.ExerciseRow
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.SearchField
import olygym.app.ui.components.Tag
import olygym.app.ui.components.Thumb
import olygym.app.ui.components.exerciseSubtitle
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.theme.FullShape
import olygym.app.ui.ui

/** The two shortcut views over the catalogue. They are views, not filters: the filters then apply. */
private const val FAVOURITES = "favourites"
private const val CHOSEN = "chosen"

/**
 * The exercise picker: search, the two shortcuts, and every lift in the catalogue with the ones you
 * already train marked. A port of ExercisePicker in frontend/src/sheets.jsx.
 *
 * onPick is called with the exercise and whether the row's plus was used — the quick add commits
 * with the default config, while tapping the row opens the config sheet first.
 *
 * Not ported yet, and stated where it would show: the demo thumbnail (media is its own phase, so a
 * row gets the app's own glyph) and the By muscle explorer with the body-part and equipment filters.
 */
fun exercisePicker(onPick: (Exercise, Boolean) -> Unit): Long =
    ui.openSheet { close -> ExercisePicker(onPick, close) }

@Composable
private fun ExercisePicker(onPick: (Exercise, Boolean) -> Unit, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val raw = profile.raw
    val usage = usageMap(raw)
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(50) }

    val all = allExercises(profile.customEx)
    val favouriteCount = favIds(raw).size
    val chosenCount = usage.size
    val scope = when (view) {
        FAVOURITES -> all.filter { isFav(raw, it.id) }
        CHOSEN -> all.filter { usage.containsKey(it.id) }
        else -> all
    }
    val profileActive = activeProfile(raw) != null
    val filtered: ((Exercise) -> Boolean)? = if (profileActive && !showAll) {
        { ex: Exercise -> exAvailable(raw, ex) }
    } else {
        null
    }
    val result: LibraryResult = libraryResults(all = scope, q = query, available = filtered)
    val listed = if (view == CHOSEN) {
        result.list.sortedWith(
            compareByDescending<Exercise> { usage[it.id] ?: 0 }.thenBy { Catalogue.nameOf(it.id) }
        )
    } else {
        sortFavouritesFirst(result.list, raw)
    }
    val special = view == FAVOURITES || view == CHOSEN

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Add exercise"))

        SearchField(
            value = query,
            onChange = {
                query = it
                shown = 50
            },
            placeholder = t("Search {0} exercises…", all.size),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (favouriteCount > 0) {
                Chip(
                    label = t("Favourites") + " (" + favouriteCount + ")",
                    on = view == FAVOURITES,
                    onClick = {
                        view = if (view == FAVOURITES) "" else FAVOURITES
                        shown = 50
                    },
                )
            }
            if (chosenCount > 0) {
                Chip(
                    label = t("Chosen") + " (" + chosenCount + ")",
                    on = view == CHOSEN,
                    onClick = {
                        view = if (view == CHOSEN) "" else CHOSEN
                        shown = 50
                    },
                )
            }
            if (profileActive && !showAll) {
                Chip(label = t("My equipment"), on = true, onClick = { showAll = true })
            }
        }

        if (!special) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { customExSheet(null, { created -> onPick(created, false) }, query.trim()) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Thumb(null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t("Create your own exercise"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = t("name + body part, no video"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlyphIcon(Glyph.PLUS, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listed.take(shown).forEach { ex ->
                ExerciseRow(
                    ex = ex,
                    subtitle = exerciseSubtitle(ex),
                    favourite = isFav(raw, ex.id),
                    onClick = {
                        close()
                        onPick(ex, false)
                    },
                    trailing = {
                        if (usage.containsKey(ex.id)) Tag(t("Chosen"), accent = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(FullShape)
                                .clickable {
                                    close()
                                    onPick(ex, true)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                Glyph.PLUS,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }

        if (listed.isEmpty() && view == CHOSEN) {
            EmptyNote(t("Nothing chosen yet — add exercises and they’ll show up here."))
        }
        if (listed.isEmpty() && view == FAVOURITES) {
            EmptyNote(t("No favourites here — tap the star on an exercise to add it."))
        }

        if (listed.size > shown) {
            Button(
                text = t("Show more"),
                onClick = { shown += 50 },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}


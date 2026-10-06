package olygym.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Exercise
import olygym.app.data.Profile
import olygym.app.data.str
import olygym.app.lib.EXDB
import olygym.app.lib.LibraryResult
import olygym.app.lib.activeProfile
import olygym.app.lib.allExercises
import olygym.app.lib.exAvailable
import olygym.app.lib.isFav
import olygym.app.lib.libraryResults
import olygym.app.lib.sortFavouritesFirst
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.BestWeightTag
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Chip
import olygym.app.ui.components.ExerciseRow
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.SearchField
import olygym.app.ui.components.Thumb
import olygym.app.ui.components.exerciseSubtitle
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.sheet.FilterChoice
import olygym.app.ui.sheet.customExSheet
import olygym.app.ui.sheet.exerciseDetailSheet
import olygym.app.ui.sheet.libraryFilterSheet
import olygym.app.ui.t

/**
 * The catalogue: the Exercises tab, a port of frontend/src/views/Library.jsx. The search field, the
 * one filter sheet behind its chip, the list with a best weight where there is one, and the door to
 * the By-muscle explorer.
 *
 * The web paginates at 40 rows with a Show more button because six hundred DOM nodes are expensive;
 * a LazyColumn builds only what is on screen, so the button is gone and the list is all there. The
 * demo thumbnails and the exercise history sheet are their own phases, as in the picker.
 */
object LibraryScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        when (val s = state) {
            is AppState.Ready -> Library(s.profile)
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = t("Not ported yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Library(profile: Profile) {
    val S = profile.raw
    val scroll = olyAppBarScrollBehavior()
    var query by remember { mutableStateOf("") }
    var bp by remember { mutableStateOf("") }
    var eq by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val all = allExercises(profile.customEx)
    val kit = activeProfile(S)

    // One function answers for any candidate filters, and both the screen and the sheet go through
    // it, so the count on the sheet button is the count you get, never an estimate of it.
    fun resultsFor(choice: FilterChoice): LibraryResult = libraryResults(
        all = all,
        q = query,
        bp = choice.bp,
        eq = choice.eq,
        available = if (kit != null && !choice.showAll) {
            { ex: Exercise -> exAvailable(S, ex) }
        } else {
            null
        },
    )

    val result = resultsFor(FilterChoice(bp, eq, showAll))
    // Favourites float to the top of whatever the filters left (issue #6), the rest keeps its order.
    val listed = sortFavouritesFirst(result.list, S)

    // Everything narrowing the list, as a chip you can drop without reopening the sheet. The active
    // equipment profile is one of them.
    val applied: List<Pair<String, () -> Unit>> = buildList {
        if (bp.isNotEmpty()) {
            add(t(bp) to {
                bp = ""
                eq = ""
            })
        }
        if (result.eq.isNotEmpty()) {
            add(t(result.eq) to { eq = "" })
        }
        if (kit != null && !showAll) {
            add(kit.str("name").orEmpty() to { showAll = true })
        }
    }

    fun openFilters() {
        libraryFilterSheet(
            bp = bp,
            eq = result.eq,
            showAll = showAll,
            profileName = kit?.str("name"),
            describeFor = { choice -> resultsFor(choice) },
            onApply = { choice ->
                bp = choice.bp
                eq = choice.eq
                showAll = choice.showAll
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Exercises"),
                subtitle = t("{0} exercises with video demos", EXDB.size),
                scrollBehavior = scroll,
                actions = {
                    Button(
                        text = t("By muscle"),
                        onClick = { Nav.to(MuscleExplorerScreen) },
                        variant = ButtonVariant.TINTED,
                        size = ButtonSize.SM,
                        icon = Glyph.TARGET,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            item {
                SearchField(value = query, onChange = { query = it }, placeholder = t("Search…"))
            }
            item {
                FilterBar(count = listed.size, appliedCount = applied.size, onOpen = { openFilters() })
            }
            if (applied.isNotEmpty()) {
                item { AppliedFilters(applied) }
            }
            item { CreateExerciseRow(query) { ex -> exerciseDetailSheet(ex) } }
            items(listed, key = { it.id }) { ex ->
                ExerciseRow(
                    ex = ex,
                    subtitle = exerciseSubtitle(ex),
                    favourite = isFav(S, ex.id),
                    onClick = { exerciseDetailSheet(ex) },
                    trailing = { BestWeightTag(S, ex.id) },
                )
            }
            if (listed.isEmpty()) {
                item {
                    NoMatch(applied.isNotEmpty()) {
                        bp = ""
                        eq = ""
                        showAll = true
                    }
                }
            }
        }
    }
}

/** The catalogue own way in: a row that becomes the custom-exercise form, then that exercise detail. */
@Composable
private fun CreateExerciseRow(query: String, onCreated: (Exercise) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { customExSheet(null, onCreated, query.trim()) }
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

/** How many results there are, and the one chip to the sheet that narrows them. */
@Composable
internal fun FilterBar(count: Int, appliedCount: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = t("{0} exercises", count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Chip(
            label = if (appliedCount > 0) t("Filters") + " · " + appliedCount else t("Filters"),
            onClick = onOpen,
            trailing = Glyph.CHEVRON_DOWN,
        )
    }
}

/** Everything narrowing the list, in the same order it was applied. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppliedFilters(applied: List<Pair<String, () -> Unit>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        applied.forEach { (label, clear) ->
            Chip(label = label, onClick = clear, on = true, trailing = Glyph.XMARK)
        }
    }
}

/** Nothing matched: the same magnifier the picker uses, and the one tap that undoes all of it. */
@Composable
internal fun NoMatch(hasFilters: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            Glyph.MAGNIFIER,
            Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = t("No match"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (hasFilters) {
            Button(
                text = t("Clear filters"),
                onClick = onClear,
                variant = ButtonVariant.PLAIN,
                size = ButtonSize.SM,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

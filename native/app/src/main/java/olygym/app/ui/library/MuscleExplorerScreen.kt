package olygym.app.ui.library

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Profile
import olygym.app.data.str
import olygym.app.lib.LibraryResult
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.MUSCLES
import olygym.app.lib.activeProfile
import olygym.app.lib.allExercises
import olygym.app.lib.exAvailable
import olygym.app.lib.isFav
import olygym.app.lib.libraryResults
import olygym.app.lib.muscleCounts
import olygym.app.lib.muscleWeightOf
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
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.SectionCard
import olygym.app.ui.components.SearchField
import olygym.app.ui.components.exerciseSubtitle
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.sheet.FilterChoice
import olygym.app.ui.sheet.exerciseDetailSheet
import olygym.app.ui.sheet.libraryFilterSheet
import olygym.app.ui.t

/**
 * The By-muscle explorer — a port of frontend/src/views/Muscles.jsx and the MuscleExplorer inside
 * it. Pick a muscle, see the count of what trains it, then narrow that list with the same filters
 * and the same rows the Library uses.
 *
 * The body silhouette the web draws above the chips is not here: it is ~90 KB of SVG paths and a
 * path renderer, which is the same deliberate omission the Stats balance and fatigue views carry.
 * The chips below it already say which muscle is which and how many exercises train it.
 */
object MuscleExplorerScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        when (val s = state) {
            is AppState.Ready -> MuscleExplorer(s.profile)
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
private fun MuscleExplorer(profile: Profile) {
    val S = profile.raw
    val scroll = olyAppBarScrollBehavior()
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var bp by remember { mutableStateOf("") }
    var eq by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val all = allExercises(profile.customEx)
    val kit = activeProfile(S)
    val muscle = selected
    // The active equipment profile narrows the catalogue here, so the per-muscle counts above the
    // list count what this profile can actually do, exactly as the web reads them.
    val catalog = if (kit != null && !showAll) all.filter { exAvailable(S, it) } else all
    val counts = muscleCounts(catalog)
    val targeted = muscle?.let { m -> catalog.filter { muscleWeightOf(it, m) > 0.0 } }.orEmpty()

    // The same pipeline the Library runs, out of the same helper. The profile has already narrowed
    // catalog, so there is nothing left for the availability predicate to do on this screen.
    fun resultsFor(choice: FilterChoice): LibraryResult =
        libraryResults(all = targeted, q = query, bp = choice.bp, eq = choice.eq)

    val result = resultsFor(FilterChoice(bp, eq, showAll))
    val listed = sortFavouritesFirst(result.list, S)

    // Body part and equipment only mean something once a muscle has been picked, so they wait until
    // then; the profile is narrowing the counts above, so it shows either way.
    val applied: List<Pair<String, () -> Unit>> = buildList {
        if (kit != null && !showAll) add(kit.str("name").orEmpty() to { showAll = true })
        if (muscle != null && bp.isNotEmpty()) {
            add(t(bp) to {
                bp = ""
                eq = ""
            })
        }
        if (muscle != null && result.eq.isNotEmpty()) add(t(result.eq) to { eq = "" })
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
                title = t("Explore muscles"),
                subtitle = t("Choose a muscle to see exercises that train it."),
                scrollBehavior = scroll,
                leading = { IconButton(Glyph.CHEVRON_LEFT, onClick = { Nav.back() }) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            item {
                SectionCard {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MUSCLES.forEach { m ->
                            Chip(
                                label = t(MUSCLE_NAME[m] ?: m) + " " + (counts[m] ?: 0),
                                on = selected == m,
                                onClick = {
                                    selected = if (selected == m) null else m
                                    eq = ""
                                },
                            )
                        }
                    }
                }
            }
            if (applied.isNotEmpty()) {
                item { AppliedFilters(applied) }
            }
            if (muscle != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Overline(t("Exercises for {0}", t(MUSCLE_NAME[muscle] ?: muscle)), Modifier.weight(1f))
                        Button(
                            text = t("Clear selection"),
                            onClick = { selected = null },
                            variant = ButtonVariant.GHOST,
                            size = ButtonSize.SM,
                        )
                    }
                }
                item {
                    SearchField(value = query, onChange = { query = it }, placeholder = t("Search…"))
                }
                item {
                    FilterBar(count = listed.size, appliedCount = applied.size, onOpen = { openFilters() })
                }
                items(listed, key = { it.id }) { ex ->
                    ExerciseRow(
                        ex = ex,
                        subtitle = t(
                            if (muscleWeightOf(ex, muscle) >= 1.0) "Primary target" else "Also trains",
                        ) + " · " + exerciseSubtitle(ex),
                        favourite = isFav(S, ex.id),
                        onClick = { exerciseDetailSheet(ex) },
                        trailing = { BestWeightTag(S, ex.id) },
                    )
                }
                if (listed.isEmpty()) {
                    item { NoMatch(false) {} }
                }
            }
        }
    }
}

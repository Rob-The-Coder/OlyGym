package olygym.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Profile
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.editArray
import olygym.app.data.editAt
import olygym.app.data.editObject
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.objectAt
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.DAYN
import olygym.app.lib.bestWeightForEntry
import olygym.app.lib.canMoveActiveWorkoutUnit
import olygym.app.lib.cascadeWeight
import olygym.app.lib.defaultIncrement
import olygym.app.lib.effectiveDay
import olygym.app.lib.exCount
import olygym.app.lib.fmtDate
import olygym.app.lib.insertWarmupRow
import olygym.app.lib.isWarmupRow
import olygym.app.lib.modeOf
import olygym.app.lib.moveActiveWorkoutUnit
import olygym.app.lib.nextUnfinishedUnit
import olygym.app.lib.pairAdjacent
import olygym.app.lib.removeRowAt
import olygym.app.lib.restAfterSet
import olygym.app.lib.restOnRecheck
import olygym.app.lib.restSecFor
import olygym.app.lib.setProgressHighWater
import olygym.app.lib.setUnitsTotal
import olygym.app.lib.setsDoneActive
import olygym.app.lib.supersetFlowStep
import olygym.app.lib.supersetUnits
import olygym.app.lib.todayISO
import olygym.app.lib.unitOf
import olygym.app.lib.unpairSuperset
import olygym.app.lib.warmupRestSecFor
import olygym.app.lib.weekFor
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.WaveProgress
import olygym.app.ui.components.Section
import olygym.app.ui.components.SectionCard
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.profileNow
import olygym.app.ui.sheet.MenuItem
import olygym.app.ui.sheet.barWeightSheet
import olygym.app.ui.sheet.chooseSheet
import olygym.app.ui.sheet.confirmSheet
import olygym.app.ui.sheet.exerciseDetailSheet
import olygym.app.ui.sheet.exerciseNoteSheet
import olygym.app.ui.sheet.menuSheet
import olygym.app.ui.sheet.renameWorkoutSheet
import olygym.app.ui.sheet.sessionNoteSheet
import olygym.app.ui.sheet.workoutCompleteSheet
import olygym.app.ui.t
import olygym.app.ui.ui

/** Everything the blocks of a session need to write back, in one object rather than fourteen args. */
private class BlockActions(
    val onToggle: (Int, Int) -> Unit,
    val onField: (Int, Int, String, Double?) -> Unit,
    val onAddSet: (Int) -> Unit,
    val onAddWarmup: (Int) -> Unit,
    val onRemoveSetAt: (Int, Int) -> Unit,
    val onStartTimed: (Int, Int) -> Unit,
    val onBarWeight: (Int) -> Unit,
    val onNote: (Int) -> Unit,
    val onRemoveExercise: (Int) -> Unit,
    val onDetails: (Int) -> Unit,
    val onProgression: (Int) -> Unit,
    val onSwap: (Int) -> Unit,
    val onPair: (Int, Int) -> Unit,
    val onUnpair: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
)

/** The session screen: the day's plan if nothing is running, the running session if something is. */
object WorkoutScreen : AppScreen() {
    @Composable
    override fun Content() {
        val profile = currentProfile() ?: return
        if (profile.active == null) StartChooser() else ActiveWorkout(profile)
    }
}

/* ------------------------------------------------------------- start chooser -- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartChooser() {
    val profile = currentProfile() ?: return
    val scroll = olyAppBarScrollBehavior()
    val today = effectiveDay(profile.raw, todayISO())
    val others = weekFor(profile.weeks, todayISO())?.days.orEmpty().filter { it != today && it.ex.isNotEmpty() }
    val weekday = DAYN[java.time.LocalDate.now().dayOfWeek.value % 7]

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Start workout"),
                scrollBehavior = scroll,
                subtitle = t(weekday) + " — " + if (today != null) t("today is {0}", today.name) else t("rest day, but no one’s stopping you"),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (today != null && today.ex.isNotEmpty()) {
                SectionCard {
                    Text(
                        text = t("Today's plan"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = today.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = exCount(today.ex.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        text = t("Start {0}", today.name),
                        onClick = { startFlow(today) },
                        variant = ButtonVariant.PRIMARY,
                        icon = Glyph.PLAY,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (others.isNotEmpty()) {
                Overline(t("This week"), Modifier.padding(top = 12.dp, bottom = 4.dp))
                Section {
                    others.forEachIndexed { index, day ->
                        if (index > 0) RowDivider()
                        ListRow(
                            title = day.name,
                            icon = Glyph.DUMBBELL,
                            subtitle = exCount(day.ex.size),
                            accessory = Accessory.NONE,
                            onClick = { startFlow(day) },
                        )
                    }
                }
            }
            // The picker is the next phase: a session with no plan behind it cannot be filled in yet,
            // so this says so rather than opening an empty session with no way to add anything.
            Button(
                text = t("Freestyle workout (pick as you go)"),
                onClick = { startFlow(null) },
                icon = Glyph.SHUFFLE,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/* ------------------------------------------------------------ active session -- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkout(profile: Profile) {
    val active = profile.active ?: return
    val entries = profile.entries
    // The units come back as JSON — they are what the flow helpers take — and are read as index
    // lists here, which is what a layout needs.
    val unitsJson = supersetUnits(active.arr("entries"))
    val units: List<List<Int>> = unitsJson.mapNotNull { element -> element.asArr()?.mapNotNull { it.asNum()?.toInt() } }
    val cur = (active.int("cur") ?: 0).coerceIn(0, maxOf(0, entries.size - 1))
    val unit: List<Int> = if (entries.isNotEmpty()) {
        unitOf(unitsJson, cur).mapNotNull { it.asNum()?.toInt() }
    } else {
        emptyList()
    }
    val unitIndex = units.indexOfFirst { it.contains(cur) }
    val workoutView = active.str("workoutView") ?: profile.raw.str("workoutView") ?: "cards"
    val listMode = workoutView == "list" || workoutView == "compact"
    val dense = workoutView == "compact"
    val total = setUnitsTotal(active.arr("entries"))
    val done = setsDoneActive(active)
    val backfill = active.obj("backfill") != null
    val sessionName = active.str("name") ?: t("Freestyle")
    val scroll = olyAppBarScrollBehavior()
    val clock = elapsedLabel(active.num("start") ?: 0.0)

    // The mark a set has to pass to count as new work. Index-keyed, so it re-baselines whenever an
    // exercise is removed and the indexes below it shift — otherwise a shifted exercise inherits its
    // predecessor's mark and its real progress reads as a re-check.
    val highWater = remember(entries.size) {
        mutableStateListOf(*entries.map { entry -> entry.arr("sets").count { it.asObj()?.bool("done") == true } }.toTypedArray())
    }

    val actions = BlockActions(
        onToggle = { index, row -> toggle(index, row, highWater) },
        onField = { index, row, field, value -> setField(index, row, field, value) },
        onAddSet = { index -> addSet(index) },
        onAddWarmup = { index -> addWarmup(index) },
        onRemoveSetAt = { index, row -> removeSetAt(index, row) },
        onStartTimed = { index, row -> startTimed(index, row) },
        onBarWeight = { index -> barWeightSheet(entries[index].str("id").orEmpty()) },
        onNote = { index -> exerciseNoteSheet(index) },
        onRemoveExercise = { index -> removeExerciseSheet(index) },
        onDetails = { index -> Catalogue[entries[index].str("id").orEmpty()]?.let { exerciseDetailSheet(it) } },
        onProgression = { index -> openProgressionSettings(index) },
        onSwap = { index -> swapActiveWorkoutExercise(index) },
        onPair = { first, second -> pairAt(first, second) },
        onUnpair = { index -> unpairAt(index) },
        onMove = { index, direction -> moveUnit(index, direction) },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = sessionName,
                scrollBehavior = scroll,
                subtitle = (if (backfill) fmtDate(active.str("d").orEmpty(), long = true) else clock) +
                    " · " + t("{0} sets", done.toString() + "/" + total),
                leading = {
                    IconButton(Glyph.XMARK, onClick = {
                        confirmSheet(
                            title = t("Discard workout?"),
                            message = t("The sets you logged in this session will be lost."),
                            confirmText = t("Discard"),
                            danger = true,
                            onConfirm = {
                                editProfile { it.with("active", null) }
                                ui.stopRest()
                                ui.stopWork()
                                Nav.goHome()
                            },
                        )
                    })
                },
                actions = {
                    IconButton(Glyph.MORE, onClick = openViewMenu)
                    IconButton(Glyph.CHECK, onClick = { finishWorkout() }, tint = MaterialTheme.colorScheme.primary)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // The sets-done bar, wavy: the web's .wprog, and the one other real progress
            // indicator in the app. The muscle-balance bars are data, and stay straight.
            WaveProgress(
                fraction = if (total > 0) done.toFloat() / total else 0f,
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            if (backfill) {
                Text(
                    text = t("Logging a past workout — no rest timers."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            when {
                entries.isEmpty() -> SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = t("Freestyle workout — add your first exercise."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                listMode -> units.forEachIndexed { index, u ->
                    val isCur = u.contains(cur)
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (u.size > 1) {
                                    t("Complex {0} / {1}", index + 1, units.size)
                                } else {
                                    t("Exercise {0} / {1}", index + 1, units.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCur) {
                                Text(
                                    text = t("Current"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Button(
                                    text = t("Set current"),
                                    onClick = { focusUnit(u.first()) },
                                    variant = ButtonVariant.TINTED,
                                    size = ButtonSize.XS,
                                )
                            }
                        }
                        UnitBlocks(entries, u, dense, actions, busy = ui.state.value.work != null)
                    }
                }

                else -> {
                    Text(
                        text = if (unit.size > 1) {
                            t("Complex {0} / {1}", unitIndex + 1, units.size)
                        } else {
                            t("Exercise {0} / {1}", unitIndex + 1, units.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    UnitBlocks(entries, unit, dense, actions, busy = ui.state.value.work != null)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            text = t("Prev"),
                            onClick = { navigateUnit(-1) },
                            icon = Glyph.CHEVRON_LEFT,
                            enabled = unitIndex > 0,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            text = t("Next"),
                            onClick = { navigateUnit(1) },
                            trailingIcon = Glyph.CHEVRON_RIGHT,
                            enabled = unitIndex in 0 until units.size - 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Button(
                text = t("Add exercise"),
                onClick = { addExerciseFlow() },
                icon = Glyph.PLUS,
                modifier = Modifier.padding(top = 14.dp),
            )
            Button(
                text = t("Swap exercise"),
                onClick = { swapActiveWorkoutExercise(cur) },
                size = ButtonSize.SM,
                icon = Glyph.SHUFFLE,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    text = t("Move up"),
                    onClick = { moveUnit(cur, -1) },
                    size = ButtonSize.SM,
                    icon = Glyph.CHEVRON_UP,
                    enabled = canMoveActiveWorkoutUnit(profileNow()?.active, cur, -1),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = t("Move down"),
                    onClick = { moveUnit(cur, 1) },
                    size = ButtonSize.SM,
                    trailingIcon = Glyph.CHEVRON_DOWN,
                    enabled = canMoveActiveWorkoutUnit(profileNow()?.active, cur, 1),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                text = if (active.str("note").isNullOrBlank()) t("Add session note") else t("Edit session note"),
                onClick = { sessionNoteSheet() },
                size = ButtonSize.SM,
                variant = if (active["note"] != null) ButtonVariant.TINTED else ButtonVariant.PLAIN,
                icon = Glyph.PENCIL,
                modifier = Modifier.padding(top = 10.dp),
            )
            val exercisesDone = entries.count { entry ->
                entry.arr("sets").isNotEmpty() && entry.arr("sets").all { it.asObj()?.bool("done") == true }
            }
            val allDone = entries.isNotEmpty() && exercisesDone == entries.size
            Button(
                text = if (allDone) {
                    t("Finish workout")
                } else {
                    t("Finish workout early · {0} exercises", exercisesDone.toString() + "/" + entries.size)
                },
                onClick = { finishWorkout() },
                variant = if (allDone) ButtonVariant.PRIMARY else ButtonVariant.GHOST,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
            )
        }
    }
}

/** The blocks of one unit: a complex says so at the top and pairs its members' tables together. */
@Composable
private fun UnitBlocks(
    entries: List<JsonObject>,
    unit: List<Int>,
    dense: Boolean,
    actions: BlockActions,
    busy: Boolean,
) {
    if (unit.size > 1) {
        SectionCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("Complex"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = t("Unpair"),
                    onClick = { actions.onUnpair(unit.first()) },
                    variant = ButtonVariant.GHOST,
                    size = ButtonSize.XS,
                    icon = Glyph.LINK,
                )
            }
        }
    }
    unit.forEachIndexed { position, index ->
        ExerciseBlock(
            entryIdx = index,
            onToggle = { row -> actions.onToggle(index, row) },
            onField = { row, field, value -> actions.onField(index, row, field, value) },
            onAddSet = { actions.onAddSet(index) },
            onAddWarmup = { actions.onAddWarmup(index) },
            onRemoveSetAt = { row -> actions.onRemoveSetAt(index, row) },
            onStartTimed = { row -> actions.onStartTimed(index, row) },
            onBarWeight = { actions.onBarWeight(index) },
            onNote = { actions.onNote(index) },
            onRemoveExercise = { actions.onRemoveExercise(index) },
            onDetails = { actions.onDetails(index) },
            onProgression = { actions.onProgression(index) },
            onSwap = { actions.onSwap(index) },
            onPairPrev = if (unit.size == 1 && index > 0) ({ actions.onPair(index - 1, index) }) else null,
            onPairNext = if (unit.size == 1 && index < entries.size - 1) ({ actions.onPair(index, index + 1) }) else null,
            onMoveUp = if (unit.size == 1) ({ actions.onMove(index, -1) }) else null,
            onMoveDown = if (unit.size == 1) ({ actions.onMove(index, 1) }) else null,
            canMoveUp = canMoveActiveWorkoutUnit(profileNow()?.active, index, -1),
            canMoveDown = canMoveActiveWorkoutUnit(profileNow()?.active, index, 1),
            step = if (unit.size > 1) position + 1 else null,
            compact = unit.size > 1,
            dense = dense,
            busy = busy,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/* ------------------------------------------------------------------ writing -- */

private fun setField(index: Int, row: Int, field: String, value: Double?) {
    editProfile { raw ->
        raw.editObject("active") { a ->
            a.editArray("entries") { es ->
                es.editAt(index) { entry ->
                    val sets = entry.arr("sets").editAt(row) { set ->
                        if (value == null) set.without(field) else set.with(field, value)
                    }
                    // Changing a weight cascades to the following sets of the same phase, so a heavier
                    // bar carries through the set instead of retyping every row.
                    entry.with("sets", if (field == "w") cascadeWeight(sets, row, value) else sets)
                }
            }
        }
    }
}

private fun modeAt(index: Int): String {
    val entry = profileNow()?.entries?.getOrNull(index) ?: return "reps"
    return modeOf((entry.obj("target") ?: JsonObject(emptyMap())).with("id", entry.str("id")))
}

private fun mutEntry(index: Int, block: (JsonObject) -> JsonObject) {
    editProfile { raw ->
        raw.editObject("active") { a -> a.editArray("entries") { es -> es.editAt(index, block) } }
    }
}

private fun addSet(index: Int) = mutEntry(index) { entry ->
    val sets = entry.arr("sets")
    val last = sets.objectAt(sets.size - 1)
    val target = entry.obj("target")
    val row = if (modeAt(index) == "time") {
        js(
            "sec" to (last?.num("sec") ?: target?.num("sec") ?: 45.0),
            "w" to (last?.num("w") ?: target?.num("weight") ?: 0.0),
            "done" to false,
        )
    } else {
        js(
            "w" to (last?.num("w") ?: 0.0),
            "r" to (last?.num("r") ?: target?.num("reps") ?: 0.0),
            "done" to false,
        )
    }
    entry.with("sets", sets + row)
}

private fun addWarmup(index: Int) = mutEntry(index) { entry ->
    val mode = modeOf((entry.obj("target") ?: JsonObject(emptyMap())).with("id", entry.str("id")))
    val step = defaultIncrement(entry.str("id"))
    entry.with("sets", insertWarmupRow(entry.arr("sets"), mode, entry.obj("target"), step))
}

private fun removeSetAt(index: Int, row: Int) = mutEntry(index) { entry ->
    entry.with("sets", removeRowAt(entry.arr("sets"), row))
}

private fun pairAt(first: Int, second: Int) = editProfile { raw ->
    raw.editObject("active") { a ->
        val entriesArr = a.arr("entries")
        if (first < 0 || second >= entriesArr.size) a else a.with("entries", pairAdjacent(entriesArr, first, second))
    }
}

private fun unpairAt(index: Int) = editProfile { raw ->
    raw.editObject("active") { a -> a.with("entries", unpairSuperset(a.arr("entries"), index)) }
}

private fun moveUnit(at: Int, direction: Int) {
    val active = profileNow()?.active ?: return
    val moved = moveActiveWorkoutUnit(active, at, direction) ?: return
    editProfile { it.with("active", moved.active) }
}

private fun navigateUnit(direction: Int) {
    val active = profileNow()?.active ?: return
    val fresh = supersetUnits(active.arr("entries"))
        .mapNotNull { element -> element.asArr()?.mapNotNull { it.asNum()?.toInt() } }
    val at = fresh.indexOfFirst { it.contains(active.int("cur") ?: 0) }
    val target = fresh.getOrNull(at + direction)?.firstOrNull() ?: return
    editProfile { raw -> raw.editObject("active") { a -> a.with("cur", target) } }
}

private fun focusUnit(first: Int) = editProfile { raw ->
    raw.editObject("active") { a -> a.with("cur", first) }
}

private fun setWorkoutView(view: String) = editProfile { raw ->
    raw.editObject("active") { a -> a.with("workoutView", view) }
}

/**
 * Ticking one set: the port of toggle in Workout.jsx. Where the marker goes, what rest the set
 * earned and whether the session is finished are all decisions the ported helpers make; this only
 * wires them to the writes.
 */
private fun toggle(index: Int, row: Int, highWater: MutableList<Int>) {
    val before = profileNow() ?: return
    val setsBefore = before.active?.arr("entries")?.objectAt(index)?.arr("sets") ?: return
    if (setsBefore.getOrNull(row) == null) return
    val mode = modeAt(index)
    val done = setsBefore.objectAt(row)?.bool("done") != true

    mutEntry(index) { entry ->
        entry.with("sets", entry.arr("sets").editAt(row) { it.with("done", done) })
    }
    if (done) ui.setTick()

    val fresh = profileNow() ?: return
    val freshEntries = fresh.active?.arr("entries") ?: return
    val freshUnits = supersetUnits(freshEntries)
    val ownUnitJson = unitOf(freshUnits, index)
    val ownUnit = ownUnitJson.mapNotNull { it.asNum()?.toInt() }
    val unitDone = ownUnit.all { k ->
        freshEntries.objectAt(k)?.arr("sets")?.all { it.asObj()?.bool("done") == true } == true
    }
    val nextUnit = if (unitDone) {
        nextUnfinishedUnit(freshEntries, freshUnits, index)?.asArr()?.mapNotNull { it.asNum()?.toInt() }
    } else {
        null
    }
    val workoutDone = unitDone && nextUnit == null
    val exJustDone = freshEntries.objectAt(index)?.arr("sets")?.all { it.asObj()?.bool("done") == true } == true

    // The next unit's own warm-up decides whether a rest is owed at all: a ramp set is where you were
    // going anyway, and stopping to time a break before it is the wrong advice.
    val restBeforeWarmup = nextUnit?.any { k ->
        freshEntries.objectAt(k)?.arr("sets")?.any { isWarmupRow(it) && it.asObj()?.bool("done") != true } == true
    } == true

    if (done && unitDone) {
        // topW is captured now; exWeights only at the finish, so a typo corrected before finishing
        // never becomes the remembered best.
        ownUnit.forEach { k ->
            val entry = freshEntries.objectAt(k) ?: return@forEach
            if (entry.arr("sets").all { it.asObj()?.bool("done") == true }) {
                val top = bestWeightForEntry(entry).takeIf { it != 0.0 }
                editProfile { raw ->
                    raw.editObject("active") { a -> a.editArray("entries") { es -> es.editAt(k) { it.with("topW", top) } } }
                }
            }
        }
    }

    if (workoutDone) {
        workoutCompleteSheet { finishWorkout() }
    } else if (done && exJustDone && mode == "time") {
        ui.toast(t("Hold logged"))
    }

    if (!done) {
        // A re-check of finished work must not navigate or reopen a sheet, but it may still owe you a
        // rest — the other half of the uncheck/re-check rule the helper owns.
        if (restOnRecheck(ui.state.value.rest != null, unitDone, workoutDone)) {
            startRestFor(fresh, index, row, ownUnitJson)
        }
        return
    }

    val progress = setProgressHighWater(freshEntries.getOrNull(index), highWater.getOrElse(index) { 0 })
    highWater[index] = progress.int("highWater") ?: 0
    if (progress.bool("isNew") != true) {
        if (restOnRecheck(ui.state.value.rest != null, unitDone, workoutDone)) {
            startRestFor(fresh, index, row, ownUnitJson)
        }
        return
    }

    if (unitDone) ui.stopRest()
    if (ownUnit.size <= 1) {
        if (!restBeforeWarmup && restAfterSet(unitDone, workoutDone)) startRestFor(fresh, index, row, ownUnitJson)
        return
    }

    val step = supersetFlowStep(freshEntries, ownUnitJson, index) ?: return
    if (step.bool("unitDone") == true) {
        if (!restBeforeWarmup) startRestFor(fresh, index, row, ownUnitJson)
    } else {
        step.int("nextIdx")?.let { next ->
            editProfile { raw -> raw.editObject("active") { a -> a.with("cur", next) } }
        }
        if (step.bool("roundDone") == true) startRestFor(fresh, index, row, ownUnitJson)
    }
}

/** The rest a set just earned, from the exercise's own rest when it set one and the profile's when not. */
private fun startRestFor(profile: Profile, index: Int, row: Int, unit: kotlinx.serialization.json.JsonElement) {
    if (profile.active?.obj("backfill") != null) return
    val entries = profile.active?.arr("entries") ?: return
    val restSec = restSecFor(entries, unit, profile.settings.restSec)
    val after = warmupRestSecFor(entries.getOrNull(index), row, restSec.toInt())
    val sets = entries.objectAt(index)?.arr("sets")
    val label = if (sets == null) {
        ""
    } else {
        t("Set {0} of {1}", sets.count { it.asObj()?.bool("done") == true }, sets.size)
    }
    ui.startRest(after, index, label)
}

/** A timed set is held, not typed: the hold's own countdown records what was actually held. */
private fun startTimed(index: Int, row: Int) {
    val profile = profileNow() ?: return
    val entry = profile.entries.getOrNull(index) ?: return
    val sec = entry.arr("sets").objectAt(row)?.num("sec") ?: 45.0
    ui.startWork(sec, Catalogue.nameOf(entry.str("id").orEmpty())) { elapsed ->
        mutEntry(index) { e -> e.with("sets", e.arr("sets").editAt(row) { it.with("sec", elapsed) }) }
        if (profileNow()?.entries?.getOrNull(index)?.arr("sets")?.objectAt(row)?.bool("done") != true) {
            mutEntry(index) { e -> e.with("sets", e.arr("sets").editAt(row) { it.with("done", true) }) }
        }
    }
}

/** The menu behind the header's ⋯: rename, and the three layouts. */
private val openViewMenu: () -> Unit = {
    val active = profileNow()?.active
    val view = active?.str("workoutView") ?: "cards"
    menuSheet(
        items = listOf(
            MenuItem(label = t("Rename workout"), icon = Glyph.PENCIL, onClick = { renameWorkoutSheet() }),
            MenuItem(
                label = t("Layout"),
                icon = Glyph.LIST,
                sub = when (view) {
                    "list" -> t("List")
                    "compact" -> t("Compact")
                    else -> t("Cards")
                },
                onClick = {
                    menuSheet(
                        title = t("Layout"),
                        items = listOf(
                            MenuItem(label = t("Cards"), icon = Glyph.CLIPBOARD, on = view == "cards", onClick = { setWorkoutView("cards") }),
                            MenuItem(label = t("List"), icon = Glyph.LIST, on = view == "list", onClick = { setWorkoutView("list") }),
                            MenuItem(label = t("Compact"), icon = Glyph.MINIMIZE, on = view == "compact", onClick = { setWorkoutView("compact") }),
                        ),
                    )
                },
            ),
        ),
    )
}

private fun removeExercise(index: Int) {
    val entry = profileNow()?.entries?.getOrNull(index)
    confirmSheet(
        title = t("Remove {0}?", Catalogue.nameOf(entry?.str("id").orEmpty())),
        message = if (entry?.arr("sets")?.any { it.asObj()?.bool("done") == true } == true) {
            t("The sets you logged for this exercise in this session will be lost.")
        } else {
            t("This removes the exercise from your current session.")
        },
        confirmText = t("Remove"),
        danger = true,
        onConfirm = { removeActiveExercise(index) },
    )
}

private fun removeExerciseSheet(index: Int) {
    val profile = profileNow() ?: return
    val entries = profile.active?.arr("entries")
    val group = unitOf(supersetUnits(entries), index)
    if (group.size > 1) {
        chooseSheet(
            title = t("Remove exercise"),
            message = t("Which exercise in this complex do you want to remove?"),
            options = group.map { element -> Catalogue.nameOf(entries?.objectAt(element.asNum()?.toInt() ?: -1)?.str("id").orEmpty()) },
            onPick = { which -> removeExercise(group[which].asNum()?.toInt() ?: index) },
        )
    } else {
        removeExercise(index)
    }
}

/** mm:ss since the session started, ticking once a second in its own state. */
@Composable
private fun elapsedLabel(start: Double): String {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = maxOf(0L, (now - start.toLong()) / 1000)
    return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
}

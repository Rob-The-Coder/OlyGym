package olygym.app.ui.workout

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import olygym.app.OlyGymApp
import olygym.app.platform.AutoBackup
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.bool
import olygym.app.data.editArray
import olygym.app.data.editObject
import olygym.app.data.asObj
import olygym.app.data.editAt
import olygym.app.data.insertAt
import olygym.app.data.int
import olygym.app.data.objectAt
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.toJsonObject
import olygym.app.data.obj
import olygym.app.data.removeObjectAt
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.data.Day
import olygym.app.lib.SwapEvent
import olygym.app.lib.applyPrescription
import olygym.app.lib.backfillEnd
import olygym.app.lib.backfillStart
import olygym.app.lib.bestWeightFor
import olygym.app.lib.bestWeightForEntry
import olygym.app.lib.buildSets
import olygym.app.lib.capWords
import olygym.app.lib.freestyleConfig
import olygym.app.lib.insertionIndexAfterCurrentUnit
import olygym.app.lib.isWarmupRow
import olygym.app.lib.modeOf
import olygym.app.lib.nextPrescription
import olygym.app.lib.swapActiveExercise
import olygym.app.lib.weightIncrement
import olygym.app.lib.defaultConfig
import olygym.app.lib.defaultIncrement
import olygym.app.lib.effectiveDay
import olygym.app.lib.supersetUnits
import olygym.app.lib.buildCompletedWorkout
import olygym.app.lib.buildDayEntries
import olygym.app.lib.cleanupSg
import olygym.app.lib.completeBackfill
import olygym.app.lib.isWarmupRow
import olygym.app.lib.setUnitsTotal
import olygym.app.lib.setsDoneActive
import olygym.app.lib.todayISO
import olygym.app.lib.uid
import olygym.app.lib.weekFor
import olygym.app.lib.workoutVolume
import olygym.app.ui.Nav
import olygym.app.ui.editProfile
import olygym.app.ui.profileNow
import olygym.app.ui.sheet.MenuItem
import olygym.app.ui.sheet.confirmSheet
import olygym.app.ui.sheet.exConfigSheet
import olygym.app.ui.sheet.exercisePicker
import olygym.app.ui.sheet.finishSummarySheet
import olygym.app.ui.sheet.menuSheet
import olygym.app.ui.sheet.weighInSheet
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * The session's lifecycle, the way the web keeps it in sheets.jsx: plain functions that read the
 * profile, write it once, and navigate. The screen owns the layout and the per-set wiring.
 */

/**
 * Start a day, through the weigh-in unless the profile turned it off. A null day is an explicit
 * freestyle session.
 */
fun startFlow(day: Day?) {
    val profile = profileNow() ?: return
    if (!profile.settings.weighIn) {
        beginWorkout(day, null)
        return
    }
    weighInSheet(required = true) { bw -> beginWorkout(day, bw) }
}

/**
 * Create the running session from a day. The day's exercises are copied into entries; nothing here
 * needs a per-entry routine id, and the layout is snapshotted so the header's menu can change it for
 * this session without touching the saved default.
 */
fun beginWorkout(day: Day?, bw: Double?) {
    val profile = profileNow() ?: return
    val raw = profile.raw
    val built = buildDayEntries(raw, day?.toJsonObject())
    val weekId = if (day != null) weekFor(profile.weeks, todayISO())?.id else null
    val active = js(
        "id" to uid(),
        "d" to todayISO(),
        "start" to System.currentTimeMillis(),
        // Written as nulls, not dropped: the web writes these two keys on a freestyle session too.
        "weekId" to (weekId ?: JsonNull),
        "dow" to (day?.dow ?: JsonNull),
        "name" to built.name,
        "bw" to (bw ?: JsonNull),
        "cur" to 0,
        "entries" to built.entries,
        "workoutView" to (raw.str("workoutView") ?: "cards"),
    )
    editProfile { it.with("active", active) }
    ui.stopRest()
    Nav.to(WorkoutScreen)
}

/**
 * Create a session for a date that has already happened — a port of beginBackfill. The same builder
 * walks up to the same entries a live start would, so a backfilled session is the day that date was
 * planned to have; a date with no day planned opens empty. The two fields that differ from a live
 * session are the date and start it is filed under, and the `backfill` block the finish path reads
 * to keep it out of the records and to know which workout it replaces.
 *
 * Not ported: the JS writes `replaceId` as an explicit null. js() drops nulls, so the key is absent
 * instead, and completeBackfill reads both the same way.
 */
fun beginBackfill(iso: String, time: String, durationMin: Int, replaceId: String?) {
    val profile = profileNow() ?: return
    val raw = profile.raw
    val day = effectiveDay(raw, iso)
    val built = buildDayEntries(raw, day?.toJsonObject())
    val weekId = if (day != null) weekFor(profile.weeks, iso)?.id else null
    val active = js(
        "id" to uid(),
        "d" to iso,
        "start" to backfillStart(iso, time),
        "weekId" to (weekId ?: JsonNull),
        "dow" to (day?.dow ?: JsonNull),
        "name" to built.name,
        "bw" to JsonNull,
        "cur" to 0,
        "entries" to built.entries,
        "backfill" to js("durationMin" to durationMin, "replaceId" to replaceId),
        "workoutView" to (raw.str("workoutView") ?: "cards"),
    )
    editProfile { it.with("active", active) }
    ui.stopRest()
    Nav.to(WorkoutScreen)
}

/**
 * Finish: confirm what the counts say, then file it. Nothing logged and a partly logged session get
 * different questions, because they mean different things.
 */
fun finishWorkout() {
    val active = profileNow()?.active ?: return
    val done = setsDoneActive(active)
    val total = setUnitsTotal(active.arr("entries"))
    when {
        done == 0 -> confirmSheet(
            title = t("Nothing logged yet"),
            message = t("You haven’t checked off any sets. Finish the workout anyway?"),
            confirmText = t("Finish anyway"),
            onConfirm = { doFinishWorkout() },
        )

        done < total -> {
            val left = total - done
            confirmSheet(
                title = t("Finish early?"),
                message = if (left == 1) {
                    t("{0} set still unchecked. Finish the workout now?", left)
                } else {
                    t("{0} sets still unchecked. Finish the workout now?", left)
                },
                confirmText = t("Finish workout"),
                onConfirm = { doFinishWorkout() },
            )
        }

        else -> doFinishWorkout()
    }
}

/**
 * File the session into the training log. A backfilled session is filed on the day it belongs to and
 * claims no records: a workout logged into the past cannot beat the history that came after it.
 */
fun doFinishWorkout() {
    val profile = profileNow() ?: return
    val active = profile.active ?: return
    val past = active.obj("backfill") != null

    val prs = mutableListOf<String>()
    if (!past) {
        profile.entries.forEach { entry ->
            val id = entry.str("id") ?: return@forEach
            val heaviest = entry.arr("sets")
                .filter { it.asObj()?.bool("done") == true && !isWarmupRow(it) }
                .mapNotNull { it.asObj()?.num("w") }
                .filter { it > 0 }
                .maxOrNull() ?: 0.0
            if (heaviest > 0 && heaviest > bestWeightFor(profile.raw, id)) prs += id
        }
    }

    val end = if (past) backfillEnd(active) else System.currentTimeMillis()
    val built = buildCompletedWorkout(
        active = active,
        end = end,
        prs = JsonArray(prs.map { kotlinx.serialization.json.JsonPrimitive(it) }),
    )
    val workout = built.with("vol", workoutVolume(built))

    editProfile { raw ->
        var next = raw
        if (past) {
            next = next.with("workouts", completeBackfill(next.arr("workouts"), active, workout))
        } else {
            var updated = next.obj("exWeights") ?: JsonObject(emptyMap())
            workout.arr("entries").forEach { element ->
                val entry = element.asObj() ?: return@forEach
                val id = entry.str("id") ?: return@forEach
                val best = bestWeightForEntry(entry)
                // The remembered best only moves up, and only from what this session actually logged.
                if (best > 0) updated = updated.with(id, js("w" to best, "d" to workout.str("d")))
            }
            next = next.with("workouts", JsonArray(next.arr("workouts") + workout)).with("exWeights", updated)
        }
        next.with("active", null)
    }
    // The web's autoBackupNow: after the one moment where losing the local data would hurt.
    profileNow()?.raw?.let { AutoBackup.write(OlyGymApp.appContext, it) }
    ui.stopRest()
    finishSummarySheet(workout, prs)
}

/**
 * Remove a whole exercise from the session. The work timer is stopped before any index can shift —
 * which also protects a confirmation sheet opened first and confirmed after a hold started — and the
 * rest survives unless it belonged to the exercise that just went.
 */
fun removeActiveExercise(idx: Int) {
    ui.stopWork()
    val rest = ui.state.value.rest
    if (rest != null && rest.forIdx == idx) ui.stopRest() else ui.shiftRestOwner(idx + 1, -1)
    editProfile { raw ->
        raw.editObject("active") { active ->
            val entries = active.arr("entries")
            if (idx < 0 || idx >= entries.size) return@editObject active
            val cleaned = cleanupSg(entries.removeObjectAt(idx))
            var cur = active.int("cur") ?: 0
            if (idx < cur) cur--
            if (cur >= cleaned.size) cur = maxOf(0, cleaned.size - 1)
            active.with("entries", cleaned).with("cur", cur)
        }
    }
}

/* ------------------------------------------------------------- adding one -- */

/**
 * Add an exercise to the running session: the picker, then either the config sheet (tapping a row)
 * or the default config (the row's plus). A planned session takes the day's prescription, a
 * freestyle one seeds from the last time the lift was done.
 */
fun addExerciseFlow() {
    val profile = profileNow() ?: return
    val active = profile.active ?: return
    val day = if (active.str("weekId") != null) effectiveDay(profile.raw, active.str("d").orEmpty()) else null
    val dayJson = day?.toJsonObject()
    exercisePicker { ex, quick ->
        val freestyle = dayJson == null
        val seed = if (freestyle) freestyleConfig(profile.raw, defaultConfig(ex.id).with("id", ex.id)) else null
        val commit = commit@ { cfg: JsonObject ->
            val before = profileNow() ?: return@commit
            val current = before.active ?: return@commit
            val insertAt = insertionIndexAfterCurrentUnit(
                supersetUnits(current.arr("entries")),
                current.int("cur") ?: 0,
                current.arr("entries").size,
            )
            val full = cfg.with("id", ex.id)
            val step = if (modeOf(full) == "reps") weightIncrement(full) else defaultIncrement(ex.id)
            editProfile { raw ->
                val a = raw.obj("active") ?: return@editProfile raw
                val plan = if (freestyle) null else nextPrescription(raw, full, dayJson)
                val sets = buildSets(
                    raw,
                    full,
                    js(
                        "step" to step,
                        "preferLast" to if (freestyle) true else null,
                        "useTarget" to if (plan?.str("kind") == "off") true else null,
                    ),
                )
                val built = if (freestyle) sets else applyPrescription(sets, plan, step)
                val entries = a.arr("entries").insertAt(
                    insertAt,
                    js("id" to ex.id, "target" to cfg, "plan" to plan, "sets" to built),
                )
                raw.with("active", a.with("entries", entries).with("cur", insertAt))
            }
            ui.shiftRestOwner(insertAt, 1)
        }
        if (quick) {
            commit(seed ?: defaultConfig(ex.id))
            ui.toast(t("“{0}” added to {1}", capWords(Catalogue.nameOf(ex.id)), day?.name ?: t("Freestyle")))
        } else {
            exConfigSheet(ex, initial = seed, routine = dayJson, onSave = { cfg -> commit(cfg) })
        }
    }
}

/**
 * The progression settings of one entry of the running session: the same config sheet, and the rows
 * are rebuilt from the new config the way the session was, keeping only what was already logged.
 */
fun openProgressionSettings(index: Int) {
    val profile = profileNow() ?: return
    val active = profile.active ?: return
    val entry = active.arr("entries").objectAt(index) ?: return
    val activeId = active.str("id")
    val entryId = entry.str("id").orEmpty()
    val entryCount = active.arr("entries").size
    val day = if (active.str("weekId") != null) effectiveDay(profile.raw, active.str("d").orEmpty()) else null
    val dayJson = day?.toJsonObject()
    exConfigSheet(
        ex = Catalogue[entryId],
        existing = entry.obj("target"),
        routine = dayJson,
        onSave = { cfg ->
            // The sheet may outlive its workout or its entry. Never apply its result to whatever
            // later happens to occupy the same index.
            val current = profileNow() ?: return@exConfigSheet
            val nowActive = current.active
            val sameEntry = nowActive?.str("id") == activeId &&
                nowActive.arr("entries").size == entryCount &&
                nowActive.arr("entries").objectAt(index)?.str("id") == entryId
            if (!sameEntry) return@exConfigSheet
            editProfile { raw ->
                val a = raw.obj("active") ?: return@editProfile raw
                val target = a.arr("entries").objectAt(index) ?: return@editProfile raw
                if (target.str("id") != entryId) return@editProfile raw
                val full = cfg.with("id", entryId)
                val step = if (modeOf(full) == "reps") weightIncrement(full) else defaultIncrement(entryId)
                // A config without a set count keeps the rows the session already has.
                val config = if ((full.num("sets") ?: 0.0) > 0) {
                    full
                } else {
                    full.with("sets", target.arr("sets").count { !isWarmupRow(it) }.coerceAtLeast(1))
                }
                val plan = nextPrescription(raw, config, dayJson)
                val fresh = applyPrescription(
                    buildSets(raw, config, js("step" to step, "useTarget" to (plan.str("kind") == "off"))),
                    plan,
                    step,
                )
                val doneWarm = target.arr("sets").filter { isWarmupRow(it) && it.asObj()?.bool("done") == true }
                val doneWork = target.arr("sets").filter { !isWarmupRow(it) && it.asObj()?.bool("done") == true }
                val freshWarm = fresh.filter { isWarmupRow(it) }
                val freshWork = fresh.filter { !isWarmupRow(it) }
                val rows = JsonArray(doneWarm + freshWarm.drop(doneWarm.size) + doneWork + freshWork.drop(doneWork.size))
                raw.with(
                    "active",
                    a.with(
                        "entries",
                        a.arr("entries").editAt(index) {
                            it.with("target", cfg).with("plan", plan).with("sets", rows)
                        },
                    ),
                )
            }
        },
    )
}

/**
 * Start a safe swap for one exact active-workout occurrence: pick the replacement, configure it, and
 * let swapActiveExercise decide whether the logged sets need asking about.
 */
fun swapActiveWorkoutExercise(index: Int) {
    val profile = profileNow() ?: return
    val active = profile.active ?: return
    if (active.arr("entries").objectAt(index) == null) return
    val dayJson = if (active.str("weekId") != null) {
        effectiveDay(profile.raw, active.str("d").orEmpty())?.toJsonObject()
    } else {
        null
    }
    var sheetId = 0L

    fun apply(replacement: JsonObject, loggedConfirmed: Boolean, disposition: String?) {
        ui.stopWork()
        ui.stopRest()
        editProfile { raw ->
            val event = swapActiveExercise(raw.obj("active"), index, replacement, loggedConfirmed, disposition)
            when (event) {
                is SwapEvent.Replaced -> raw.with("active", event.active)
                is SwapEvent.Inserted -> raw.with("active", event.active)
                else -> raw
            }
        }
    }

    fun swapTo(ex: Exercise, cfg: JsonObject) {
        // The picker is a chooser here, not a stack you keep adding from: one swap, then back to the
        // workout.
        ui.closeSheet(sheetId)
        val state = profileNow() ?: return
        val target = state.active?.arr("entries")?.objectAt(index) ?: return
        val freestyle = dayJson == null
        val full = cfg.with("id", ex.id)
        val step = if (modeOf(full) == "reps") weightIncrement(full) else defaultIncrement(ex.id)
        val plan = if (freestyle) null else nextPrescription(state.raw, full, dayJson)
        val built = buildSets(
            state.raw,
            full,
            js(
                "step" to step,
                "preferLast" to if (freestyle) true else null,
                "useTarget" to if (plan?.str("kind") == "off") true else null,
            ),
        )
        val replacement = js(
            "id" to ex.id,
            "target" to cfg,
            "plan" to plan,
            "sets" to if (freestyle) built else applyPrescription(built, plan, step),
        )
        val logged = target.arr("sets").any { it.asObj()?.bool("done") == true }
        if (!logged) {
            apply(replacement, false, null)
            return
        }
        if (target.obj("sg") != null) {
            menuSheet(
                title = t("Swap exercise?"),
                subtitle = t("Logged sets stay with the original exercise. Choose where the replacement belongs."),
                items = listOf(
                    MenuItem(label = t("Keep replacement in this group"), onClick = { apply(replacement, true, "keep") }),
                    MenuItem(label = t("Insert after this group"), onClick = { apply(replacement, true, "detach") }),
                ),
            )
            return
        }
        confirmSheet(
            title = t("Swap exercise?"),
            message = t("Logged sets stay with the original exercise. The replacement will be inserted afterward."),
            confirmText = t("Continue"),
            onConfirm = { apply(replacement, true, null) },
        )
    }

    sheetId = exercisePicker { ex, quick ->
        if (quick) {
            swapTo(ex, defaultConfig(ex.id))
        } else {
            exConfigSheet(ex, routine = dayJson, onSave = { cfg -> swapTo(ex, cfg) })
        }
    }
}


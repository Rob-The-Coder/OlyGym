package olygym.app.ui.workout

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.bool
import olygym.app.data.editArray
import olygym.app.data.editObject
import olygym.app.data.asObj
import olygym.app.data.int
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.toJsonObject
import olygym.app.data.obj
import olygym.app.data.removeObjectAt
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.data.Day
import olygym.app.lib.backfillEnd
import olygym.app.lib.bestWeightFor
import olygym.app.lib.bestWeightForEntry
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
import olygym.app.ui.sheet.confirmSheet
import olygym.app.ui.sheet.finishSummarySheet
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

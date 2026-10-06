package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.js

/*
 * The starter-plan catalog — a port of frontend/src/lib/starter.js. Training data only: the
 * chooser's plan names and descriptions are string literals inside t() calls in the sheet, because
 * the web's check-source-strings script only finds them there, and copy parked in here would
 * silently ship English in every language.
 *
 * A routine is (key, name, [(exerciseId, sets, reps)]). The key is what a plan's schedule points
 * at, so a weekday never depends on the position of a routine in the list. Names stay canonical
 * English — they become ordinary user days, which are not translated. starter.js carries an emoji
 * per routine too; the built day does not, and nothing reads it, so it is not carried here.
 *
 * Exercise ids are from the OlyGym catalogue (see exercises-data.js): "wl" + the Catalyst
 * Athletics exercise-page id.
 */

private data class StarterRoutine(val key: String, val name: String, val ex: List<Triple<String, Int, Int>>)

private data class StarterPlan(val routines: List<StarterRoutine>, val schedule: List<Pair<Int, String>>)

private fun r(key: String, name: String, vararg ex: Triple<String, Int, Int>) = StarterRoutine(key, name, ex.toList())

private val PPL = listOf(
    r("snatch", "Snatch Day", Triple("wl58", 5, 3), Triple("wl97", 3, 3), Triple("wl79", 3, 5), Triple("wl78", 3, 5)),
    r("cj", "Clean & Jerk Day", Triple("wl59", 5, 3), Triple("wl405", 4, 3), Triple("wl98", 3, 3), Triple("wl77", 3, 5)),
    r("squat-pull", "Squat & Pull Day", Triple("wl77", 5, 5), Triple("wl604", 3, 5), Triple("wl183", 3, 8), Triple("wl171", 3, 10)),
)

private val UPPER_LOWER = listOf(
    r("tech-a", "Technique A", Triple("wl61", 5, 3), Triple("wl97", 3, 3), Triple("wl79", 3, 5)),
    r("strength-a", "Strength A", Triple("wl77", 4, 5), Triple("wl101", 3, 8), Triple("wl39", 3, 8)),
    r("tech-b", "Technique B", Triple("wl67", 5, 3), Triple("wl405", 4, 3), Triple("wl98", 3, 3)),
    r("strength-b", "Strength B", Triple("wl78", 4, 5), Triple("wl604", 3, 5), Triple("wl806", 3, 8)),
)

private val FULL_BODY = listOf(
    r("fb-a", "Full Body A", Triple("wl58", 4, 3), Triple("wl77", 3, 5), Triple("wl171", 3, 10)),
    r("fb-b", "Full Body B", Triple("wl76", 4, 2), Triple("wl78", 3, 5), Triple("wl39", 3, 8)),
    r("fb-c", "Full Body C", Triple("wl78", 3, 5), Triple("wl604", 3, 5), Triple("wl806", 3, 8)),
)

private val FIVE_BY_FIVE = listOf(
    r("5x5-a", "5x5 A", Triple("wl77", 5, 5), Triple("wl806", 5, 5), Triple("wl171", 5, 5)),
    r("5x5-b", "5x5 B", Triple("wl78", 5, 5), Triple("wl87", 5, 5), Triple("wl604", 5, 5)),
    r("5x5-c", "5x5 C", Triple("wl77", 5, 5), Triple("wl183", 5, 5), Triple("wl39", 5, 5)),
)

/** [weekday, routineKey] — weekday is a DAYN index, so 1 is Monday. Every plan is one concrete
 *  calendar week, placed on its own weekdays. */
private val PLANS: Map<String, StarterPlan> = linkedMapOf(
    "ppl" to StarterPlan(PPL, listOf(1 to "snatch", 3 to "cj", 5 to "squat-pull")),
    "upper-lower" to StarterPlan(
        UPPER_LOWER,
        listOf(1 to "tech-a", 2 to "strength-a", 4 to "tech-b", 5 to "strength-b"),
    ),
    "full-body" to StarterPlan(FULL_BODY, listOf(1 to "fb-a", 3 to "fb-b", 5 to "fb-c")),
    "5x5" to StarterPlan(FIVE_BY_FIVE, listOf(1 to "5x5-a", 3 to "5x5-b", 5 to "5x5-c")),
)

/** [(id, day count)] for the chooser. The day count is read off the schedule, so the two can never
 *  disagree. */
fun starterPlanOptions(): List<Pair<String, Int>> = PLANS.map { (id, plan) -> id to plan.schedule.size }

/** The weekdays a plan would claim, or null for an unknown id. */
fun starterPlanDays(id: String?): List<Int>? = PLANS[id]?.schedule?.map { it.first }

/**
 * One dated week in the shape the file holds, or null for an unknown id — a caller that treats null
 * as "change nothing" can never half-apply a plan. Plan names are translated in the sheet, so the
 * name arrives already translated; the week carries its own empty customEx because the plan-sharing
 * path reads it (see plan-share.js).
 */
fun buildStarterPlan(
    id: String?,
    name: String = "",
    weekStart: Int = MONDAY,
    todayIso: String = todayISO(),
): JsonObject? {
    val plan = PLANS[id] ?: return null
    val byKey = plan.routines.associateBy { it.key }
    val days = plan.schedule.mapNotNull { (dow, key) ->
        val routine = byKey[key] ?: return@mapNotNull null
        js(
            "dow" to dow,
            "name" to routine.name,
            "ex" to routine.ex.map { (exId, sets, reps) ->
                js("id" to exId, "sets" to sets, "reps" to reps, "weight" to 0)
            },
        )
    }
    return js(
        "id" to uid(),
        "startIso" to isoOf(startOfWeek(todayIso, weekStartOf(weekStart))),
        "name" to name,
        "days" to days,
        "customEx" to emptyList<JsonObject>(),
    )
}

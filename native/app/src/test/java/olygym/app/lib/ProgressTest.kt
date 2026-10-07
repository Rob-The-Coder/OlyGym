package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The exercise-progress reading, which Stats.jsx keeps inline. These are the four rules the card
 * depends on: the picker lists what has a history, a session that logged another mode is skipped,
 * an unloaded exercise's progress is its reps, and a session whose mode never changed is one point.
 */

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * The shipped catalogue, read the way MusclesTest reads it. These cases name real ids (wl58, wl77)
 * and the picker drops an exercise the catalogue does not know, so without this the class passes only
 * when another class happened to install the real index first -- which it no longer always does.
 */
private val progressCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    json.decodeFromString<List<Exercise>>(file.readText())
}
private fun pSet(w: Double, r: Int, done: Boolean = true) = js("w" to w, "r" to r, "done" to done)

private fun pEntry(id: String, sets: List<kotlinx.serialization.json.JsonObject>, target: kotlinx.serialization.json.JsonObject? = null) =
    js("id" to id, "sets" to sets, "target" to target)

private fun pWorkout(d: String, start: Double, entries: List<kotlinx.serialization.json.JsonObject>) =
    js("d" to d, "start" to start, "entries" to entries)

private fun pState(vararg workouts: kotlinx.serialization.json.JsonObject) =
    js("unit" to "kg", "workouts" to workouts.toList())

class ProgressTest {

    @Before
    fun installCatalogue() {
        Catalogue.install(progressCatalogue)
    }

    @Test
    fun `the picker lists each exercise once, biggest reading first`() {
        val S = pState(
            pWorkout("2026-10-05", 1.0, listOf(pEntry("wl58", listOf(pSet(60.0, 3))))),
            pWorkout("2026-10-07", 2.0, listOf(pEntry("wl77", listOf(pSet(120.0, 5))), pEntry("wl58", listOf(pSet(65.0, 3))))),
        )
        val readings = progressExercises(S)
        assertEquals(listOf("wl77", "wl58"), readings.map { it.id })
        assertEquals(120.0, readings[0].mx, 1e-9)
        assertEquals("kg", readings[0].unit)
    }

    @Test
    fun `a session that logged another mode is skipped, and the points carry their sets`() {
        val S = pState(
            pWorkout("2026-10-05", 1.0, listOf(pEntry("wl58", listOf(pSet(60.0, 3))))),
            pWorkout("2026-10-07", 2.0, listOf(pEntry("wl58", listOf(js("sec" to 30, "done" to true))))),
            pWorkout("2026-10-09", 3.0, listOf(pEntry("wl58", listOf(pSet(70.0, 1))))),
        )
        val series = progressSeries(S, "wl58")
        assertEquals("reps", series.mode)
        assertEquals(listOf(1L, 3L), series.points.map { it.t })
        assertEquals(listOf(60.0, 70.0), series.points.map { it.y })
        assertEquals(70.0, series.best, 1e-9)
        assertEquals(1, series.points[0].sets.size)
    }

    @Test
    fun `an exercise that was never loaded reads as its reps`() {
        val S = pState(
            pWorkout("2026-10-05", 1.0, listOf(pEntry("wl39", listOf(pSet(0.0, 8))))),
            pWorkout("2026-10-08", 2.0, listOf(pEntry("wl39", listOf(pSet(0.0, 12))))),
        )
        val series = progressSeries(S, "wl39")
        assertEquals(true, series.repsOnly)
        assertEquals("reps", series.unit)
        assertEquals(listOf(8.0, 12.0), series.points.map { it.y })
        assertEquals(12.0, series.best, 1e-9)
        assertEquals("reps", progressExercises(S).first { it.id == "wl39" }.unit)
    }

    @Test
    fun `a timed exercise reads in seconds`() {
        val S = pState(
            pWorkout("2026-10-05", 1.0, listOf(pEntry("wl600", listOf(js("sec" to 30, "mode" to "time", "done" to true))))),
            pWorkout("2026-10-08", 2.0, listOf(pEntry("wl600", listOf(js("sec" to 45, "mode" to "time", "done" to true))))),
        )
        val series = progressSeries(S, "wl600")
        assertEquals("time", series.mode)
        assertEquals("s", series.unit)
        assertEquals(listOf(30.0, 45.0), series.points.map { it.y })
    }

    @Test
    fun `the workouts on one date, for a heatmap cell`() {
        val S = pState(
            pWorkout("2026-10-05", 1.0, listOf(pEntry("wl58", listOf(pSet(60.0, 3))))),
            pWorkout("2026-10-05", 2.0, listOf(pEntry("wl77", listOf(pSet(120.0, 5))))),
            pWorkout("2026-10-06", 3.0, listOf(pEntry("wl58", listOf(pSet(65.0, 3))))),
        )
        assertEquals(2, workoutsOnDate(S["workouts"], "2026-10-05").size)
        assertEquals(1, workoutsOnDate(S["workouts"], "2026-10-06").size)
        assertEquals(0, workoutsOnDate(JsonArray(emptyList()), "2026-10-06").size)
    }
}
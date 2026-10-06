package olygym.app.lib

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The spec of frontend/src/lib/exercise-history.test.js, one case each, in the same order.
 */

private const val BH_DAY = 86400000.0
private val BH_T0 = LocalDateTime.of(2026, 1, 5, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

/** The JS iso(i): T0 plus i days, read back as a UTC date. */
private fun bhIso(i: Int): String =
    Instant.ofEpochMilli(BH_T0 + (i * BH_DAY).toLong()).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun bhWork(w: Double, r: Double, done: Boolean = true): JsonObject =
    js("w" to w, "r" to r, "done" to done)

private fun bhWarm(w: Double, r: Double): JsonObject = js("w" to w, "r" to r, "done" to true, "phase" to "warmup")

/** One workout on day i with a single bench entry made of the given rows. */
private fun bhSession(i: Int, rows: List<JsonObject>): JsonObject = js(
    "id" to "w" + i,
    "d" to bhIso(i),
    "start" to (BH_T0 + (i * BH_DAY).toLong()),
    "entries" to listOf(js("id" to "bench", "target" to js("mode" to "reps"), "sets" to rows)),
)

private fun bhHold(i: Int): JsonObject = js(
    "id" to "h" + i,
    "d" to bhIso(i),
    "start" to (BH_T0 + (i * BH_DAY).toLong()),
    "entries" to listOf(
        js(
            "id" to "plank",
            "target" to js("mode" to "time"),
            "sets" to listOf(
                js("sec" to (40 + i * 10).toDouble(), "done" to true),
                js("sec" to 30.0, "done" to true),
            ),
        ),
    ),
)

class ExerciseHistoryTest {

    @Test
    fun `is empty when the exercise was never logged`() {
        val S = js("workouts" to listOf(bhSession(0, listOf(bhWork(60.0, 5.0)))))
        val h = exerciseHistory(S, "squat")
        assertEquals(0, h.total)
        assertEquals(0.0, h.best, 1e-9)
        assertNull(h.prId)
        assertTrue(h.sessions.isEmpty())
        assertTrue(h.points.isEmpty())
        assertEquals(0, exerciseHistory(js("workouts" to emptyList<JsonObject>()), "bench").total)
        assertEquals(0, exerciseHistory(js(), "bench").total)
    }

    @Test
    fun `lists sessions newest first with their work sets, value and volume`() {
        val S = js(
            "workouts" to listOf(
                bhSession(0, listOf(bhWork(60.0, 5.0), bhWork(60.0, 5.0))),
                bhSession(2, listOf(bhWork(65.0, 5.0), bhWork(65.0, 4.0))),
            ),
        )
        val h = exerciseHistory(S, "bench")
        assertEquals("reps", h.mode)
        assertEquals("weight", h.metric)
        assertEquals(2, h.total)
        assertEquals(listOf(bhIso(2), bhIso(0)), h.sessions.map { it.d })
        assertEquals(65.0, h.sessions[0].value ?: -1.0, 1e-9)
        assertEquals(65.0 * 9, h.sessions[0].volume ?: -1.0, 1e-9)
        assertEquals("reps", h.sessions[0].target?.str("mode"))
        assertEquals(2, h.sessions[0].sets.size)
        assertEquals(60.0, h.sessions[1].value ?: -1.0, 1e-9)
        assertEquals(600.0, h.sessions[1].volume ?: -1.0, 1e-9)
        // the chart stays chronological
        assertEquals(listOf(listOf(bhIso(0), 60.0), listOf(bhIso(2), 65.0)), h.points.map { listOf(it.d, it.y) })
    }

    @Test
    fun `leaves warm-ups and unfinished rows out of every number`() {
        val S = js(
            "workouts" to listOf(
                bhSession(0, listOf(bhWarm(40.0, 8.0), bhWork(60.0, 5.0), bhWork(100.0, 5.0, false))),
            ),
        )
        val h = exerciseHistory(S, "bench")
        assertEquals(60.0, h.best, 1e-9)
        assertEquals(listOf(bhWork(60.0, 5.0)), h.sessions[0].sets.toList())
        assertEquals(300.0, h.sessions[0].volume ?: -1.0, 1e-9)
    }

    @Test
    fun `marks the PR on the session that first reached the best weight, once`() {
        val S = js(
            "workouts" to listOf(
                bhSession(0, listOf(bhWork(60.0, 5.0))),
                bhSession(1, listOf(bhWork(70.0, 5.0))),
                bhSession(2, listOf(bhWork(65.0, 5.0))),
                bhSession(3, listOf(bhWork(70.0, 3.0))),
            ),
        )
        val h = exerciseHistory(S, "bench")
        assertEquals(70.0, h.best, 1e-9)
        assertEquals("w1", h.prId)
        assertEquals(listOf("w1"), h.sessions.filter { it.pr }.map { it.id })
    }

    @Test
    fun `keeps the last ten sessions in the list but every session on the chart`() {
        val S = js("workouts" to (0 until 14).map { bhSession(it, listOf(bhWork((50 + it).toDouble(), 5.0))) })
        val h = exerciseHistory(S, "bench")
        assertEquals(14, h.total)
        assertEquals(HISTORY_SESSIONS, h.sessions.size)
        assertEquals(bhIso(13), h.sessions[0].d)
        assertEquals(bhIso(4), h.sessions.last().d)
        assertEquals(14, h.points.size)
        // the record lives outside the listed window, so no listed session carries the marker
        assertEquals("w13", h.prId)
        assertEquals(3, exerciseHistory(S, "bench", 3).sessions.size)
    }

    @Test
    fun `orders backfilled sessions by date, not by position in the log`() {
        val backfilled = js(
            "id" to "w1",
            "d" to bhIso(1),
            "entries" to listOf(js("id" to "bench", "target" to js("mode" to "reps"), "sets" to listOf(bhWork(80.0, 5.0)))),
        )
        val S = js("workouts" to listOf(bhSession(5, listOf(bhWork(60.0, 5.0))), backfilled))
        val h = exerciseHistory(S, "bench")
        assertEquals(listOf(bhIso(1), bhIso(5)), h.points.map { it.d })
        assertEquals(listOf(bhIso(5), bhIso(1)), h.sessions.map { it.d })
        assertEquals("w1", h.prId)
    }

    @Test
    fun `plots reps for an exercise that was never loaded`() {
        val S = js(
            "workouts" to listOf(
                bhSession(0, listOf(bhWork(0.0, 8.0))),
                bhSession(1, listOf(bhWork(0.0, 10.0), bhWork(0.0, 9.0))),
            ),
        )
        val h = exerciseHistory(S, "bench")
        assertEquals("reps", h.metric)
        assertEquals(listOf(8.0, 10.0), h.points.map { it.y })
        assertEquals(10.0, h.best, 1e-9)
        assertEquals("w1", h.prId)
    }

    @Test
    fun `plots the longest hold for timed work`() {
        val S = js("workouts" to listOf(bhHold(0), bhHold(1)))
        val plank = exerciseHistory(S, "plank")
        assertEquals("time", plank.mode)
        assertEquals("sec", plank.metric)
        assertEquals(50.0, plank.best, 1e-9)
        assertEquals("h1", plank.prId)
        assertEquals(listOf(40.0, 50.0), plank.points.map { it.y })
        assertNull(plank.sessions[0].volume)
    }

    @Test
    fun `gives a session logged in another mode no point, but keeps it in the list`() {
        val timed = js(
            "id" to "a",
            "d" to bhIso(0),
            "start" to BH_T0,
            "entries" to listOf(
                js("id" to "x", "target" to js("mode" to "time"), "sets" to listOf(js("sec" to 30.0, "done" to true))),
            ),
        )
        val reps = js(
            "id" to "b",
            "d" to bhIso(1),
            "start" to (BH_T0 + BH_DAY.toLong()),
            "entries" to listOf(js("id" to "x", "target" to js("mode" to "reps"), "sets" to listOf(bhWork(20.0, 10.0)))),
        )
        val h = exerciseHistory(js("workouts" to listOf(timed, reps)), "x")
        assertEquals("reps", h.mode)
        assertEquals(1, h.points.size)
        assertEquals(listOf(listOf("b", 20.0), listOf("a", null)), h.sessions.map { listOf(it.id, it.value) })
    }
}

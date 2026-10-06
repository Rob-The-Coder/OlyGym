package olygym.app.lib

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spec of frontend/src/lib/effort.test.js, one case each, in the same order. */
class EffortTest {

    private fun daysAgo(n: Int): Pair<String, Long> {
        val d = LocalDate.now().minusDays(n.toLong())
        return d.toString() to (System.currentTimeMillis() - n.toLong() * 86_400_000L)
    }

    // One workout on a day, with the sets given. Everything here is a finished set unless a set
    // says otherwise, because that is what the stats read.
    private fun W(n: Int, sets: List<JsonObject>): JsonObject {
        val (iso, start) = daysAgo(n)
        val rows = sets.map { s -> JsonObject(js("w" to 60, "r" to 8, "done" to true) + s) }
        return js(
            "id" to ("w" + n),
            "d" to iso,
            "start" to start,
            "entries" to listOf(js("id" to "0025", "sets" to rows)),
        )
    }

    private fun S(vararg workouts: JsonObject): JsonObject = js("workouts" to workouts.toList())

    private val summaryFixture: JsonObject = S(
        W(2, listOf(js("rir" to 1), js("rir" to 3), js("rir" to 2))),
        W(4, listOf(js("rir" to 0), js("rpe" to 6), js())),          // RPE 6 = RIR 4, one set unrated
        W(40, listOf(js("rir" to 5), js("rir" to 5))),
    )

    @Test
    fun `reads RIR straight and RPE as its mirror — RPE 8 is RIR 2`() {
        assertEquals(2.0, rirOf(js("rir" to 2))!!, 0.0)
        assertEquals(2.0, rirOf(js("rpe" to 8))!!, 0.0)
        assertEquals(0.5, rirOf(js("rpe" to 9.5))!!, 0.0)
    }

    @Test
    fun `keeps a rated 0 and rejects everything that only looks like one`() {
        assertEquals(0.0, rirOf(js("rir" to 0))!!, 0.0)        // taken to failure — a rating, not "empty"
        assertEquals(0.0, rirOf(js("rpe" to 10))!!, 0.0)
        assertNull(rirOf(js()))
        assertNull(rirOf(js("rir" to null)))
        assertNull(rirOf(null))
    }

    @Test
    fun `prefers the scale the set itself was logged on when a file wrote both`() {
        assertEquals(3.0, rirOf(js("rir" to 3, "rpe" to 9))!!, 0.0)
    }

    @Test
    fun `converts back to whichever scale is on screen`() {
        assertEquals(2.0, toScale("rir", 2.0)!!, 0.0)
        assertEquals(8.0, toScale("rpe", 2.0)!!, 0.0)
        assertEquals(10.0, toScale("rpe", 0.0)!!, 0.0)
        assertNull(toScale("rir", null))
    }

    @Test
    fun `does not let the round trip produce float dust`() {
        assertEquals(7.5, toScale("rpe", 10.0 - 7.5)!!, 0.0)
    }

    @Test
    fun `follows the profile setting when it has one`() {
        assertEquals("rpe", displayScale(js("effort" to "rpe", "workouts" to emptyList<Any>())))
        assertEquals("rir", displayScale(js("effort" to "rir", "workouts" to emptyList<Any>())))
    }

    @Test
    fun `shows an unrated profile the scale its imported history is written in`() {
        // Effort off, but a file brought RPE with it: labelling that history "RIR" would show a
        // number the profile has never entered and mean the opposite of what it says.
        assertEquals("rpe", displayScale(JsonObject(js("effort" to "none") + S(W(3, listOf(js("rpe" to 8), js("rpe" to 9)))))))
        assertEquals("rir", displayScale(JsonObject(js("effort" to "none") + S(W(3, listOf(js("rir" to 2)))))))
        assertEquals("rir", displayScale(js("effort" to "none", "workouts" to emptyList<Any>())))
    }

    @Test
    fun `averages the rated sets and ignores the rest`() {
        assertEquals(2.0, avgRir(JsonArray(listOf(js("rir" to 1), js("rir" to 3))))!!, 0.0)
        assertEquals(2.0, avgRir(JsonArray(listOf(js("rir" to 1), js(), js("rpe" to 7))))!!, 0.0)   // RPE 7 = RIR 3
    }

    @Test
    fun `is null rather than 0 when nothing was rated`() {
        assertNull(avgRir(JsonArray(listOf(js(), js("rir" to null)))))
        assertNull(avgRir(JsonArray(emptyList())))
        assertNull(avgRir(null))
    }

    @Test
    fun `counts rated sets against every finished set, not against itself`() {
        val r = effortSummary(summaryFixture, 0)
        assertEquals(8, r.done)
        assertEquals(7, r.rated)
    }

    @Test
    fun `leaves unrated sets out of the average instead of reading them as failure`() {
        val r = effortSummary(S(W(2, listOf(js("rir" to 4), js(), js(), js(), js(), js()))), 0)
        assertEquals(1, r.rated)
        assertNull(r.avg)        // one rating is not an average
    }

    @Test
    fun `waits for a real sample before reporting a number`() {
        val few = S(W(2, List(MIN_RATED - 1) { js("rir" to 2) }))
        assertNull(effortSummary(few, 0).avg)
        val enough = S(W(2, List(MIN_RATED) { js("rir" to 2) }))
        assertEquals(2.0, effortSummary(enough, 0).avg!!, 0.0)
    }

    @Test
    fun `counts the sets taken close to failure`() {
        val r = effortSummary(summaryFixture, 0)
        assertEquals(4, r.hard)                       // 1, 3, 2, 0 — the 4 and the two 5s are not
        assertEquals(4.0 / 7.0, r.hardPct!!, 0.0)
    }

    @Test
    fun `honours the window`() {
        val r = effortSummary(summaryFixture, 7)
        assertEquals(5, r.rated)                      // the 40-day-old session is out
        assertEquals(3, effortSummary(summaryFixture, 3).rated)
    }

    @Test
    fun `survives a profile with no training at all`() {
        val r = effortSummary(js("workouts" to emptyList<Any>()), 30)
        assertEquals(0, r.done)
        assertEquals(0, r.rated)
        assertEquals(0, r.hard)
        assertNull(r.avg)
        assertNull(r.hardPct)
    }

    @Test
    fun `uses phase as authoritative while retaining legacy boolean warm-up fallback`() {
        val summary = effortSummary(
            S(
                W(
                    1,
                    listOf(
                        js("rir" to 0, "phase" to "warmup"),
                        js("rir" to 1, "phase" to "work", "warmup" to true),
                        js("rir" to 2), js("rir" to 2), js("rir" to 2), js("rir" to 2),
                    ),
                ),
            ),
            0,
        )
        assertEquals(5, summary.done)
        assertEquals(5, summary.rated)
        assertEquals(5, summary.hard)
    }

    @Test
    fun `decides whether the effort UI exists at all`() {
        assertTrue(hasEffort(S(W(2, listOf(js("rir" to 0))))))
        assertTrue(hasEffort(S(W(2, listOf(js("rpe" to 8))))))
        assertFalse(hasEffort(S(W(2, listOf(js(), js("rir" to null))))))
        assertFalse(hasEffort(js("workouts" to emptyList<Any>())))
    }

    @Test
    fun `ignores sets that were never finished`() {
        assertFalse(hasEffort(S(W(2, listOf(js("rir" to 2, "done" to false))))))
    }

    @Test
    fun `averages per calendar week and carries the week volume alongside`() {
        val pts = effortWeeks(S(W(1, listOf(js("rir" to 1), js("rir" to 3), js()))), 0)
        assertEquals(1, pts.size)
        assertEquals(2.0, pts[0].rir, 0.0)
        assertEquals(2, pts[0].n)        // rated
        assertEquals(3, pts[0].sets)     // trained
    }

    @Test
    fun `drops a week that rests on a single tap`() {
        assertTrue(effortWeeks(S(W(1, listOf(js("rir" to 1)))), 0).isEmpty())
    }

    @Test
    fun `comes back oldest first, whatever order the workouts arrived in`() {
        val pts = effortWeeks(S(W(2, listOf(js("rir" to 1), js("rir" to 1))), W(30, listOf(js("rir" to 3), js("rir" to 3)))), 0)
        assertEquals(listOf(3.0, 1.0), pts.map { it.rir })
        assertTrue(pts[0].t < pts[1].t)
    }

    @Test
    fun `bins by whole steps and collapses the far end into a tail`() {
        val h = effortHistogram(S(W(2, listOf(js("rir" to 0), js("rir" to 1.5), js("rir" to 4), js("rir" to 7)))), 0)
        assertEquals(listOf(1, 1, 0, 0, 2), h.map { it.n })
        assertTrue(h[4].tail)
        assertEquals(0.25, h[0].pct, 0.0)
    }

    @Test
    fun `is all zeroes, not NaN, when nothing is rated`() {
        val h = effortHistogram(S(W(2, listOf(js()))), 0)
        assertTrue(h.all { it.n == 0 && it.pct == 0.0 })
    }

    @Test
    fun `draws the line at the effort that actually drives adaptation`() {
        assertTrue(isHardSet(js("rir" to HARD_RIR)))
        assertFalse(isHardSet(js("rir" to (HARD_RIR + 0.5))))
        assertTrue(isHardSet(js("rpe" to 10)))
        assertFalse(isHardSet(js()))          // unrated is not hard, and not easy either
    }

    @Test
    fun `runs hardest-first and covers the whole scale with no gap`() {
        // presets are the quick-pick buttons, top of the scale (0 RIR = failure) first
        assertEquals(listOf(0.0, 0.5, 1.0, 2.0, 3.0, 4.0), EFFORT_PRESETS.map { it.rir })
        // only the last bucket is the collapsed "4+" tail
        assertEquals(listOf(false, false, false, false, false, true), EFFORT_PRESETS.map { it.tail })
        // the bands are contiguous: each band's ceiling is below the next band's floor, and the
        // last reaches infinity so no rating is ever left without a colour
        assertEquals(Double.POSITIVE_INFINITY, EFFORT_BANDS[EFFORT_BANDS.size - 1].max, 0.0)
        for (i in 1 until EFFORT_BANDS.size) {
            assertTrue(EFFORT_BANDS[i].rir > EFFORT_BANDS[i - 1].max)
        }
    }

    @Test
    fun `every preset carries a colour and a human description`() {
        for (p in EFFORT_PRESETS) {
            assertTrue(p.color.startsWith("var(--"))
            assertTrue(p.feel.isNotEmpty())
        }
    }

    @Test
    fun `gives each preset value its own band colour`() {
        // hardest to easiest — the colours the picker shows on its buttons
        assertEquals("var(--purple)", effortColor(0.0))
        assertEquals("var(--red)", effortColor(0.5))
        assertEquals("var(--orange)", effortColor(1.0))
        assertEquals("var(--yellow)", effortColor(2.0))
        assertEquals("var(--green)", effortColor(3.0))
        assertEquals("var(--acc-2)", effortColor(4.0))
    }

    @Test
    fun `colours a typed in-between value by the band it falls in, never leaving it blank`() {
        // a half-step belongs to the harder band below it — the band ceilings are inclusive
        assertEquals(effortColor(1.0), effortColor(1.5))   // orange, not yellow
        assertEquals(effortColor(2.0), effortColor(2.5))   // yellow, not green
        assertEquals(effortColor(0.0), effortColor(0.25))
    }

    @Test
    fun `collapses everything past the top bucket into one colour`() {
        // nobody reliably tells 5 from 7 reps in reserve — they are all "easy"
        assertEquals(effortColor(4.0), effortColor(7.0))
        assertEquals(effortColor(4.0), effortColor(10.0))
    }

    @Test
    fun `is null for an unrated set — colour means a rating, empty has none`() {
        assertNull(effortColor(null))
        assertNull(effortColor(null))   // undefined reads as null here, the same result
    }

    @Test
    fun `reads the same colour whether the set was logged as RIR or RPE`() {
        // the picker colours by internal RIR (via rirOf), so 0 RIR and 10 RPE match, 2 and 8 match
        assertEquals(effortColor(rirOf(js("rir" to 0))), effortColor(rirOf(js("rpe" to 10))))
        assertEquals(effortColor(rirOf(js("rir" to 2))), effortColor(rirOf(js("rpe" to 8))))
        assertEquals(effortColor(rirOf(js("rir" to 0.5))), effortColor(rirOf(js("rpe" to 9.5))))
    }

    @Test
    fun `labels presets on the RIR scale unchanged`() {
        assertEquals(listOf(0.0, 0.5, 1.0, 2.0, 3.0, 4.0), EFFORT_PRESETS.map { toScale("rir", it.rir)!! })
    }

    @Test
    fun `labels the same presets as their RPE mirror for an RPE profile`() {
        assertEquals(listOf(10.0, 9.5, 9.0, 8.0, 7.0, 6.0), EFFORT_PRESETS.map { toScale("rpe", it.rir)!! })
    }
}

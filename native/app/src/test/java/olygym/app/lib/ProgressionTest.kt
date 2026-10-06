package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The spec of frontend/src/lib/progression.test.js and progression.extra-sets.test.js, one case
 * each, in the same order. The vitest spec resolves exercises by name out of the shipped
 * catalogue; here the same names are installed with a target muscle that lands each lift on the
 * step the spec pins (see HEAVY_MUSCLES and ALIAS).
 */
class ProgressionTest {

    private val LIFT = "bench-press"
    private val HEAVY = "back-squat"
    private val PRESS = "overhead-press"

    @Before
    fun installCatalogue() {
        Catalogue.install(FIXTURE)
    }

    // The vitest suite looks a name up in the catalogue so a renamed lift fails the file loudly.
    private fun byName(name: String): String {
        val ex = EXDB.firstOrNull { it.n == name }
            ?: throw IllegalStateException("the catalogue no longer has \"" + name + "\"")
        return ex.id
    }

    // Number formatting is the one place JSON equality would lie: a computed Double prints "50.0"
    // where the spec writes 50. Compare numbers by value, everything else structurally.
    private fun canonical(value: JsonElement?): JsonElement? = when (value) {
        null -> null
        is JsonArray -> JsonArray(value.map { canonical(it)!! })
        is JsonObject -> JsonObject(value.mapValues { canonical(it.value)!! })
        is JsonPrimitive -> {
            val b = value.booleanOrNull
            val d = value.doubleOrNull
            if (!value.isString && b == null && d != null) JsonPrimitive(d) else value
        }
    }

    private fun assertJson(expected: JsonElement?, actual: JsonElement?) {
        assertEquals(canonical(expected), canonical(actual))
    }

    private fun numbers(vararg values: Double): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

    // Build a state whose history is a list of sessions given as [weight, ...repsPerSet].
    // A rep count of null means "the set was never checked off".
    private fun hist(id: String, rows: List<List<Any?>>, target: JsonObject? = null): JsonObject {
        val workouts = rows.mapIndexed { i, row ->
            val w = row[0]
            val t = target ?: js("sets" to 3, "reps" to 5, "weight" to w)
            val sets = row.drop(1).map { r ->
                if (r == null) js("w" to w, "r" to 0, "done" to false)
                else js("w" to w, "r" to r, "done" to true)
            }
            js("d" to ("2026-01-0" + (i + 1)), "entries" to listOf(js("id" to id, "target" to t, "sets" to sets)))
        }
        return js("unit" to "kg", "workouts" to workouts)
    }

    private fun legacy(rows: List<List<Any?>>): JsonObject {
        val workouts = rows.mapIndexed { i, row ->
            val sets = row.drop(1).map { r -> js("w" to row[0], "r" to r, "done" to true) }
            js(
                "d" to ("2026-03-" + (i + 1).toString().padStart(2, '0')),
                "entries" to listOf(js("id" to LIFT, "sets" to sets)),
            )
        }
        return js("unit" to "kg", "workouts" to workouts)
    }

    private fun tgt(w: Any?): JsonObject = js("sets" to 3, "reps" to 5, "weight" to w)

    private fun progEntry(id: String, w: Any?, reps: List<Int>, extra: JsonObject? = null): JsonObject {
        var e = js(
            "id" to id,
            "target" to tgt(w),
            "sets" to reps.map { js("w" to w, "r" to it, "done" to true) },
        )
        extra?.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
        return e
    }

    // ---------------------------------------------------------------- readSession

    private val T = js("sets" to 3, "reps" to 5)

    @Test
    fun `counts a session where every set made its reps as a hit`() {
        val s = readSession(
            js(
                "id" to LIFT, "target" to T,
                "sets" to listOf(
                    js("w" to 60, "r" to 5, "done" to true),
                    js("w" to 60, "r" to 5, "done" to true),
                    js("w" to 60, "r" to 6, "done" to true),
                ),
            ),
        )
        assertEquals(true, s.bool("ok"))
        assertEquals(60.0, s.num("weight")!!, 0.0)
    }

    @Test
    fun `counts short reps as a miss even when the set was checked off`() {
        assertEquals(
            false,
            readSession(
                js(
                    "id" to LIFT, "target" to T,
                    "sets" to listOf(
                        js("w" to 60, "r" to 5, "done" to true),
                        js("w" to 60, "r" to 5, "done" to true),
                        js("w" to 60, "r" to 3, "done" to true),
                    ),
                ),
            ).bool("ok"),
        )
    }

    @Test
    fun `counts an unchecked set as a miss — it was not performed`() {
        val s = readSession(
            js(
                "id" to LIFT, "target" to T,
                "sets" to listOf(
                    js("w" to 60, "r" to 5, "done" to true),
                    js("w" to 60, "r" to 5, "done" to true),
                    js("w" to 60, "r" to 0, "done" to false),
                ),
            ),
        )
        assertEquals(false, s.bool("ok"))
        assertEquals(60.0, s.num("weight")!!, 0.0) // the working weight is still known from the sets that counted
    }

    @Test
    fun `counts fewer sets than prescribed as a miss`() {
        assertEquals(
            false,
            readSession(
                js(
                    "id" to LIFT, "target" to T,
                    "sets" to listOf(js("w" to 60, "r" to 5, "done" to true), js("w" to 60, "r" to 5, "done" to true)),
                ),
            ).bool("ok"),
        )
    }

    @Test
    fun `refuses to call a session a hit when nothing was prescribed`() {
        assertEquals(
            false,
            readSession(js("id" to LIFT, "target" to js(), "sets" to listOf(js("w" to 60, "r" to 5, "done" to true)))).bool("ok"),
        )
    }

    @Test
    fun `reads a timed session by the hold, not by reps`() {
        val s = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 2, "sec" to 45, "mode" to "time"),
                "sets" to listOf(js("sec" to 45, "w" to 0, "done" to true), js("sec" to 50, "w" to 0, "done" to true)),
            ),
        )
        assertEquals("time", s.str("mode"))
        assertEquals(true, s.bool("ok"))
        assertEquals(50.0, s.num("best")!!, 0.0)
        assertEquals(
            false,
            readSession(
                js(
                    "id" to LIFT, "target" to js("sets" to 2, "sec" to 45, "mode" to "time"),
                    "sets" to listOf(js("sec" to 45, "done" to true), js("sec" to 30, "done" to true)),
                ),
            ).bool("ok"),
        )
    }

    // ---------------------------------------------------------------- stallCount

    @Test
    fun `counts consecutive misses back from the most recent session`() {
        assertEquals(0, stallCount(JsonArray(listOf(js("ok" to true), js("ok" to true)))))
        assertEquals(1, stallCount(JsonArray(listOf(js("ok" to true), js("ok" to false)))))
        assertEquals(3, stallCount(JsonArray(listOf(js("ok" to false), js("ok" to false), js("ok" to false)))))
        assertEquals(1, stallCount(JsonArray(listOf(js("ok" to false), js("ok" to true), js("ok" to false)))))
        assertEquals(0, stallCount(JsonArray(emptyList())))
    }

    // ---------------------------------------------------------------- defaultPolicy

    @Test
    fun `is off unless the profile switched automatic progression on`() {
        assertEquals("linear", defaultPolicy(js("autoProg" to true)))
        assertEquals("off", defaultPolicy(js("autoProg" to false)))
        assertEquals("off", defaultPolicy(js())) // every profile written before the setting
        assertEquals("off", defaultPolicy(null))
    }

    // ---------------------------------------------------------------- policyFor

    @Test
    fun `leaves reps work alone until the profile asks for progression`() {
        val on = defaultPolicy(js("autoProg" to true))
        assertEquals("off", policyFor(js("id" to LIFT), null, "reps", defaultPolicy(js())))
        assertEquals("linear", policyFor(js("id" to LIFT), null, "reps", on))
    }

    @Test
    fun `leaves timed work alone even with the setting on`() {
        val on = defaultPolicy(js("autoProg" to true))
        assertEquals("off", policyFor(js("id" to LIFT, "mode" to "time"), null, "time", on))
    }

    @Test
    fun `lets the exercise override the routine, and the routine override the setting`() {
        assertEquals("off", policyFor(js("id" to LIFT, "prog" to "off"), js("prog" to "linear"), "reps", "linear"))
        assertEquals("off", policyFor(js("id" to LIFT), js("prog" to "off"), "reps", "linear"))
        assertEquals("linear", policyFor(js("id" to LIFT, "prog" to "linear"), js("prog" to "off"), "reps", "off"))
        assertEquals("linear", policyFor(js("id" to LIFT), null, "reps", "linear"))
    }

    @Test
    fun `refuses a policy that makes no sense for the mode`() {
        assertEquals("off", policyFor(js("id" to LIFT, "mode" to "time", "prog" to "linear"), null, "time"))
        assertEquals(listOf("off"), POLICIES_FOR["time"])
    }

    // ---------------------------- automatic progression is opt-in

    @Test
    fun `prescribes nothing at all for a profile that never chose`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60)
        assertEquals(js("policy" to "off", "kind" to "off"), nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 5))), cfg))
    }

    @Test
    fun `hands back the linear answer once the profile turns it on`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60)
        val clean = hist(LIFT, listOf(listOf(60, 5, 5, 5)))
        val p = nextPrescription(JsonObject(clean + ("autoProg" to JsonPrimitive(true))), cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(62.5, p.num("weight")!!, 0.0)
    }

    @Test
    fun `does not override a rule the exercise already named, in either direction`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60)
        val clean = hist(LIFT, listOf(listOf(60, 5, 5, 5)))
        assertEquals(
            js("policy" to "off", "kind" to "off"),
            nextPrescription(JsonObject(clean + ("autoProg" to JsonPrimitive(true))), cfg.with("prog", "off")),
        )
        assertEquals("up", nextPrescription(clean, cfg.with("prog", "linear")).str("kind"))
    }

    // ---------------------------------------------------------------- defaultIncrement

    @Test
    fun `gives lower-body lifts the bigger jump`() {
        assertEquals(2.5, defaultIncrement(LIFT), 0.0)
        assertEquals(5.0, defaultIncrement(HEAVY), 0.0)
    }

    @Test
    fun `gives the classic heavy lifts the big step and the Olympic lifts the small one`() {
        val expected = listOf(
            "back squat" to 5.0, "front squat" to 5.0, "split squat" to 5.0,
            "deadlift" to 5.0, "romanian deadlift (rdl)" to 5.0, "good morning" to 5.0,
            "bench press" to 2.5, "bent row" to 2.5, "pull-up" to 2.5,
            "snatch" to 2.5, "clean" to 2.5, "clean-jerk" to 2.5,
            "snatch balance" to 2.5, "snatch pull" to 2.5,
        )
        for ((name, step) in expected) assertEquals(name, step, defaultIncrement(byName(name)), 0.0)
    }

    @Test
    fun `falls back for an unknown exercise`() {
        assertEquals(2.5, defaultIncrement("nope"), 0.0)
    }

    // ---------------------------------------------------------------- weightIncrement

    @Test
    fun `uses a positive exercise override and otherwise the exercise-unit default`() {
        assertEquals(1.0, weightIncrement(js("id" to LIFT, "inc" to 1)), 0.0)
        assertEquals(2.5, weightIncrement(js("id" to LIFT)), 0.0)
        assertEquals(5.0, weightIncrement(js("id" to HEAVY, "inc" to 0)), 0.0)
    }

    // ---------------------------------------------------------------- linear progression

    @Test
    fun `says nothing useful before there is any history`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(js("unit" to "kg", "workouts" to emptyList<Any>()), cfg)
        assertEquals("first", p.str("kind"))
        assertNull(p["weight"])
    }

    @Test
    fun `adds the increment after a clean session`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 5))), cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(62.5, p.num("weight")!!, 0.0)
    }

    @Test
    fun `repeats the weight after a miss instead of advancing`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 3))), cfg)
        assertEquals("hold", p.str("kind"))
        assertEquals(60.0, p.num("weight")!!, 0.0)
    }

    @Test
    fun `does not advance when the last set was left unchecked`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, null))), cfg)
        assertEquals("hold", p.str("kind"))
        assertEquals(60.0, p.num("weight")!!, 0.0)
    }

    @Test
    fun `deloads after three misses in a row, onto a loadable weight`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 3), listOf(60, 5, 4, 4), listOf(60, 5, 5, 4))), cfg)
        assertEquals("deload", p.str("kind"))
        assertEquals(55.0, p.num("weight")!!, 0.0) // 60 × 0.9 = 54 → nearest loadable 2.5 step
        assertEquals(3, DELOAD_AFTER["linear"])
    }

    @Test
    fun `holds a below-step load instead of deloading upward`() {
        // 1 kg with a 2.5 kg step: a cut would snap straight back up to 2.5, so the load holds.
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(
            hist(LIFT, listOf(listOf(1, 1, 1, 1), listOf(1, 1, 1, 1), listOf(1, 1, 1, 1))),
            cfg.with("weight", 1).with("inc", 2.5),
        )
        assertEquals("deload", p.str("kind"))
        assertEquals(1.0, p.num("weight")!!, 0.0)
    }

    @Test
    fun `a good session in between clears the stall`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 3), listOf(60, 5, 5, 5), listOf(60, 5, 5, 3))), cfg)
        assertEquals("hold", p.str("kind"))
    }

    @Test
    fun `a deload starts a new streak, so one miss at the new weight does not deload again`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        // Three misses at 60 kg earn a deload.
        val stalled: List<List<Any?>> = listOf(listOf(60, 4, 4, 4), listOf(60, 4, 4, 4), listOf(60, 4, 4, 4))
        val deload = nextPrescription(hist(LIFT, stalled), cfg)
        assertEquals("deload", deload.str("kind"))

        // A bad day at the lighter weight is the first miss of a new run, not the fourth of the old.
        val next = nextPrescription(hist(LIFT, stalled + listOf(listOf<Any?>(deload.num("weight"), 4, 4, 4))), cfg)
        assertEquals("hold", next.str("kind"))
        assertEquals(deload.num("weight")!!, next.num("weight")!!, 0.0)
    }

    @Test
    fun `never deloads below one increment, however light the lift already is`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(2.5, 1, 1, 1), listOf(2.5, 1, 1, 1), listOf(2.5, 1, 1, 1))), cfg)
        assertEquals("deload", p.str("kind"))
        assertEquals(2.5, p.num("weight")!!, 0.0)
    }

    @Test
    fun `always makes a deload actually lighter, even when rounding would not`() {
        // 20 × 0.9 = 18 → nearest 2.5 step is 17.5, fine. 5 × 0.9 = 4.5 → nearest step is 5,
        // which is no deload at all, so it has to step down instead.
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(5, 1, 1, 1), listOf(5, 1, 1, 1), listOf(5, 1, 1, 1))), cfg)
        assertTrue(p.num("weight")!! < 5.0)
    }

    @Test
    fun `uses the heavier step for a lower-body lift`() {
        val p = nextPrescription(hist(HEAVY, listOf(listOf(100, 5, 5, 5))), js("id" to HEAVY, "sets" to 3, "reps" to 5, "prog" to "linear"))
        assertEquals(105.0, p.num("weight")!!, 0.0)
    }

    @Test
    fun `honours a per-exercise increment override`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 5))), cfg.with("inc", 1))
        assertEquals(61.0, p.num("weight")!!, 0.0)
    }

    // ---------------------------------------------------------------- bodyweight exercises

    @Test
    fun `never invents a weight to deload to — there is nothing to take off a push-up`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(
            hist(LIFT, listOf(listOf(0, 10, 10, 8), listOf(0, 10, 10, 9), listOf(0, 10, 10, 8)), js("sets" to 3, "reps" to 10)),
            cfg,
        )
        assertEquals("hold", p.str("kind"))
        assertEquals(0.0, p.num("weight")!!, 0.0)
        assertEquals(10.0, p.num("reps")!!, 0.0)
    }

    @Test
    fun `progresses in reps instead of load after a clean session`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(0, 10, 10, 10)), js("sets" to 3, "reps" to 10)), cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(0.0, p.num("weight")!!, 0.0)
        assertEquals(11.0, p.num("reps")!!, 0.0)
    }

    @Test
    fun `climbs to the ceiling one rep at a time`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(0, 10, 10, 10)), js("sets" to 3, "reps" to 10)), cfg.with("repsMax", 15))
        assertEquals("up", p.str("kind"))
        assertEquals(11.0, p.num("reps")!!, 0.0)
        assertNull(p["sets"])
    }

    @Test
    fun `leaves a belted set to the normal policies — there is a load to add now`() {
        val belted = hist(LIFT, listOf(listOf(10, 10, 10, 10)), js("sets" to 3, "reps" to 10))
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(belted, cfg.with("bodyweight", true).with("repsMax", 15))
        assertEquals("up", p.str("kind"))
        assertTrue(p.num("weight")!! > 10.0)
        assertNull(p["sets"])
    }

    @Test
    fun `keeps climbing reps forever when no ceiling was set — the old behaviour`() {
        val at30 = hist(LIFT, listOf(listOf(0, 30, 30, 30)), js("sets" to 3, "reps" to 30))
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(at30, cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(31.0, p.num("reps")!!, 0.0)
        assertNull(p["sets"])
    }

    @Test
    fun `applies to linear progression`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(0, 10, 10, 4), listOf(0, 10, 10, 4), listOf(0, 10, 10, 4)), js("sets" to 3, "reps" to 10)), cfg.with("prog", "linear"))
        assertEquals(0.0, p.num("weight")!!, 0.0)
        assertEquals("hold", p.str("kind"))
    }

    @Test
    fun `still adds load the moment the exercise is actually weighted`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 0, "prog" to "linear")
        val p = nextPrescription(hist(LIFT, listOf(listOf(10, 10, 10, 10)), js("sets" to 3, "reps" to 10)), cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(12.5, p.num("weight")!!, 0.0)
    }

    // ---------------------------------------------------------------- policy "off"

    @Test
    fun `has no opinion at all`() {
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 5))), js("id" to LIFT, "sets" to 3, "reps" to 5, "prog" to "off"))
        assertEquals("off", p.str("kind"))
        assertNull(p["weight"])
    }

    // ---------------------------------------------------------------- sessionsFor

    @Test
    fun `skips workouts where the exercise was never actually logged`() {
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js("d" to "2026-01-01", "entries" to listOf(js("id" to LIFT, "target" to js("sets" to 1, "reps" to 5), "sets" to listOf(js("w" to 60, "r" to 5, "done" to true))))),
                js("d" to "2026-01-02", "entries" to listOf(js("id" to LIFT, "target" to js("sets" to 1, "reps" to 5), "sets" to listOf(js("w" to 60, "r" to 0, "done" to false))))),
                js("d" to "2026-01-03", "entries" to listOf(js("id" to "other", "target" to js(), "sets" to listOf(js("w" to 20, "r" to 5, "done" to true))))),
            ),
        )
        assertEquals(listOf("2026-01-01"), sessionsFor(S, LIFT).map { it.asObj()!!.str("d") })
    }

    @Test
    fun `ignores marked deload workouts when it calculates the next regular target`() {
        val base = js("sets" to 3, "reps" to 5, "weight" to 60)
        fun entry(weight: Int, reps: List<Int>): JsonObject = js(
            "id" to LIFT,
            "target" to base.with("weight", weight),
            "sets" to reps.map { js("w" to weight, "r" to it, "done" to true) },
        )
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js("d" to "2026-01-01", "entries" to listOf(entry(60, listOf(5, 5, 5)))),
                js("d" to "2026-01-08", "excludeFromProgression" to true, "entries" to listOf(entry(30, listOf(8, 8)))),
            ),
        )
        assertEquals(listOf("2026-01-01"), sessionsFor(S, LIFT).map { it.asObj()!!.str("d") })
        assertEquals(62.5, nextPrescription(S, js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")).num("weight")!!, 0.0)
    }

    @Test
    fun `reads a legacy entry that has no target without crashing`() {
        val S = js("unit" to "kg", "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(js("id" to LIFT, "sets" to listOf(js("w" to 60, "r" to 5, "done" to true)))))))
        assertEquals(1, sessionsFor(S, LIFT).size)
    }

    // ------------------------ per-entry noProg (combine routines)

    @Test
    fun `skips a noProg entry for that exercise only, in a mixed combined session`() {
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js(
                    "d" to "2026-02-01",
                    "routineIds" to listOf("strength", "rehab"),
                    "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)), progEntry(PRESS, 40, listOf(5, 5, 5), js("noProg" to true))),
                ),
            ),
        )
        assertEquals(1, sessionsFor(S, LIFT).size)
        assertEquals(0, sessionsFor(S, PRESS).size)
    }

    @Test
    fun `still advances the non-excluded exercise of a mixed combined session`() {
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js(
                    "d" to "2026-02-01",
                    "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)), progEntry(PRESS, 40, listOf(5, 5, 5), js("noProg" to true))),
                ),
            ),
        )
        assertEquals(62.5, nextPrescription(S, js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")).num("weight")!!, 0.0)
    }

    @Test
    fun `a noProg gap never becomes the deload stall baseline`() {
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js("d" to "2026-02-01", "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)))),
                js("d" to "2026-02-03", "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)))),
                js("d" to "2026-02-05", "entries" to listOf(progEntry(LIFT, 30, listOf(8, 8, 8), js("noProg" to true)))),
            ),
        )
        assertEquals(listOf("2026-02-01", "2026-02-03"), sessionsFor(S, LIFT).map { it.asObj()!!.str("d") })
        assertEquals(62.5, nextPrescription(S, js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")).num("weight")!!, 0.0)
    }

    @Test
    fun `honours a legacy whole-workout excludeFromProgression flag (all entries skipped)`() {
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js("d" to "2026-02-01", "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)))),
                js("d" to "2026-02-08", "excludeFromProgression" to true, "entries" to listOf(progEntry(LIFT, 30, listOf(8, 8)))),
            ),
        )
        assertEquals(listOf("2026-02-01"), sessionsFor(S, LIFT).map { it.asObj()!!.str("d") })
    }

    @Test
    fun `an exercise only ever logged noProg gives sessionsFor empty and nextPrescription first`() {
        val S = js("unit" to "kg", "workouts" to listOf(js("d" to "2026-02-01", "entries" to listOf(progEntry(LIFT, 30, listOf(8, 8, 8), js("noProg" to true))))))
        assertEquals(0, sessionsFor(S, LIFT).size)
        assertEquals("first", nextPrescription(S, js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 50, "prog" to "linear")).str("kind"))
    }

    @Test
    fun `entryExcluded truth table`() {
        assertEquals(false, entryExcluded(js(), js()))
        assertEquals(true, entryExcluded(js("excludeFromProgression" to true), js()))
        assertEquals(true, entryExcluded(js(), js("noProg" to true)))
        assertEquals(true, entryExcluded(js("excludeFromProgression" to true), js("noProg" to true)))
    }

    @Test
    fun `stallCount is not reset by a noProg gap at the same weight`() {
        // three real misses at 60, with a noProg 60 session interleaved — still streaks to a deload
        fun miss(d: String): JsonObject = js("d" to d, "entries" to listOf(progEntry(LIFT, 60, listOf(4, 4, 4))))
        val S = js(
            "unit" to "kg",
            "workouts" to listOf(
                js("d" to "2026-02-01", "entries" to listOf(progEntry(LIFT, 60, listOf(5, 5, 5)))),
                miss("2026-02-03"),
                js("d" to "2026-02-04", "entries" to listOf(progEntry(LIFT, 60, listOf(3, 3, 3), js("noProg" to true)))),
                miss("2026-02-05"),
                miss("2026-02-07"),
            ),
        )
        val sessions = sessionsFor(S, LIFT)
        assertEquals(listOf("2026-02-01", "2026-02-03", "2026-02-05", "2026-02-07"), sessions.map { it.asObj()!!.str("d") })
        assertEquals(3, stallCount(sessions))
        assertEquals("deload", nextPrescription(S, js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")).str("kind"))
    }

    // ------------------------ history logged before targets were recorded

    @Test
    fun `judges a targetless session against the current plan instead of calling it a miss`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(legacy(listOf(listOf(60, 5, 5, 5))), cfg)
        assertEquals("up", p.str("kind"))
        assertEquals(62.5, p.num("weight")!!, 0.0)
    }

    @Test
    fun `does not manufacture a stall out of a long clean history`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        val p = nextPrescription(legacy(List(11) { listOf<Any?>(60, 5, 5, 5) }), cfg)
        assertEquals("up", p.str("kind"))
    }

    @Test
    fun `still spots a genuine miss in old data`() {
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        assertEquals("hold", nextPrescription(legacy(listOf(listOf(60, 5, 5, 2))), cfg).str("kind"))
    }

    @Test
    fun `matches the weight hint the app showed before this engine existed`() {
        // Old rule: every set at or above the plan's reps, with a real weight → suggest a step up.
        val cfg = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear")
        assertEquals(62.5, nextPrescription(legacy(listOf(listOf(60, 5, 6, 5))), cfg).num("weight")!!, 0.0)
        assertEquals("hold", nextPrescription(legacy(listOf(listOf(60, 5, 4, 5))), cfg).str("kind"))
    }

    // ---------------------------------------------------------------- applyPrescription

    private val sets = JsonArray(
        listOf(js("w" to 60, "r" to 5, "done" to true), js("w" to 60, "r" to 5, "done" to false)),
    )

    @Test
    fun `rewrites only what the policy decided, and only unlogged sets`() {
        val out = applyPrescription(sets, js("kind" to "up", "weight" to 62.5))
        assertJson(js("w" to 60, "r" to 5, "done" to true), out[0])
        assertJson(js("w" to 62.5, "r" to 5, "done" to false), out[1])
    }

    @Test
    fun `sets reps too when the policy has an opinion about them`() {
        assertJson(js("w" to 42.5, "r" to 8, "done" to false), applyPrescription(sets, js("kind" to "up", "weight" to 42.5, "reps" to 8))[1])
    }

    @Test
    fun `touches nothing for off or a first session`() {
        assertSame(sets, applyPrescription(sets, js("kind" to "off")))
        assertSame(sets, applyPrescription(sets, js("kind" to "first")))
        assertSame(sets, applyPrescription(sets, null))
    }

    @Test
    fun `adjusts a timed set without inventing a weight`() {
        val timed = JsonArray(listOf(js("sec" to 45, "w" to 0, "done" to false)))
        assertJson(JsonArray(listOf(js("sec" to 50, "w" to 0, "done" to false))), applyPrescription(timed, js("kind" to "up", "sec" to 50)))
    }

    @Test
    fun `never shrinks a session that has already logged sets`() {
        assertEquals(sets.size, applyPrescription(sets, js("kind" to "up", "weight" to 60, "sets" to 1)).size)
    }

    // ------------------------ warm-up rows in session reads (round 3)

    @Test
    fun `readSession ignores warm-up rows for reps and ok`() {
        // An undone warm-up (r 0) must not poison ok forever; its lighter reps must not drag the
        // work-row read - the warm-up is prep, the session is the work rows.
        val s = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 2, "reps" to 5, "mode" to "reps"),
                "sets" to listOf(
                    js("w" to 20, "r" to 8, "done" to true, "warmup" to true),
                    js("w" to 60, "r" to 5, "done" to true),
                    js("w" to 60, "r" to 6, "done" to true),
                ),
            ),
        )
        assertJson(numbers(5.0, 6.0), s["reps"])
        assertEquals(true, s.bool("ok"))
    }

    @Test
    fun `readSession keeps an undone warm-up out of held and ok in time mode`() {
        val s = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 2, "sec" to 45, "mode" to "time"),
                "sets" to listOf(
                    js("sec" to 45, "done" to true, "warmup" to true),
                    js("sec" to 45, "done" to true),
                    js("sec" to 30, "done" to true),
                ),
            ),
        )
        assertJson(numbers(45.0, 30.0), s["held"])
        assertEquals(false, s.bool("ok")) // the 30s work row is the miss, not the warm-up
    }

    @Test
    fun `uses phase as authoritative and falls back to the legacy warm-up flag`() {
        val s = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 1, "reps" to 5),
                "sets" to listOf(
                    js("phase" to "warmup", "w" to 120, "r" to 20, "done" to true),
                    js("phase" to "work", "warmup" to true, "w" to 60, "r" to 5, "done" to true),
                ),
            ),
        )
        assertEquals(60.0, s.num("weight")!!, 0.0)
        assertJson(numbers(5.0), s["reps"])
        assertEquals(true, s.bool("ok"))
    }

    // ------------------------ applyPrescription never touches warm-up rows (round 3)

    @Test
    fun `leaves a done warm-up exactly as logged`() {
        val rows = JsonArray(
            listOf(
                js("w" to 20, "r" to 8, "done" to true, "warmup" to true),
                js("w" to 60, "r" to 5, "done" to true),
                js("w" to 60, "r" to 5, "done" to false),
            ),
        )
        val out = applyPrescription(rows, js("kind" to "up", "weight" to 62.5, "reps" to 5))
        assertJson(js("w" to 20, "r" to 8, "done" to true, "warmup" to true), out[0])
        assertJson(js("w" to 60, "r" to 5, "done" to true), out[1])
        assertJson(js("w" to 62.5, "r" to 5, "done" to false), out[2])
    }

    @Test
    fun `an all-warm-up entry terminates and stays untouched`() {
        val rows = JsonArray(
            listOf(
                js("w" to 20, "r" to 8, "done" to true, "warmup" to true),
                js("w" to 25, "r" to 6, "done" to true, "warmup" to true),
            ),
        )
        val out = applyPrescription(rows, js("kind" to "up", "weight" to 62.5, "reps" to 5, "sets" to 4))
        assertJson(rows, out) // no work row to seed growth from - nothing grows, no loop
    }

    // ------------------------ drop-sets and rest-pause sets in progression

    @Test
    fun `readSession judges a drop-set row on its own main weight and reps, ignoring the drops`() {
        val withDrops = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 1, "reps" to 5),
                "sets" to listOf(js("type" to "dropset", "w" to 60, "r" to 5, "done" to true, "drops" to listOf(js("w" to 40, "r" to 8), js("w" to 20, "r" to 10)))),
            ),
        )
        val plain = readSession(js("id" to LIFT, "target" to js("sets" to 1, "reps" to 5), "sets" to listOf(js("w" to 60, "r" to 5, "done" to true))))
        assertJson(plain, withDrops)
    }

    @Test
    fun `readSession judges a rest-pause row on its activation weight and reps, ignoring the bursts`() {
        val withBursts = readSession(
            js(
                "id" to LIFT, "target" to js("sets" to 1, "reps" to 8),
                "sets" to listOf(js("type" to "restpause", "w" to 60, "r" to 8, "done" to true, "clusters" to listOf(js("r" to 4, "restSec" to 15), js("r" to 3, "restSec" to 15)))),
            ),
        )
        val plain = readSession(js("id" to LIFT, "target" to js("sets" to 1, "reps" to 8), "sets" to listOf(js("w" to 60, "r" to 8, "done" to true))))
        assertJson(plain, withBursts)
    }

    @Test
    fun `applyPrescription still rewrites a drop-set own weight, leaving its drops untouched`() {
        val rows = JsonArray(listOf(js("type" to "dropset", "w" to 60, "r" to 5, "done" to false, "drops" to listOf(js("w" to 40, "r" to 8)))))
        val out = applyPrescription(rows, js("kind" to "up", "weight" to 62.5))
        assertJson(js("type" to "dropset", "w" to 62.5, "r" to 5, "done" to false, "drops" to listOf(js("w" to 40, "r" to 8))), out[0])
    }

    // ------------------------ a weight off the increment grid (issue #175)

    @Test
    fun `adds the step instead of snapping the sum to the grid`() {
        // A sled logged as its own 167 lb plus plates: 397 with a 10 lb step goes to 407, not 410.
        val lin = js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 397, "prog" to "linear", "inc" to 10)
        val p = nextPrescription(hist(LIFT, listOf(listOf(397, 5, 5, 5)), js("sets" to 3, "reps" to 5, "weight" to 397)), lin)
        assertEquals("up", p.str("kind"))
        assertEquals(407.0, p.num("weight")!!, 0.0)
    }

    @Test
    fun `still snaps from a weight that sits on the grid`() {
        val p = nextPrescription(hist(LIFT, listOf(listOf(60, 5, 5, 5))), js("id" to LIFT, "sets" to 3, "reps" to 5, "weight" to 60, "prog" to "linear", "inc" to 2.5))
        assertEquals(62.5, p.num("weight")!!, 0.0)
    }

    // ------------------------ progression.extra-sets.test.js (issue #233)

    private fun set(w: Int, r: Int, done: Boolean = true): JsonObject = js("w" to w, "r" to r, "done" to done)

    private fun extraEntry(target: JsonObject, sets: List<JsonObject>): JsonObject = js("id" to "0025", "target" to target, "sets" to sets)

    private val plan = js("mode" to "reps", "sets" to 3, "reps" to 8, "weight" to 60)

    @Test
    fun `ignores a heavier bonus set when reading the weight`() {
        val done = readSession(extraEntry(plan, listOf(set(60, 8), set(60, 8), set(60, 8), set(80, 8))))
        assertEquals(60.0, done.num("weight")!!, 0.0)
        assertEquals(true, done.bool("ok"))
    }

    @Test
    fun `does not call the session missed because a bonus set fell short`() {
        val done = readSession(extraEntry(plan, listOf(set(60, 8), set(60, 8), set(60, 8), set(80, 3))))
        assertEquals(true, done.bool("ok"))
        // The bonus set sits outside the planned three, so it never reaches the read.
        assertJson(numbers(8.0, 8.0, 8.0), done["reps"])
    }

    @Test
    fun `still reports a short session as short, and still reads every planned set`() {
        val short = readSession(extraEntry(plan, listOf(set(60, 8), set(60, 5), set(60, 8))))
        assertEquals(false, short.bool("ok"))
        assertJson(numbers(8.0, 5.0, 8.0), short["reps"])
        val missing = readSession(extraEntry(plan, listOf(set(60, 8), set(60, 8))))
        assertEquals(false, missing.bool("ok"))
    }

    @Test
    fun `reads everything when the plan names no set count (freestyle)`() {
        val free = readSession(extraEntry(js("mode" to "reps", "reps" to 8), listOf(set(60, 8), set(80, 8))))
        assertEquals(80.0, free.num("weight")!!, 0.0)
        assertJson(numbers(8.0, 8.0), free["reps"])
    }

    @Test
    fun `leaves warm-ups out before counting, so a warm-up does not eat a planned slot`() {
        val warm = readSession(
            extraEntry(
                plan,
                listOf(js("w" to 20, "r" to 10, "done" to true, "phase" to "warmup"), set(60, 8), set(60, 8), set(60, 8), set(90, 8)),
            ),
        )
        assertEquals(60.0, warm.num("weight")!!, 0.0)
        assertEquals(true, warm.bool("ok"))
    }

    @Test
    fun `applies the same rule to a timed hold`() {
        val timed = js("mode" to "time", "sets" to 2, "sec" to 30, "weight" to 0)
        val held = readSession(extraEntry(timed, listOf(js("sec" to 30, "done" to true), js("sec" to 30, "done" to true), js("sec" to 12, "done" to true))))
        assertEquals(true, held.bool("ok"))
        assertEquals(30.0, held.num("best")!!, 0.0)
    }
}

private val FIXTURE = listOf(
    Exercise(id = "bench-press", n = "bench press", bp = "chest", eq = "barbell", tg = "pectorals"),
    Exercise(id = "back-squat", n = "back squat", bp = "upper legs", eq = "barbell", tg = "quads"),
    Exercise(id = "front-squat", n = "front squat", bp = "upper legs", eq = "barbell", tg = "quads"),
    Exercise(id = "split-squat", n = "split squat", bp = "upper legs", eq = "barbell", tg = "quads"),
    Exercise(id = "deadlift", n = "deadlift", bp = "back", eq = "barbell", tg = "hamstrings"),
    Exercise(id = "romanian-deadlift-rdl", n = "romanian deadlift (rdl)", bp = "back", eq = "barbell", tg = "hamstrings"),
    Exercise(id = "good-morning", n = "good morning", bp = "back", eq = "barbell", tg = "hamstrings"),
    Exercise(id = "bent-row", n = "bent row", bp = "back", eq = "barbell", tg = "lats"),
    Exercise(id = "pull-up", n = "pull-up", bp = "back", eq = "body weight", tg = "lats"),
    Exercise(id = "snatch", n = "snatch", bp = "Snatch", eq = "barbell", tg = "traps"),
    Exercise(id = "clean", n = "clean", bp = "Clean", eq = "barbell", tg = "traps"),
    Exercise(id = "clean-jerk", n = "clean-jerk", bp = "Clean", eq = "barbell", tg = "traps"),
    Exercise(id = "snatch-balance", n = "snatch balance", bp = "Snatch", eq = "barbell", tg = "traps"),
    Exercise(id = "snatch-pull", n = "snatch pull", bp = "Snatch", eq = "barbell", tg = "traps"),
    Exercise(id = "overhead-press", n = "overhead press", bp = "shoulders", eq = "barbell", tg = "delts"),
)

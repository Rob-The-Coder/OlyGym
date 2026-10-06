package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The spec of frontend/src/lib/history.test.js, one case each, in the same order. */
class HistoryTest {

    // A loaded lift: a barbell movement, so every label test takes the ordinary path instead of the
    // bodyweight one. The vitest spec finds the same pair in the shipped catalogue; here they are
    // installed so isBodyweightEq has something to read.
    private val LIFT = "test-barbell"
    private val BW = "test-bodyweight"

    private val TUE = "2026-08-18"
    private val MONDAY = "2026-08-17"

    @Before
    fun installCatalogue() {
        Catalogue.install(
            listOf(
                Exercise(id = LIFT, n = "test barbell lift", bp = "legs", eq = "barbell"),
                Exercise(id = BW, n = "test bodyweight movement", bp = "chest", eq = "body weight"),
            ),
        )
    }

    private val emptyS = js("workouts" to emptyList<Any>(), "exWeights" to js())

    private fun arr(vararg values: Any?): JsonArray = JsonArray(values.map { it.toJson() })

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

    private fun entry(
        id: String,
        sets: List<JsonObject>,
        target: JsonObject? = null,
        extra: JsonObject? = null,
    ): JsonObject {
        var e = js("id" to id, "sets" to sets)
        if (target != null) e = JsonObject(e + ("target" to target))
        extra?.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
        return e
    }

    private fun week(days: List<JsonObject>, startIso: String = MONDAY): JsonArray =
        arr(js("id" to "w1", "startIso" to startIso, "name" to "", "days" to days))

    private fun dayOn(dow: Int, name: String, ex: List<JsonObject> = listOf(js("id" to "0001"))): JsonObject =
        js("dow" to dow, "name" to name, "ex" to ex)

    private fun wk(d: String, w: Int, r: Int, extra: JsonObject? = null): JsonObject {
        var e = js(
            "id" to LIFT,
            "target" to js("sets" to 1, "reps" to r, "weight" to w),
            "sets" to listOf(js("w" to w, "r" to r, "done" to true)),
        )
        extra?.forEach { (k, v) -> e = JsonObject(e + (k to v)) }
        return js("d" to d, "entries" to listOf(e))
    }

    private fun doneRow(w: Any?, r: Any?): JsonObject = js("w" to w, "r" to r, "done" to true)

    // ---------------------------------------------------------------- modeOf

    @Test
    fun `falls back to the body part when a plan has no mode — every existing plan keeps working`() {
        assertEquals("reps", modeOf(js("id" to LIFT)))
        assertEquals("reps", modeOf(js("id" to "no-such-exercise")))
        assertEquals("reps", modeOf(js()))
        assertEquals("reps", modeOf(null))
        assertEquals("reps", modeOf(null)) // undefined reads the same as null
    }

    @Test
    fun `lets an explicit mode win over the body part`() {
        assertEquals("time", modeOf(js("id" to LIFT, "mode" to "time")))
        assertEquals("reps", modeOf(js("id" to LIFT, "mode" to "reps")))
    }

    @Test
    fun `coerces the retired cardio mode to reps rather than trusting a bad file`() {
        assertEquals("reps", modeOf(js("id" to LIFT, "mode" to "cardio")))
        assertEquals("reps", modeOf(js("id" to LIFT, "mode" to "nonsense")))
        assertEquals("reps", modeOf(js("id" to LIFT, "mode" to "")))
    }

    @Test
    fun `exposes the timed check`() {
        assertEquals(true, isTimed(js("id" to LIFT, "mode" to "time")))
        assertEquals(false, isTimed(js("id" to LIFT)))
    }

    // ---------------------------------------------------------------- fmtSec

    @Test
    fun `reads as a clock, not a pile of seconds`() {
        assertEquals("0:00", fmtSec(0.0))
        assertEquals("0:09", fmtSec(9.0))
        assertEquals("0:45", fmtSec(45.0))
        assertEquals("1:00", fmtSec(60.0))
        assertEquals("1:30", fmtSec(90.0))
        assertEquals("10:05", fmtSec(605.0))
    }

    @Test
    fun `is defensive about junk input`() {
        assertEquals("0:00", fmtSec(-5.0))
        assertEquals("0:00", fmtSec(null))
        assertEquals("0:00", fmtSec(null)) // undefined reads the same as null
        assertEquals("0:00", fmtSec(Double.NaN))
        assertEquals("0:45", fmtSec(44.6))
    }

    // ---------------------------------------------------------------- setLabel

    @Test
    fun `describes each mode in its own terms`() {
        assertEquals("60×10", setLabel(LIFT, js("w" to 60, "r" to 10)))
        assertEquals("0:45", setLabel(LIFT, js("sec" to 45, "w" to 0), js("mode" to "time")))
        assertEquals("1:30 · 20", setLabel(LIFT, js("sec" to 90, "w" to 20), js("mode" to "time")))
    }

    @Test
    fun `reads a legacy set with no config exactly as before`() {
        assertEquals("0×0", setLabel(LIFT, js("w" to 0, "r" to 0)))
    }

    @Test
    fun `appends RIR when present, including a valid 0`() {
        assertEquals("60×10 (RIR 2)", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to 2)))
        assertEquals("60×10 (RIR 1.5)", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to 1.5)))
        assertEquals("60×10 (RIR 0)", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to 0)))
    }

    @Test
    fun `says nothing about RIR on a set that never logged one`() {
        assertEquals("60×10", setLabel(LIFT, js("w" to 60, "r" to 10)))
        // cleared in the UI: the key is dropped, but a null must read the same as absent
        assertEquals("60×10", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to null)))
    }

    @Test
    fun `appends RPE for a set logged on that scale`() {
        assertEquals("60×10 (RPE 8)", setLabel(LIFT, js("w" to 60, "r" to 10, "rpe" to 8)))
        assertEquals("60×10 (RPE 9.5)", setLabel(LIFT, js("w" to 60, "r" to 10, "rpe" to 9.5)))
        assertEquals("60×10", setLabel(LIFT, js("w" to 60, "r" to 10, "rpe" to null)))
    }

    @Test
    fun `keeps each set on the scale it was logged with`() {
        assertEquals("60×10 (RIR 2)", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to 2)))
        assertEquals("60×10 (RIR 2)", setLabel(LIFT, js("w" to 60, "r" to 10, "rir" to 2, "rpe" to 8)))
    }

    // ---------------------------------------------------------------- effortOf

    @Test
    fun `reads the scale a profile logs`() {
        assertEquals("rpe", effortOf(js("effort" to "rpe")))
        assertEquals("rir", effortOf(js("effort" to "rir")))
        assertEquals("none", effortOf(js("effort" to "none")))
        assertEquals("none", effortOf(js()))
    }

    @Test
    fun `keeps the column for a profile still carrying the old showRir flag`() {
        assertEquals("rir", effortOf(js("showRir" to true)))
        assertEquals("rir", effortOf(js("effort" to null, "showRir" to true)))
        assertEquals("none", effortOf(js("effort" to null)))
        assertEquals("none", effortOf(js("showRir" to false)))
        assertEquals("rpe", effortOf(js("showRir" to true, "effort" to "rpe")))
        assertEquals("none", effortOf(js("showRir" to true, "effort" to "none")))
    }

    @Test
    fun `survives the overlay every load path performs`() {
        // The store cannot be imported here, so the overlay it performs is reproduced literally:
        // stored profile spread over the defaults, whose effort is null.
        assertEquals("rir", effortOf(js("unit" to "kg", "showRir" to true)))
        assertEquals("none", effortOf(js("unit" to "kg", "showRir" to false)))
        assertEquals("none", effortOf(js("unit" to "kg")))
        assertEquals("rpe", effortOf(js("unit" to "kg", "effort" to "rpe")))
        assertEquals("rir", effortOf(js("unit" to "kg", "showRir" to true)))
    }

    @Test
    fun `is not fooled by a junk value`() {
        assertEquals("none", effortOf(js("effort" to "rpe10")))
        assertEquals("none", effortOf(js("effort" to "RIR")))
        assertEquals("none", effortOf(js("effort" to "f")))
        assertEquals("none", effortOf(null))
        assertEquals("none", effortOf(null))
        assertEquals("rir", effortOf(js("effort" to "nope", "showRir" to true)))
    }

    // ---------------------------------------------------------------- stepEffort

    @Test
    fun `starts at the bottom of the scale and walks up`() {
        assertEquals(0.0, stepEffort("rir", null, 1)!!, 0.0)
        assertEquals(6.0, stepEffort("rpe", null, 1)!!, 0.0)
        assertEquals(0.5, stepEffort("rir", 0.0, 1)!!, 0.0)
        assertEquals(1.0, stepEffort("rir", 0.5, 1)!!, 0.0)
        assertEquals(6.5, stepEffort("rpe", 6.0, 1)!!, 0.0)
    }

    @Test
    fun `leaves an untouched cell unlogged when stepped down`() {
        assertNull(stepEffort("rir", null, -1))
        assertNull(stepEffort("rpe", null, -1))
        assertNull(stepEffort("rir", null, -1)) // undefined reads the same as null
    }

    @Test
    fun `clears the cell again when stepped back off the floor`() {
        assertNull(stepEffort("rir", 0.0, -1))
        assertNull(stepEffort("rpe", 6.0, -1))
        assertEquals(0.0, stepEffort("rir", 0.5, -1)!!, 0.0)
        assertEquals(6.0, stepEffort("rpe", 6.5, -1)!!, 0.0)
    }

    @Test
    fun `stops at the top of the scale`() {
        assertEquals(10.0, stepEffort("rir", 9.5, 1)!!, 0.0)
        assertEquals(10.0, stepEffort("rir", 10.0, 1)!!, 0.0)
        assertEquals(10.0, stepEffort("rpe", 10.0, 1)!!, 0.0)
    }

    @Test
    fun `keeps halves clean instead of drifting into float dust`() {
        var v: Double? = null
        for (i in 0 until 6) v = stepEffort("rpe", v, 1)
        assertEquals(8.5, v!!, 0.0)
        assertEquals(0.8, stepEffort("rir", 0.1 + 0.2, 1)!!, 0.0)
    }

    @Test
    fun `steps evenly from a value typed below the floor rather than snapping`() {
        assertEquals(3.5, stepEffort("rpe", 3.0, 1)!!, 0.0)
        assertNull(stepEffort("rpe", 3.0, -1))
    }

    @Test
    fun `does nothing when the profile logs no effort at all`() {
        assertNull(stepEffort("none", null, 1))
        assertEquals(2.0, stepEffort("none", 2.0, 1)!!, 0.0)
        assertEquals(2.0, stepEffort(null, 2.0, -1)!!, 0.0)
    }

    // ---------------------------------------------------------------- capEffort

    @Test
    fun `caps a typed value at the top of the scale`() {
        assertEquals(10.0, capEffort("rir", 12.0)!!, 0.0)
        assertEquals(10.0, capEffort("rpe", 99.0)!!, 0.0)
        assertEquals(8.0, capEffort("rpe", 8.0)!!, 0.0)
    }

    @Test
    fun `does not floor a typed value, so typing double-one-zero survives its first keystroke`() {
        assertEquals(1.0, capEffort("rpe", 1.0)!!, 0.0)
        assertEquals(0.0, capEffort("rir", 0.0)!!, 0.0)
    }

    @Test
    fun `passes an emptied field through untouched`() {
        assertNull(capEffort("rir", null))
        assertNull(capEffort("rpe", null)) // undefined reads the same as null
        assertEquals(12.0, capEffort("none", 12.0)!!, 0.0)
    }

    // ------------------------------------------------- logging effort across a session

    @Test
    fun `logs a working set on the chosen scale`() {
        var v: Double? = null
        for (i in 0 until 4) v = stepEffort("rpe", v, 1)
        assertEquals("80×5 (RPE 7.5)", setLabel(LIFT, js("w" to 80, "r" to 5, "rpe" to v)))
    }

    @Test
    fun `a set taken to failure is logged, not left blank`() {
        val v = stepEffort("rir", null, 1)
        assertEquals(0.0, v!!, 0.0)
        assertEquals("100×3 (RIR 0)", setLabel(LIFT, js("w" to 100, "r" to 3, "rir" to v)))
    }

    @Test
    fun `switching the setting mid-history rewrites nothing`() {
        val old = js("w" to 60, "r" to 10, "rir" to 2)
        val fresh = js("w" to 60, "r" to 10, "rpe" to 8)
        assertEquals("rpe", effortOf(js("effort" to "rpe")))
        assertEquals("60×10 (RIR 2)", setLabel(LIFT, old))
        assertEquals("60×10 (RPE 8)", setLabel(LIFT, fresh))
        assertEquals("none", effortOf(js("effort" to "none")))
        assertEquals("60×10 (RIR 2)", setLabel(LIFT, old))
    }

    @Test
    fun `never attaches effort to a timed set, which has no place for it`() {
        assertEquals("0:45", setLabel(LIFT, js("sec" to 45, "rir" to 2), js("id" to LIFT, "mode" to "time")))
    }

    // ---------------------------------------------------------------- defaultConfig

    @Test
    fun `gives each mode a sensible starting point`() {
        assertJson(js("sets" to 3, "reps" to 10, "weight" to 0, "mode" to "reps"), defaultConfig(LIFT))
        assertJson(js("sets" to 3, "sec" to 45, "weight" to 0, "mode" to "time"), defaultConfig(LIFT, "time"))
    }

    @Test
    fun `seeds the bodyweight flag from the catalogue, and only when it is true`() {
        assertJson(
            js("sets" to 3, "reps" to 10, "weight" to 0, "mode" to "reps", "bodyweight" to true),
            defaultConfig(BW),
        )
        assertJson(
            js("sets" to 3, "sec" to 45, "weight" to 0, "mode" to "time", "bodyweight" to true),
            defaultConfig(BW, "time"),
        )
        assertFalse(defaultConfig(LIFT).containsKey("bodyweight"))
    }

    // ------------------------------------------- bodyweight and per side (issues 31/32/33)

    @Test
    fun `defaults from the catalogue so an existing plan needs no flag`() {
        assertEquals(true, isBw(js("id" to BW)))
        assertEquals(false, isBw(js("id" to LIFT)))
    }

    @Test
    fun `lets the config win in both directions — a belt on a dip, a flag on a machine`() {
        assertEquals(false, isBw(js("id" to BW, "bodyweight" to false)))
        assertEquals(true, isBw(js("id" to LIFT, "bodyweight" to true)))
    }

    @Test
    fun `exLine ignores a stale side flag on a hold, which has no reps to split`() {
        assertEquals("3 × 0:45", exLine(js("id" to LIFT, "sets" to 3, "sec" to 45, "mode" to "time", "side" to true), "kg"))
    }

    @Test
    fun `setLabel reads bodyweight as reps alone, because 0x12 describes nothing`() {
        assertEquals("12", setLabel(BW, js("w" to 0, "r" to 12), js("id" to BW)))
    }

    @Test
    fun `setLabel spells out a belt as an addition`() {
        assertEquals("+10 × 8", setLabel(BW, js("w" to 10, "r" to 8), js("id" to BW)))
    }

    @Test
    fun `setLabel logs a per-side set as the plain total, like every other set in the app`() {
        assertEquals("16", setLabel(BW, js("w" to 0, "r" to 16), js("id" to BW, "side" to true)))
        assertEquals("20×16", setLabel(LIFT, js("w" to 20, "r" to 16), js("id" to LIFT, "side" to true)))
    }

    @Test
    fun `setLabel keeps the effort tail`() {
        assertEquals("12 (RIR 2)", setLabel(BW, js("w" to 0, "r" to 12, "rir" to 2), js("id" to BW)))
    }

    @Test
    fun `exLine marks added weight as added`() {
        assertEquals("3 × 8 · +10 kg", exLine(js("id" to BW, "sets" to 3, "reps" to 8, "weight" to 10), "kg"))
    }

    @Test
    fun `exLine summarises a planned exercise per mode`() {
        assertEquals("3 × 10", exLine(js("id" to LIFT, "sets" to 3, "reps" to 10), "kg"))
        assertEquals("3 × 10 · 60 kg", exLine(js("id" to LIFT, "sets" to 3, "reps" to 10, "weight" to 60), "kg"))
        assertEquals("3 × 0:45", exLine(js("id" to LIFT, "sets" to 3, "sec" to 45, "mode" to "time"), "kg"))
        assertEquals("2 × 1:30 · 20 kg", exLine(js("id" to LIFT, "sets" to 2, "sec" to 90, "weight" to 20, "mode" to "time"), "kg"))
    }

    // ---------------------------------------------------------------- freestyleConfig

    @Test
    fun `inherits the last target and completed set count for a newly added exercise`() {
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-01",
                    "entries" to listOf(
                        entry(
                            LIFT,
                            listOf(doneRow(60, 8), doneRow(62.5, 7), doneRow(62.5, 6), doneRow(62.5, 5)),
                            js("mode" to "reps", "sets" to 4, "reps" to 8, "weight" to 60, "prog" to "linear"),
                        ),
                    ),
                ),
            ),
        )
        val cfg = freestyleConfig(S, js("id" to LIFT, "mode" to "reps", "sets" to 3, "reps" to 10, "weight" to 0))
        assertJson(
            js("id" to LIFT, "mode" to "reps", "sets" to 4, "reps" to 8, "weight" to 60, "prog" to "linear"),
            cfg,
        )
        assertJson(
            arr(
                js("w" to 60, "r" to 8, "done" to false),
                js("w" to 62.5, "r" to 7, "done" to false),
                js("w" to 62.5, "r" to 6, "done" to false),
                js("w" to 62.5, "r" to 5, "done" to false),
            ),
            buildSets(S, cfg),
        )
    }

    @Test
    fun `adds no rows when the config asks for no warm-ups`() {
        val S = js("exWeights" to js(), "workouts" to emptyList<Any>())
        val cfg = js("id" to "0025", "mode" to "reps", "sets" to 2, "reps" to 5, "weight" to 100)
        assertJson(arr(js("w" to 100, "r" to 5, "done" to false), js("w" to 100, "r" to 5, "done" to false)), buildSets(S, cfg))
    }

    @Test
    fun `prepends the planned warm-ups as a ramp toward the work weight`() {
        val S = js("exWeights" to js(), "workouts" to emptyList<Any>())
        val cfg = js("id" to "0025", "mode" to "reps", "sets" to 2, "reps" to 5, "weight" to 100, "warmupSets" to 3)
        val rows = buildSets(S, cfg, js("step" to 2.5))
        assertEquals(listOf<Double?>(50.0, 75.0, 87.5, 100.0, 100.0), rows.map { it.asObj()?.num("w") })
        assertTrue(rows.take(3).all { it.asObj()?.str("phase") == "warmup" })
        assertTrue(rows.drop(3).all { it.asObj()?.str("phase") == null })
    }

    @Test
    fun `caps the planned warm-ups and ignores nonsense values`() {
        val S = js("exWeights" to js(), "workouts" to emptyList<Any>())
        val base = js("id" to "0025", "mode" to "reps", "sets" to 1, "reps" to 5, "weight" to 100)
        val many = JsonObject(base + ("warmupSets" to JsonPrimitive(99)))
        assertEquals(5, buildSets(S, many, js("step" to 2.5)).count { it.asObj()?.str("phase") == "warmup" })
        val negative = JsonObject(base + ("warmupSets" to JsonPrimitive(-2)))
        assertEquals(1, buildSets(S, negative, js("step" to 2.5)).size)
        val nonsense = JsonObject(base + ("warmupSets" to JsonPrimitive("x")))
        assertEquals(1, buildSets(S, nonsense, js("step" to 2.5)).size)
    }

    @Test
    fun `keeps planned warm-ups out of the work-set count`() {
        val S = js("exWeights" to js(), "workouts" to emptyList<Any>())
        val rows = buildSets(S, js("id" to "0025", "mode" to "reps", "sets" to 3, "reps" to 5, "weight" to 100, "warmupSets" to 2), js("step" to 2.5))
        val loggedSets = rows.map { r -> JsonObject(r.asObj()!! + ("done" to JsonPrimitive(true))) }
        val logged = js("entries" to listOf(js("id" to "0025", "sets" to loggedSets)))
        assertEquals(5, logged.arr("entries").first().asObj()!!.arr("sets").size)
        assertEquals(3, workSetsDone(logged))
    }

    @Test
    fun `inherits the target for timed exercises too`() {
        val timed = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-02",
                    "entries" to listOf(
                        entry(
                            LIFT,
                            listOf(js("sec" to 55, "w" to 15, "done" to true), js("sec" to 60, "w" to 17.5, "done" to true)),
                            js("mode" to "time", "sets" to 2, "sec" to 60, "weight" to 15),
                        ),
                    ),
                ),
            ),
        )
        val timedCfg = freestyleConfig(timed, js("id" to LIFT, "mode" to "time", "sets" to 3, "sec" to 45, "weight" to 0))
        assertJson(js("id" to LIFT, "mode" to "time", "sets" to 2, "sec" to 60, "weight" to 15), timedCfg)
        assertJson(
            arr(js("sec" to 55, "w" to 15, "done" to false), js("sec" to 60, "w" to 17.5, "done" to false)),
            buildSets(timed, timedCfg),
        )
    }

    @Test
    fun `keeps the supplied defaults when there is no completed matching workout`() {
        val cfg = freestyleConfig(emptyS, js("id" to LIFT, "mode" to "reps", "sets" to 3, "reps" to 10, "weight" to 50))
        assertJson(js("id" to LIFT, "mode" to "reps", "sets" to 3, "reps" to 10, "weight" to 50), cfg)
    }

    // ---------------------------------------------------------------- buildSets

    @Test
    fun `builds reps sets from the plan when there is no history`() {
        assertJson(
            arr(js("w" to 50, "r" to 8, "done" to false), js("w" to 50, "r" to 8, "done" to false), js("w" to 50, "r" to 8, "done" to false)),
            buildSets(emptyS, js("id" to LIFT, "sets" to 3, "reps" to 8, "weight" to 50)),
        )
    }

    @Test
    fun `builds timed sets, carrying the planned duration and load`() {
        assertJson(
            arr(js("sec" to 60, "w" to 20, "done" to false), js("sec" to 60, "w" to 20, "done" to false)),
            buildSets(emptyS, js("id" to LIFT, "mode" to "time", "sets" to 2, "sec" to 60, "weight" to 20)),
        )
    }

    @Test
    fun `carries last time numbers forward within the same mode`() {
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-01",
                    "entries" to listOf(entry(LIFT, listOf(js("sec" to 70, "w" to 10, "done" to true)), js("mode" to "time"))),
                ),
            ),
        )
        assertJson(
            arr(js("sec" to 70, "w" to 10, "done" to false), js("sec" to 70, "w" to 10, "done" to false)),
            buildSets(S, js("id" to LIFT, "mode" to "time", "sets" to 2, "sec" to 45, "weight" to 0)),
        )
    }

    @Test
    fun `does not seed a duration from a rep count when an exercise switches to time`() {
        val e = entry(LIFT, listOf(doneRow(60, 10)))
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(e))),
        )
        assertJson(
            arr(js("sec" to 45, "w" to 0, "done" to false)),
            buildSets(S, js("id" to LIFT, "mode" to "time", "sets" to 1, "sec" to 45, "weight" to 0)),
        )
    }

    @Test
    fun `does not seed reps from a timed set when an exercise switches back`() {
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-01",
                    "entries" to listOf(entry(LIFT, listOf(js("sec" to 70, "w" to 10, "done" to true)), js("mode" to "time"))),
                ),
            ),
        )
        assertJson(
            arr(js("w" to 40, "r" to 8, "done" to false)),
            buildSets(S, js("id" to LIFT, "mode" to "reps", "sets" to 1, "reps" to 8, "weight" to 40)),
        )
    }

    @Test
    fun `still prefers the confirmed working weight for reps sets`() {
        val S = js(
            "exWeights" to js(LIFT to js("w" to 75)),
            "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(entry(LIFT, listOf(doneRow(60, 10)))))),
        )
        assertJson(arr(js("w" to 75, "r" to 10, "done" to false)), buildSets(S, js("id" to LIFT, "sets" to 1, "reps" to 8, "weight" to 50)))
    }

    @Test
    fun `can preserve each last set weight for freestyle instead of using the working-weight hint`() {
        val S = js(
            "exWeights" to js(LIFT to js("w" to 75)),
            "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(entry(LIFT, listOf(doneRow(60, 10), doneRow(62.5, 8)))))),
        )
        assertJson(
            arr(js("w" to 60, "r" to 10, "done" to false), js("w" to 62.5, "r" to 8, "done" to false)),
            buildSets(S, js("id" to LIFT, "sets" to 2, "reps" to 8, "weight" to 50), js("preferLast" to true)),
        )
    }

    @Test
    fun `can use a deload target without carrying regular-session values into it`() {
        val S = js(
            "exWeights" to js(LIFT to js("w" to 75)),
            "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(entry(LIFT, listOf(doneRow(75, 10), doneRow(75, 9)))))),
        )
        assertJson(
            arr(js("w" to 40, "r" to 12, "done" to false), js("w" to 40, "r" to 12, "done" to false)),
            buildSets(S, js("id" to LIFT, "sets" to 2, "reps" to 12, "weight" to 40), js("useTarget" to true)),
        )
    }

    @Test
    fun `uses the current routine target instead of another routine bodyweight history`() {
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-01",
                    "routineId" to "routine-a",
                    "entries" to listOf(
                        entry(
                            BW,
                            listOf(js("w" to 0, "r" to 15, "done" to true), js("w" to 0, "r" to 15, "done" to true)),
                            js("sets" to 2, "reps" to 15, "weight" to 0, "bodyweight" to true),
                        ),
                    ),
                ),
            ),
        )
        val cfg = js("id" to BW, "sets" to 4, "reps" to 8, "weight" to 0, "bodyweight" to true, "prog" to "off")
        assertJson(
            arr(js("w" to 0, "r" to 8, "done" to false), js("w" to 0, "r" to 8, "done" to false), js("w" to 0, "r" to 8, "done" to false), js("w" to 0, "r" to 8, "done" to false)),
            buildSets(S, cfg, js("useTarget" to true)),
        )

        val reverseS = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-02",
                    "routineId" to "routine-b",
                    "entries" to listOf(
                        entry(
                            BW,
                            listOf(js("w" to 0, "r" to 8, "done" to true), js("w" to 0, "r" to 8, "done" to true), js("w" to 0, "r" to 8, "done" to true), js("w" to 0, "r" to 8, "done" to true)),
                            js("sets" to 4, "reps" to 8, "weight" to 0, "bodyweight" to true),
                        ),
                    ),
                ),
            ),
        )
        assertJson(
            arr(js("w" to 0, "r" to 15, "done" to false), js("w" to 0, "r" to 15, "done" to false)),
            buildSets(reverseS, js("id" to BW, "sets" to 2, "reps" to 15, "weight" to 0, "bodyweight" to true, "prog" to "off"), js("useTarget" to true)),
        )
    }

    @Test
    fun `preserves configured load, reps and duration when history is present`() {
        val repsS = js(
            "exWeights" to js(LIFT to js("w" to 75)),
            "workouts" to listOf(js("d" to "2026-01-01", "entries" to listOf(entry(LIFT, listOf(doneRow(75, 15)))))),
        )
        assertJson(
            arr(js("w" to 40, "r" to 8, "done" to false), js("w" to 40, "r" to 8, "done" to false)),
            buildSets(repsS, js("id" to LIFT, "sets" to 2, "reps" to 8, "weight" to 40), js("useTarget" to true)),
        )

        val timedS = js(
            "exWeights" to js(),
            "workouts" to listOf(
                js(
                    "d" to "2026-01-02",
                    "entries" to listOf(entry(LIFT, listOf(js("sec" to 90, "w" to 20, "done" to true)), js("mode" to "time"))),
                ),
            ),
        )
        assertJson(
            arr(js("sec" to 30, "w" to 5, "done" to false), js("sec" to 30, "w" to 5, "done" to false)),
            buildSets(timedS, js("id" to LIFT, "mode" to "time", "sets" to 2, "sec" to 30, "weight" to 5), js("useTarget" to true)),
        )
    }

    // ---------------------------------------------------------------- workoutVolume

    @Test
    fun `counts reps work and leaves timed sets out — there is no weight times reps for a hold`() {
        val w = js(
            "entries" to listOf(
                js("id" to LIFT, "sets" to listOf(js("w" to 60, "r" to 10, "done" to true), js("w" to 60, "r" to 10, "done" to false))),
                js("id" to LIFT, "target" to js("mode" to "time"), "sets" to listOf(js("sec" to 60, "w" to 20, "done" to true))),
            ),
        )
        assertEquals(600.0, workoutVolume(w), 0.0)
    }

    @Test
    fun `needs no per-side case — the logged reps are already both sides (issue 31)`() {
        val w = js("entries" to listOf(js("id" to LIFT, "target" to js("side" to true), "sets" to listOf(js("w" to 20, "r" to 16, "done" to true)))))
        assertEquals(320.0, workoutVolume(w), 0.0)
    }

    @Test
    fun `leaves an unloaded bodyweight set at zero volume rather than inventing a number`() {
        val w = js("entries" to listOf(js("id" to BW, "target" to js("bodyweight" to true), "sets" to listOf(js("w" to 0, "r" to 20, "done" to true)))))
        assertEquals(0.0, workoutVolume(w), 0.0)
    }

    @Test
    fun `recognizes both warm-up schemas in work-set counts`() {
        val w = js(
            "unit" to "kg",
            "entries" to listOf(
                js(
                    "id" to LIFT,
                    "unit" to "kg",
                    "sets" to listOf(
                        js("warmup" to true, "unit" to "kg", "w" to 20, "r" to 5, "done" to true),
                        js("phase" to "warmup", "unit" to "kg", "w" to 30, "r" to 5, "done" to true),
                        js("phase" to "work", "unit" to "kg", "w" to 60, "r" to 5, "done" to true),
                    ),
                ),
            ),
        )
        assertEquals(1, workSetsDone(w))
    }

    @Test
    fun `does not use a warm-up as the previous best working weight`() {
        val S = js(
            "workouts" to listOf(
                js(
                    "entries" to listOf(
                        js("id" to LIFT, "topW" to 120, "sets" to listOf(js("phase" to "warmup", "done" to true, "w" to 120), js("phase" to "work", "done" to true, "w" to 80))),
                    ),
                ),
            ),
        )
        assertEquals(80.0, bestWeightFor(S, LIFT), 0.0)
        val warmOnly = js(
            "workouts" to listOf(
                js("entries" to listOf(js("id" to LIFT, "topW" to 120, "sets" to listOf(js("phase" to "warmup", "done" to true, "w" to 120))))),
            ),
        )
        assertEquals(0.0, bestWeightFor(warmOnly, LIFT), 0.0)
    }

    @Test
    fun `uses completed non-warm-up load for timed entries`() {
        val entry = js(
            "target" to js("mode" to "time"),
            "topW" to 200,
            "sets" to listOf(
                js("phase" to "warmup", "sec" to 30, "w" to 30, "done" to true),
                js("phase" to "work", "sec" to 60, "w" to 20, "done" to true),
                js("phase" to "work", "sec" to 75, "w" to 25, "done" to true),
                js("phase" to "work", "sec" to 90, "w" to 40, "done" to false),
            ),
        )
        assertEquals(25.0, bestWeightForEntry(entry), 0.0)
    }

    @Test
    fun `does not report a repeated weighted timed hold as a new load PR (blocker 3)`() {
        val prior = js("id" to LIFT, "target" to js("mode" to "time"), "sets" to listOf(js("phase" to "work", "sec" to 60, "w" to 20, "done" to true)))
        val repeated = js("id" to LIFT, "target" to js("mode" to "time"), "sets" to listOf(js("phase" to "work", "sec" to 60, "w" to 20, "done" to true)))
        val state = js("workouts" to listOf(js("entries" to listOf(prior))))
        val repeatedWeight = maxOf(
            0.0,
            repeated.arr("sets").filter { it.asObj()?.bool("done") == true }.map { it.asObj()?.num("w") ?: 0.0 }.maxOrNull() ?: 0.0,
        )
        assertEquals(20.0, bestWeightForEntry(prior), 0.0)
        assertEquals(false, repeatedWeight > bestWeightFor(state, LIFT))
    }

    // ---------------------------------------------------------------- superset editing

    @Test
    fun `pairs adjacent entries without mutating the source and keeps the display units contiguous`() {
        val entries = arr(js("id" to "a"), js("id" to "b"), js("id" to "c"))
        val paired = pairAdjacent(entries, 1, 2, "sg-new")
        assertJson(arr(js("id" to "a"), js("id" to "b", "sg" to "sg-new"), js("id" to "c", "sg" to "sg-new")), paired)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c")), entries)
        assertJson(arr(arr(0), arr(1, 2)), supersetUnits(paired))
    }

    @Test
    fun `merges both contiguous groups when their boundary entries are paired`() {
        val entries = arr(
            js("id" to "a", "sg" to "left"),
            js("id" to "b", "sg" to "left"),
            js("id" to "c", "sg" to "right"),
            js("id" to "d", "sg" to "right"),
        )
        val merged = pairAdjacent(entries, 1, 2)
        assertEquals(listOf("left", "left", "left", "left"), merged.map { it.asObj()?.str("sg") })
        assertEquals(listOf("left", "left", "right", "right"), entries.map { it.asObj()?.str("sg") })
    }

    @Test
    fun `unpairs one entry and removes sg values left without an adjacent partner`() {
        val entries = arr(
            js("id" to "a", "sg" to "group"),
            js("id" to "b", "sg" to "group"),
            js("id" to "c", "sg" to "group"),
            js("id" to "d", "sg" to "orphan"),
        )
        val unpaired = unpairSuperset(entries, 1)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c"), js("id" to "d")), unpaired)
        assertEquals(listOf("group", "group", "group", "orphan"), entries.map { it.asObj()?.str("sg") })
    }

    @Test
    fun `rejects a non-adjacent pairing request`() {
        val entries = arr(js("id" to "a"), js("id" to "b"), js("id" to "c"))
        val e = assertThrows(IllegalArgumentException::class.java) { pairAdjacent(entries, 0, 2, "sg-invalid") }
        assertTrue(e.message?.contains("adjacent") == true)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c")), entries)
    }

    @Test
    fun `pairs adjacent entries without mutating the source and keeps the display units contiguous (repeat)`() {
        val entries = arr(js("id" to "a"), js("id" to "b"), js("id" to "c"))
        val paired = pairAdjacent(entries, 1, 2, "sg-new")
        assertJson(arr(js("id" to "a"), js("id" to "b", "sg" to "sg-new"), js("id" to "c", "sg" to "sg-new")), paired)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c")), entries)
        assertJson(arr(arr(0), arr(1, 2)), supersetUnits(paired))
    }

    @Test
    fun `merges both contiguous groups when their boundary entries are paired (repeat)`() {
        val entries = arr(
            js("id" to "a", "sg" to "left"),
            js("id" to "b", "sg" to "left"),
            js("id" to "c", "sg" to "right"),
            js("id" to "d", "sg" to "right"),
        )
        val merged = pairAdjacent(entries, 1, 2)
        assertEquals(listOf("left", "left", "left", "left"), merged.map { it.asObj()?.str("sg") })
        assertEquals(listOf("left", "left", "right", "right"), entries.map { it.asObj()?.str("sg") })
    }

    @Test
    fun `unpairs one entry and removes sg values left without an adjacent partner (repeat)`() {
        val entries = arr(
            js("id" to "a", "sg" to "group"),
            js("id" to "b", "sg" to "group"),
            js("id" to "c", "sg" to "group"),
            js("id" to "d", "sg" to "orphan"),
        )
        val unpaired = unpairSuperset(entries, 1)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c"), js("id" to "d")), unpaired)
        assertEquals(listOf("group", "group", "group", "orphan"), entries.map { it.asObj()?.str("sg") })
    }

    @Test
    fun `rejects a non-adjacent pairing request (repeat)`() {
        val entries = arr(js("id" to "a"), js("id" to "b"), js("id" to "c"))
        val e = assertThrows(IllegalArgumentException::class.java) { pairAdjacent(entries, 0, 2, "sg-invalid") }
        assertTrue(e.message?.contains("adjacent") == true)
        assertJson(arr(js("id" to "a"), js("id" to "b"), js("id" to "c")), entries)
    }

    // ---------------------------------------------------------------- session row helpers

    @Test
    fun `cascadeWeight propagates to same-flag undone rows and never rewrites done sets`() {
        val rows = arr(
            js("warmup" to true, "w" to 20, "done" to true),
            js("warmup" to true, "w" to 20, "done" to false),
            js("w" to 60, "done" to true),
            js("w" to 60, "done" to false),
            js("w" to 60, "done" to false),
        )
        val next = cascadeWeight(rows, 2, 62.5)
        assertEquals(60.0, next[2].asObj()!!.num("w")!!, 0.0)
        assertEquals(62.5, next[3].asObj()!!.num("w")!!, 0.0)
        assertEquals(62.5, next[4].asObj()!!.num("w")!!, 0.0)
        assertEquals(20.0, next[1].asObj()!!.num("w")!!, 0.0)
    }

    @Test
    fun `cascadeWeight deleting the weight removes the key from following undone rows only`() {
        val rows = arr(js("w" to 60, "done" to true), js("w" to 60, "done" to false), js("w" to 60, "done" to false))
        val next = cascadeWeight(rows, 0, null)
        assertEquals(60.0, next[0].asObj()!!.num("w")!!, 0.0)
        assertFalse(next[1].asObj()!!.containsKey("w"))
        assertFalse(next[2].asObj()!!.containsKey("w"))
    }

    @Test
    fun `insertWarmupRow inserts before the first work row, ramping toward the work weight`() {
        val rows = arr(
            js("warmup" to true, "w" to 20, "r" to 8, "done" to true),
            js("warmup" to true, "w" to 30, "r" to 8, "done" to false),
            js("w" to 60, "r" to 8, "done" to false),
        )
        val next = insertWarmupRow(rows, "reps", js("reps" to 8), 2.5)
        assertEquals(4, next.size)
        assertEquals(true, next[2].asObj()?.bool("warmup"))
        assertEquals(45.0, next[2].asObj()!!.num("w")!!, 0.0)
        assertEquals(60.0, next[3].asObj()!!.num("w")!!, 0.0)
    }

    @Test
    fun `gives the first warm-up half the working weight, not the working weight itself`() {
        val next = insertWarmupRow(arr(js("w" to 100, "r" to 5, "done" to false)), "reps", js("reps" to 5), 2.5)
        assertEquals(2, next.size)
        val first = next[0].asObj()!!
        assertEquals(50.0, first.num("w")!!, 0.0)
        assertEquals(5.0, first.num("r")!!, 0.0)
        assertEquals("warmup", first.str("phase"))
        assertEquals(true, first.bool("warmup"))
        assertEquals(false, first.bool("done"))
        assertEquals(100.0, next[1].asObj()!!.num("w")!!, 0.0)
    }

    @Test
    fun `rounds the ramp to the exercise loading step`() {
        assertEquals(45.0, insertWarmupRow(arr(js("w" to 95, "r" to 5)), "reps", js("reps" to 5), 5.0)[0].asObj()!!.num("w")!!, 0.0)
    }

    @Test
    fun `keeps bodyweight warm-ups at zero and never exceeds the work set`() {
        assertEquals(0.0, insertWarmupRow(arr(js("w" to 0, "r" to 12)), "reps", js("reps" to 12), 2.5)[0].asObj()!!.num("w")!!, 0.0)
        val at100 = insertWarmupRow(arr(js("warmup" to true, "w" to 100, "r" to 5), js("w" to 100, "r" to 5)), "reps", js("reps" to 5), 2.5)
        assertEquals(100.0, at100[1].asObj()!!.num("w")!!, 0.0)
    }

    @Test
    fun `ramps a timed hold the same way`() {
        val first = insertWarmupRow(arr(js("sec" to 45, "w" to 40, "done" to false)), "time", js("sec" to 45), 2.5)[0].asObj()!!
        assertEquals(45.0, first.num("sec")!!, 0.0)
        assertEquals(20.0, first.num("w")!!, 0.0)
        assertEquals("warmup", first.str("phase"))
    }

    @Test
    fun `removeRowAt never empties an entry below one row`() {
        assertEquals(1, removeRowAt(arr(js("w" to 60)), 0).size)
        val rows = arr(js("w" to 60), js("w" to 70))
        val next = removeRowAt(rows, 0)
        assertEquals(1, next.size)
        assertEquals(70.0, next[0].asObj()!!.num("w")!!, 0.0)
    }

    // ------------------------------------------ warm-up rows identified by phase alone

    @Test
    fun `workSetsDone does not count a phase-only warm-up`() {
        val imported = js("w" to 40, "r" to 10, "done" to true, "phase" to "warmup")
        val work = js("w" to 100, "r" to 5, "done" to true)
        assertEquals(1, workSetsDone(js("entries" to listOf(js("sets" to listOf(imported, work))))))
    }

    @Test
    fun `cascadeWeight keeps phase-only warm-ups in their own lane`() {
        val rows = arr(
            js("w" to 40, "r" to 10, "phase" to "warmup"),
            js("w" to 45, "r" to 10, "phase" to "warmup"),
            js("w" to 100, "r" to 5),
        )
        val next = cascadeWeight(rows, 0, 50)
        assertEquals(50.0, next[1].asObj()!!.num("w")!!, 0.0)
        assertEquals(100.0, next[2].asObj()!!.num("w")!!, 0.0)
    }

    // ---------------------------------------------------------------- nextTrainingDay

    @Test
    fun `finds the next day that has exercises, and returns the day itself`() {
        val S = js("weeks" to week(listOf(dayOn(4, "B", listOf(js("id" to "0002"))))))
        val nd = nextTrainingDay(S, TUE)!!
        assertEquals("2026-08-20", nd.iso)
        assertEquals(4, nd.weekday)
        assertEquals("B", nd.day.name)
    }

    @Test
    fun `looks forward only — today itself is never the answer`() {
        val S = js(
            "weeks" to arr(
                js("id" to "w1", "startIso" to MONDAY, "days" to listOf(dayOn(2, "A"))),
                js("id" to "w2", "startIso" to "2026-08-24", "days" to listOf(dayOn(2, "A"))),
            ),
        )
        val nd = nextTrainingDay(S, TUE)!!
        assertEquals("2026-08-25", nd.iso)
        assertEquals(2, nd.weekday)
    }

    @Test
    fun `wraps around the end of the week`() {
        val S = js(
            "weeks" to arr(
                js("id" to "w1", "startIso" to MONDAY, "days" to listOf(dayOn(1, "A"))),
                js("id" to "w2", "startIso" to "2026-08-24", "days" to listOf(dayOn(1, "A"))),
            ),
        )
        val nd = nextTrainingDay(S, TUE)!!
        assertEquals("2026-08-24", nd.iso)
        assertEquals(1, nd.weekday)
    }

    @Test
    fun `is null when every day is rest, or no week covers the dates`() {
        assertNull(nextTrainingDay(js("weeks" to week(emptyList())), TUE))
        assertNull(nextTrainingDay(js("weeks" to emptyList<Any>()), TUE))
    }

    @Test
    fun `skips a day with no exercises — starting it would open an empty session`() {
        val S = js("weeks" to week(listOf(dayOn(3, "A", emptyList()), dayOn(5, "B"))))
        assertEquals(5, nextTrainingDay(S, TUE)!!.weekday)
        assertEquals("B", nextTrainingDay(S, TUE)!!.day.name)
    }

    @Test
    fun `stops at a gap between weeks rather than inventing a day`() {
        val S = js("weeks" to week(listOf(dayOn(4, "B"))))
        assertEquals(4, nextTrainingDay(S, TUE)!!.weekday)
        assertNull(nextTrainingDay(S, "2026-08-20"))
    }

    // ---------------------------------------------------------------- pinnedNoteFor

    private val pinnedS = js(
        "workouts" to listOf(
            js("d" to "2026-08-01", "entries" to listOf(js("id" to "0025", "note" to "felt heavy", "notePin" to true))),
            js("d" to "2026-08-08", "entries" to listOf(js("id" to "0025", "note" to "just a diary line"))),
            js("d" to "2026-08-15", "entries" to listOf(js("id" to "0025", "note" to "go narrower", "notePin" to true))),
            js("d" to "2026-08-22", "entries" to listOf(js("id" to "0293", "note" to "other exercise", "notePin" to true))),
        ),
    )

    @Test
    fun `returns only the newest pinned note for that exercise`() {
        assertJson(js("note" to "go narrower", "d" to "2026-08-15"), pinnedNoteFor(pinnedS, "0025"))
    }

    @Test
    fun `ignores notes that were not pinned`() {
        val S = js("workouts" to listOf(js("d" to "2026-08-08", "entries" to listOf(js("id" to "0025", "note" to "diary")))))
        assertNull(pinnedNoteFor(S, "0025"))
    }

    @Test
    fun `is null for an exercise with no notes, and safe on empty state`() {
        assertNull(pinnedNoteFor(pinnedS, "9999"))
        assertNull(pinnedNoteFor(js(), "0025"))
    }

    // ---------------------------------------------------------------- exNoteFor

    @Test
    fun `reads the standing note and trims it away when blank`() {
        assertEquals("seat 4, pin 7", exNoteFor(js("exNotes" to js("0025" to " seat 4, pin 7 ")), "0025"))
        assertNull(exNoteFor(js("exNotes" to js("0025" to "   ")), "0025"))
        assertNull(exNoteFor(js(), "0025"))
    }

    // ---------------------------------------------------------------- effectiveDay

    @Test
    fun `is the day planned for a date, and null for a rest day or a date outside every week`() {
        val S = js("weeks" to week(listOf(dayOn(3, "A"))))
        assertEquals("A", effectiveDay(S, "2026-08-19")?.name)
        assertNull(effectiveDay(S, "2026-08-20"))
        assertNull(effectiveDay(js("weeks" to emptyList<Any>()), "2026-08-19"))
        assertNull(effectiveDay(js("weeks" to week(listOf(dayOn(3, "A")), "2026-08-24")), "2026-08-19"))
    }

    @Test
    fun `does not read the legacy routine fields at all`() {
        val S = js(
            "routines" to listOf(js("id" to "r1", "name" to "A", "ex" to listOf(js("id" to "1")))),
            "week" to js("3" to listOf("r1")),
            "dayPlan" to js(),
            "weeks" to emptyList<Any>(),
        )
        assertNull(effectiveDay(S, "2026-08-19"))
    }

    // ------------------------------------------ lastEntryFor / buildSets skip a noProg entry

    @Test
    fun `lastEntryFor returns the prior counting session, not a later noProg one`() {
        val S = js("workouts" to listOf(wk("2026-01-01", 60, 8), wk("2026-01-05", 30, 12, js("noProg" to true))))
        assertEquals("2026-01-01", lastEntryFor(S, LIFT)?.str("d"))
    }

    @Test
    fun `lastEntryFor skips a legacy whole-workout excludeFromProgression session`() {
        val S = js(
            "workouts" to listOf(
                wk("2026-01-01", 60, 8),
                js(
                    "d" to "2026-01-05",
                    "excludeFromProgression" to true,
                    "entries" to listOf(entry(LIFT, listOf(doneRow(30, 12)), js("sets" to 1, "reps" to 12, "weight" to 30))),
                ),
            ),
        )
        assertEquals("2026-01-01", lastEntryFor(S, LIFT)?.str("d"))
    }

    @Test
    fun `buildSets seeds opening rows from the last counting session`() {
        val S = js(
            "exWeights" to js(),
            "workouts" to listOf(wk("2026-01-01", 60, 8), wk("2026-01-05", 30, 12, js("noProg" to true))),
        )
        assertJson(arr(js("w" to 60, "r" to 8, "done" to false)), buildSets(S, js("id" to LIFT, "sets" to 1, "reps" to 5, "weight" to 50)))
    }

    @Test
    fun `buildSets with only noProg history falls back to the routine target`() {
        val S = js("exWeights" to js(), "workouts" to listOf(wk("2026-01-05", 30, 12, js("noProg" to true))))
        assertJson(arr(js("w" to 50, "r" to 5, "done" to false)), buildSets(S, js("id" to LIFT, "sets" to 1, "reps" to 5, "weight" to 50)))
    }

    @Test
    fun `freestyleConfig ignores a noProg entry`() {
        val S = js("exWeights" to js(), "workouts" to listOf(wk("2026-01-05", 30, 12, js("noProg" to true))))
        assertJson(
            js("id" to LIFT, "mode" to "reps", "sets" to 3, "reps" to 10, "weight" to 0),
            freestyleConfig(S, js("id" to LIFT, "mode" to "reps", "sets" to 3, "reps" to 10, "weight" to 0)),
        )
    }

    @Test
    fun `bestWeightFor is unchanged — a heavy noProg set still counts toward Best`() {
        val S = js("workouts" to listOf(wk("2026-01-01", 60, 8), wk("2026-01-05", 140, 3, js("noProg" to true))))
        assertEquals(140.0, bestWeightFor(S, LIFT), 0.0)
    }

    // ---------------------------------------------------------------- legacy timed sets

    @Test
    fun `reads a timed set saved without a target from the set itself`() {
        assertEquals("0:45", setLabel("0001", js("sec" to 45, "done" to true)))
        assertEquals("60×10", setLabel("0025", js("w" to 60, "r" to 10, "done" to true)))
    }
}

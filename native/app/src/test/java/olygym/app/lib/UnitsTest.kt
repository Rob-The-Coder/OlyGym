package olygym.app.lib

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.obj
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The spec of frontend/src/lib/units.test.js, one case each, in the same order. */
class UnitsTest {

    private val S: JsonObject = Json.parseToJsonElement(
        """
        {"unit":"kg","targetW":80,"bodyweight":[{"d":"2026-01-01","w":82.4,"t":1}],
         "exWeights":{"0025":{"w":80,"d":"2026-01-01"},"legacy":100},"barWeights":{"0025":20},
         "weeks":[{"id":"w","startIso":"2026-01-05","name":"","days":[{"dow":1,"name":"","ex":[
           {"id":"0025","sets":3,"reps":5,"weight":80,"inc":2.5,"warmup":[{"weight":40,"reps":8}]},
           {"id":"plank","mode":"time","sec":30,"inc":5}]}]}],
         "workouts":[{"id":"w","entries":[{"id":"0025","topW":80,"target":{"weight":80},"sets":[{"w":80,"r":5,"done":true}]}]}],
         "active":{"id":"a","entries":[{"id":"0025","sets":[{"w":82.5,"r":5,"done":false}]}]},
         "workoutView":"list"}
        """.trimIndent(),
    ) as JsonObject

    private fun numberFor(value: JsonElement?): Double = value?.asNum()!!

    // Number formatting is the one place JSON equality would lie: a parsed integer 1 prints "1"
    // where the converted body weight is 1.0. Compare numbers by value, everything else
    // structurally.
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

    private fun bodyweightWs(state: JsonObject): List<Double> =
        state.arr("bodyweight").map { it.asObj()!!.num("w")!! }

    @Test
    fun `rounds lb to a half and kg to a quarter`() {
        assertEquals(132.5, numberFor(convertWeight(JsonPrimitive(60), "kg", "lb")), 0.0)
        assertEquals(61.25, numberFor(convertWeight(JsonPrimitive(135), "lb", "kg")), 0.0)
        assertEquals(5.5, numberFor(convertWeight(JsonPrimitive(2.5), "kg", "lb")), 0.0)
    }

    @Test
    fun `leaves the value alone for the same unit, nothing, or garbage`() {
        assertEquals(60.0, numberFor(convertWeight(JsonPrimitive(60), "kg", "kg")), 0.0)
        assertNull(convertWeight(null, "kg", "lb"))
        assertEquals("", convertWeight(JsonPrimitive(""), "kg", "lb").asStr())
        assertEquals("abc", convertWeight(JsonPrimitive("abc"), "kg", "lb").asStr())
    }

    @Test
    fun `round-trips plate-loadable numbers`() {
        for (kg in listOf(20.0, 42.5, 60.0, 100.0, 142.5)) {
            val lb = convertWeight(JsonPrimitive(kg), "kg", "lb")
            assertEquals(kg, numberFor(convertWeight(lb, "lb", "kg")), 0.0)
        }
    }

    @Test
    fun `converts every stored weight and keeps everything else`() {
        val out = convertStateUnit(S, "lb").asObj()!!
        assertEquals("lb", out.str("unit"))
        assertEquals(176.4, numberFor(out["targetW"]), 0.0) // body weight keeps its 0.1, see below
        assertJson(js("d" to "2026-01-01", "w" to 181.7, "t" to 1), out.arr("bodyweight")[0])
        assertEquals(176.5, numberFor(out.obj("exWeights")!!.obj("0025")!!["w"]), 0.0)
        assertEquals(220.5, numberFor(out.obj("exWeights")!!["legacy"]), 0.0)
        assertEquals(44.0, numberFor(out.obj("barWeights")!!["0025"]), 0.0)
        val ex0 = out.arr("weeks")[0].asObj()!!.arr("days")[0].asObj()!!.arr("ex")[0].asObj()!!
        assertEquals(176.5, numberFor(ex0["weight"]), 0.0)
        assertEquals(5.5, numberFor(ex0["inc"]), 0.0)
        assertJson(js("weight" to 88, "reps" to 8), ex0.arr("warmup")[0])
        val ex1 = out.arr("weeks")[0].asObj()!!.arr("days")[0].asObj()!!.arr("ex")[1]
        assertJson(js("id" to "plank", "mode" to "time", "sec" to 30, "inc" to 5), ex1) // seconds stay seconds
        val entry = out.arr("workouts")[0].asObj()!!.arr("entries")[0].asObj()!!
        assertEquals(176.5, numberFor(entry["topW"]), 0.0)
        assertJson(js("weight" to 176.5), entry.obj("target"))
        assertJson(js("w" to 176.5, "r" to 5, "done" to true), entry.arr("sets")[0])
        val activeW = out.obj("active")!!.arr("entries")[0].asObj()!!.arr("sets")[0].asObj()!!["w"]
        assertEquals(182.0, numberFor(activeW), 0.0)
        assertEquals("list", out.str("workoutView"))
        assertEquals("kg", S.str("unit")) // the input is not mutated
        assertEquals(80.0, numberFor(S.arr("workouts")[0].asObj()!!.arr("entries")[0].asObj()!!.arr("sets")[0].asObj()!!["w"]), 0.0)
    }

    @Test
    fun `is a no-op for the unit already in use`() {
        assertSame(S, convertStateUnit(S, "kg"))
    }

    // History rows, the detail header, the month calendar and the heatmap tooltips read the
    // cached `vol` and `bw` off the saved workout rather than summing sets, so a conversion that
    // skips them shows kg totals under an lb label (QA C11).
    @Test
    fun `converts each workout's cached volume and session body weight, and the active session's (QA C11)`() {
        val session = Json.parseToJsonElement(
            """{"id":"0025","sets":[{"w":80,"r":5,"done":true},{"w":40,"r":8,"done":true,"phase":"warmup"}]}""",
        ) as JsonObject
        val active = JsonObject(S.obj("active")!! + ("bw" to JsonPrimitive(82.4)))
        val state = JsonObject(
            S + ("workouts" to JsonArray(listOf(
                js("id" to "w", "vol" to 700, "bw" to 82.4, "entries" to listOf(session)),
                js("id" to "x", "entries" to emptyList<Any>()),
            ))) + ("active" to active),
        )
        val out = convertStateUnit(state, "lb").asObj()!!
        val first = out.arr("workouts")[0].asObj()!!
        // 176.5 x 5, warm-up left out — what the converted set list adds up to.
        assertEquals(882.5, numberFor(first["vol"]), 0.0)
        assertEquals(882.5, workoutVolume(first), 0.0)
        assertEquals(181.7, numberFor(first["bw"]), 0.0)
        assertEquals(181.7, numberFor(out.obj("active")!!["bw"]), 0.0)
        // A workout that never had a cached volume does not grow one.
        assertFalse(out.arr("workouts")[1].asObj()!!.containsKey("vol"))
        assertEquals(700.0, numberFor(state.arr("workouts")[0].asObj()!!["vol"]), 0.0) // the input is not mutated
    }

    // Body weight is weighed in and edited at 0.1, not loaded on a bar: rounding it to plate steps
    // moved 22 of 24 weigh-ins on a kg -> lb -> kg round trip (QA C15). The goal weight is one too.
    @Test
    fun `keeps body weight at one-decimal resolution and brings a kg to lb to kg round trip home (QA C15)`() {
        val state = Json.parseToJsonElement(
            """
            {"unit":"kg","targetW":77,"bodyweight":[
              {"d":"2026-01-01","w":78.6,"t":0},{"d":"2026-01-02","w":82.1,"t":1},
              {"d":"2026-01-03","w":82.2,"t":2},{"d":"2026-01-04","w":81.8,"t":3},
              {"d":"2026-01-05","w":81.4,"t":4}],
             "workouts":[{"id":"w","bw":78.6,"entries":[]}],"active":{"id":"a","bw":78.6,"entries":[]}}
            """.trimIndent(),
        ) as JsonObject
        val lb = convertStateUnit(state, "lb").asObj()!!
        assertEquals(listOf(173.3, 181.0, 181.2, 180.3, 179.5), bodyweightWs(lb))
        assertEquals(169.8, numberFor(lb["targetW"]), 0.0)
        val back = convertStateUnit(lb, "kg").asObj()!!
        assertEquals(listOf(78.6, 82.1, 82.2, 81.8, 81.4), bodyweightWs(back))
        assertEquals(77.0, numberFor(back["targetW"]), 0.0)
        assertEquals(78.6, numberFor(back.arr("workouts")[0].asObj()!!["bw"]), 0.0)
        assertEquals(78.6, numberFor(back.obj("active")!!["bw"]), 0.0)
    }
}

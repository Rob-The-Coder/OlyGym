package olygym.app.lib

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.js
import olygym.app.data.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The spec of frontend/src/lib/recovery.test.js, one case each, in the same order. The
 * catalogue is the committed asset the app installs at startup, read here where a JVM test can
 * reach it, exactly as MusclesTest does.
 */

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val recoveryCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    json.decodeFromString<List<Exercise>>(file.readText())
}

private const val HOUR: Long = 60L * 60 * 1000
private const val DAY: Long = 24 * HOUR
private val V: Double = 640 * (30.0 / 38).pow(1.5)

private fun utcMillis(year: Int, month: Int, day: Int, hour: Int): Long =
    LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

private val NOW: Long = utcMillis(2026, 1, 1, 12)

// EXIDX[id], materialised the way the port does.
private fun catalogueJson(ex: Exercise): JsonObject = js(
    "id" to ex.id,
    "n" to ex.n,
    "bp" to ex.bp,
    "eq" to ex.eq,
    "tg" to ex.tg,
    "sm" to smOf(ex),
)

private fun weightsOf(ex: Exercise): Map<String, Double> =
    musclesOf(catalogueJson(ex)).mapValues { (_, value) -> value.asNum() ?: 0.0 }

// Keep fixtures tied to the shipped catalogue while making the expected stimulus explicit.
private val SINGLE: Exercise by lazy {
    EXDB.first { ex ->
        val weights = weightsOf(ex)
        ex.bp != "cardio" && weights.size == 1 && weights.values.first() == 1.0
    }
}
private val WEIGHTED: Exercise by lazy {
    EXDB.first { ex ->
        val weights = weightsOf(ex)
        ex.bp != "cardio" && weights.values.any { it == 0.4 }
    }
}
// A chest-led movement for the fatigue and strength fixtures.
private val CHEST: Exercise by lazy { EXDB.first { it.n == "bench press" } }

private val WEIGHTED_WEIGHTS: Map<String, Double> by lazy { weightsOf(WEIGHTED) }
private val SINGLE_SLUG: String by lazy { weightsOf(SINGLE).keys.first() }
private val WEIGHTED_PRIMARY_SLUG: String by lazy { WEIGHTED_WEIGHTS.entries.first { it.value == 1.0 }.key }
private val SECONDARY_SLUG: String by lazy { WEIGHTED_WEIGHTS.entries.first { it.value == 0.4 }.key }

private val LOADED: Exercise by lazy {
    EXDB.first { ex ->
        val weights = weightsOf(ex)
        ex.bp != "cardio" && ex.eq != "body weight" && weights.size == 1 && weights.values.first() == 1.0
    }
}
private val BODYWEIGHT: Exercise by lazy { EXDB.first { it.bp != "cardio" && it.eq == "body weight" } }
private val LOADED_SLUG: String by lazy { weightsOf(LOADED).keys.first() }
private val BODYWEIGHT_SLUG: String by lazy { weightsOf(BODYWEIGHT).keys.first() }

private fun workoutAt(id: String, start: Double, sets: List<JsonObject> = listOf(js("done" to true))): JsonObject =
    js(
        "d" to Instant.ofEpochMilli(start.toLong()).toString(),
        "start" to start,
        "entries" to listOf(js("id" to id, "sets" to sets)),
    )

private fun doneWorkoutAt(id: String, start: Double, count: Int = 1): JsonObject =
    workoutAt(id, start, List(count) { js("done" to true, "w" to 80.0, "r" to 8.0) })

private fun zeroFatigue(): Map<String, Double> = MUSCLES.associateWith { 0.0 }
private fun floorStrength(): Map<String, Double> = MUSCLES.associateWith { STRENGTH_FLOOR }

private fun stableFloat(value: Double): Double =
    BigDecimal(value).setScale(12, RoundingMode.HALF_UP).toDouble()

private fun referenceAfter(reference: Double, stimulus: Double): Double =
    minOf(reference, reference + (stimulus - reference) / FATIGUE_MIN_SESSIONS)

private fun expectedFatigue(sessions: List<Pair<Double, Double>>): Double {
    var reference = FATIGUE_REF_VOLUME
    var normalised = 0.0
    for ((stimulus, age) in sessions) {
        normalised += stimulus / reference * halfLifeDecay(age, FATIGUE_HALF_LIFE_MS)
        reference = referenceAfter(reference, stimulus)
    }
    return 1 - exp(-normalised)
}

// Inverse of the saturation curve: raw stimulus needed to land exactly on a target level.
private fun rawAt(target: Double): Double = -FATIGUE_REF_VOLUME * ln(1 - target)

private fun loadedWorkout(start: Double, weight: Double, count: Int = 8): JsonObject =
    workoutAt(CHEST.id, start, List(count) { js("done" to true, "w" to weight, "r" to 8.0) })

private fun stampedWorkout(
    id: String,
    start: Double,
    unit: String?,
    weight: Double,
    target: JsonObject? = null,
    bw: Double? = null,
): JsonObject = js(
    "d" to Instant.ofEpochMilli(start.toLong()).toString(),
    "start" to start,
    "unit" to unit,
    "bw" to bw,
    "entries" to listOf(js("id" to id, "target" to target, "sets" to listOf(js("done" to true, "w" to weight, "r" to 8.0)))),
)

private fun entryAt(workout: JsonObject): JsonObject = workout.arr("entries").first().asObj()!!
class RecoveryTest {

    @Before
    fun installCatalogue() {
        Catalogue.install(recoveryCatalogue)
    }

    @Test
    fun `exports the pinned windows, half-lives, floor, and state labels`() {
        assertEquals(2000.0, FATIGUE_REF_VOLUME, 0.0)
        assertEquals(30.0 * DAY, FATIGUE_SCAN_MS, 0.0)
        assertEquals(36.0 * HOUR, FATIGUE_HALF_LIFE_MS, 0.0)
        assertEquals(75.0, BODYWEIGHT_REF_LOAD, 0.0)
        assertEquals(50.0, CARDIO_TONNAGE_PER_MIN, 0.0)
        assertEquals(14.0 * DAY, STRENGTH_FULL_MS, 0.0)
        assertEquals(28.0 * DAY, STRENGTH_HALF_LIFE_MS, 0.0)
        assertEquals(0.5, STRENGTH_FLOOR, 0.0)
        assertEquals(
            mapOf("READY" to "ready", "RECOVERING" to "recovering", "FATIGUED" to "fatigued"),
            mapOf(
                "READY" to FATIGUE_STATES.READY,
                "RECOVERING" to FATIGUE_STATES.RECOVERING,
                "FATIGUED" to FATIGUE_STATES.FATIGUED,
            ),
        )
        assertEquals(0.5, halfLifeDecay(FATIGUE_HALF_LIFE_MS, FATIGUE_HALF_LIFE_MS), 0.0)
    }

    @Test
    fun `returns every muscle, ready-floor defaults, and hook defaults for empty history`() {
        val none = JsonArray(emptyList())
        val fatigue = fatigueOf(none, NOW)
        val strength = strengthOf(none, NOW)

        assertEquals(MUSCLES, fatigue.keys.toList())
        assertEquals(zeroFatigue(), fatigue)
        assertEquals(MUSCLES.map { "ready" }, fatigue.values.map { fatigueStateOf(it) })
        assertEquals(MUSCLES, strength.keys.toList())
        assertEquals(floorStrength(), strength)
        assertEquals(emptyList<String>(), fatiguedMuscles(none, NOW))
        assertEquals(MUSCLES, detrainedMuscles(none, NOW))
    }

    @Test
    fun `applies one completed set to each of the exercise muscle weights`() {
        val workouts = JsonArray(listOf(doneWorkoutAt(WEIGHTED.id, NOW.toDouble())))
        val fatigue = fatigueOf(workouts, NOW)
        val strength = strengthOf(workouts, NOW)

        for (slug in MUSCLES) {
            val weight = WEIGHTED_WEIGHTS[slug] ?: 0.0
            assertEquals(1 - exp(-V * weight / FATIGUE_REF_VOLUME), fatigue[slug]!!, 1e-9)
            assertEquals(if (weight != 0.0) 1.0 else STRENGTH_FLOOR, strength[slug]!!, 0.0)
        }
        // one set (80 x 8) never crosses the fatigued threshold on the saturating curve
        assertEquals(emptyList<String>(), fatiguedMuscles(workouts, NOW))

        // pure volume: one high-rep set at medium weight registers real tonnage (weighted by
        // its intensity - its own Epley estimate with the 12-rep cap is 50 x 40/30 = 70 kg)
        val highRep = JsonArray(listOf(
            workoutAt(SINGLE.id, NOW.toDouble(), listOf(js("done" to true, "w" to 50.0, "r" to 50.0))),
        ))
        val weighted = 2500.0 * (50.0 / (50.0 * (1 + 12.0 / 30))).pow(1.5)
        assertEquals(1 - exp(-weighted / FATIGUE_REF_VOLUME), fatigueOf(highRep, NOW)[SINGLE_SLUG]!!, 1e-9)
        assertTrue(fatigueOf(highRep, NOW)[SINGLE_SLUG]!! > 0.5)
    }

    @Test
    fun `uses a deleted exercise snapshot for the same bounded primary and secondary fatigue`() {
        val resolved = doneWorkoutAt(WEIGHTED.id, NOW.toDouble())
        val deletedEntry = JsonObject(
            entryAt(resolved) +
                ("id" to JsonPrimitive("deleted-weighted-exercise")) +
                ("muscleSnapshot" to exerciseMuscleSnapshot(catalogueJson(WEIGHTED))),
        )
        val deleted = JsonObject(resolved + ("entries" to JsonArray(listOf(deletedEntry))))

        assertEquals(fatigueOf(JsonArray(listOf(deleted)), NOW), fatigueOf(JsonArray(listOf(resolved)), NOW))
        assertTrue(fatigueOf(JsonArray(listOf(deleted)), NOW)[WEIGHTED_PRIMARY_SLUG]!! > 0)
        assertTrue(fatigueOf(JsonArray(listOf(deleted)), NOW)[SECONDARY_SLUG]!! > 0)
    }

    @Test
    fun `uses a deleted exercise snapshot for strength without changing decay or floor semantics`() {
        val start = NOW - 15.0 * DAY
        val resolved = doneWorkoutAt(WEIGHTED.id, start)
        val deletedEntry = JsonObject(
            entryAt(resolved) +
                ("id" to JsonPrimitive("deleted-weighted-exercise")) +
                ("muscleSnapshot" to exerciseMuscleSnapshot(catalogueJson(WEIGHTED))),
        )
        val deleted = JsonObject(resolved + ("entries" to JsonArray(listOf(deletedEntry))))
        val untouchedSlug = MUSCLES.first { (WEIGHTED_WEIGHTS[it] ?: 0.0) == 0.0 }
        val strength = strengthOf(JsonArray(listOf(deleted)), NOW)

        assertEquals(strengthOf(JsonArray(listOf(resolved)), NOW), strength)
        assertEquals(0.5.pow(1.0 / 28), strength[WEIGHTED_PRIMARY_SLUG]!!, 1e-9)
        assertEquals(0.5.pow(1.0 / 28), strength[SECONDARY_SLUG]!!, 1e-9)
        assertEquals(STRENGTH_FLOOR, strength[untouchedSlug]!!, 0.0)
    }

    @Test
    fun `raises starting fatigue with volume, never pins, and fades without a cliff`() {
        fun at0(count: Int) = fatigueOf(JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW.toDouble(), count))), NOW)[SINGLE_SLUG]!!
        assertEquals(1 - exp(-V / FATIGUE_REF_VOLUME), at0(1), 1e-9)
        assertEquals(1 - exp(-5 * V / FATIGUE_REF_VOLUME), at0(5), 1e-9)
        assertEquals(1 - exp(-12 * V / FATIGUE_REF_VOLUME), at0(12), 1e-9)
        assertTrue(at0(12) > at0(5))
        assertTrue(at0(5) > at0(1))
        assertTrue(at0(12) < 1)
        // one half-life later the gradient is still visible at any volume
        val later = fatigueOf(JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW - FATIGUE_HALF_LIFE_MS, 12))), NOW)[SINGLE_SLUG]!!
        assertEquals(1 - exp(-6 * V / FATIGUE_REF_VOLUME), later, 1e-9)
        assertTrue(later < at0(12))
        // no cliff: 72h keeps decaying instead of snapping to zero
        val old = fatigueOf(JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW - 72.0 * HOUR))), NOW)[SINGLE_SLUG]!!
        assertTrue(old > 0)
        assertTrue(old < 0.25)
    }

    @Test
    fun `decays each weighted stimulus exactly at 36 hours and by sqrt-half at 18 hours`() {
        for (age in listOf(FATIGUE_HALF_LIFE_MS, FATIGUE_HALF_LIFE_MS / 2)) {
            val fatigue = fatigueOf(JsonArray(listOf(doneWorkoutAt(WEIGHTED.id, NOW - age))), NOW)
            val expectedDecay = 0.5.pow(age / FATIGUE_HALF_LIFE_MS)
            for ((slug, weight) in WEIGHTED_WEIGHTS) {
                assertEquals(1 - exp(-V * weight * expectedDecay / FATIGUE_REF_VOLUME), fatigue[slug]!!, 1e-9)
            }
            if (age == FATIGUE_HALF_LIFE_MS) {
                assertEquals(1 - exp(-V * 0.5 / FATIGUE_REF_VOLUME), fatigue[WEIGHTED_PRIMARY_SLUG]!!, 1e-9)
            }
            if (age == FATIGUE_HALF_LIFE_MS / 2) {
                assertEquals(1 - exp(-V * 0.5.pow(0.5) / FATIGUE_REF_VOLUME), fatigue[WEIGHTED_PRIMARY_SLUG]!!, 1e-9)
            }
        }
    }

    @Test
    fun `fades a 72-hour-old set below the ready threshold instead of hard-cutting`() {
        val workouts = JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW - 72.0 * HOUR)))
        val value = fatigueOf(workouts, NOW)[SINGLE_SLUG]!!
        assertTrue(value > 0)
        assertTrue(value < 0.25)
        assertEquals(FATIGUE_STATES.READY, fatigueStateOf(value))
        assertEquals(1.0, strengthOf(workouts, NOW)[SINGLE_SLUG]!!, 0.0)
    }

    @Test
    fun `ignores sets whose done flag is false for both axes`() {
        val workouts = JsonArray(listOf(workoutAt(WEIGHTED.id, NOW.toDouble(), listOf(js("done" to false)))))
        assertEquals(zeroFatigue(), fatigueOf(workouts, NOW))
        assertEquals(floorStrength(), strengthOf(workouts, NOW))
        assertEquals(emptyList<String>(), fatiguedMuscles(workouts, NOW))
        assertEquals(MUSCLES, detrainedMuscles(workouts, NOW))
    }

    @Test
    fun `classifies exactly point-25 as recovering and point-2499 as ready`() {
        val weight = WEIGHTED_WEIGHTS[SECONDARY_SLUG]!!
        val sets = 4
        fun valueAt(target: Double): Double {
            val age = FATIGUE_HALF_LIFE_MS * log2(sets * V * weight / rawAt(target))
            val value = fatigueOf(JsonArray(listOf(doneWorkoutAt(WEIGHTED.id, NOW - age, sets))), NOW)[SECONDARY_SLUG]!!
            assertEquals(target, value, 1e-9)
            return stableFloat(value)
        }

        assertEquals(FATIGUE_STATES.RECOVERING, fatigueStateOf(valueAt(0.25)))
        assertEquals(FATIGUE_STATES.READY, fatigueStateOf(valueAt(0.2499)))
    }

    @Test
    fun `classifies exactly point-5 as recovering, point-5001 as fatigued, and hooks only fatigued muscles`() {
        val sets = 4
        val atHalf = JsonArray(listOf(doneWorkoutAt(
            SINGLE.id,
            NOW - FATIGUE_HALF_LIFE_MS * log2(sets * V / rawAt(0.4999)),
            sets,
        )))
        val aboveHalf = JsonArray(listOf(doneWorkoutAt(
            SINGLE.id,
            NOW - FATIGUE_HALF_LIFE_MS * log2(sets * V / rawAt(0.5001)),
            sets,
        )))
        val half = fatigueOf(atHalf, NOW)[SINGLE_SLUG]!!
        val above = fatigueOf(aboveHalf, NOW)[SINGLE_SLUG]!!

        assertEquals(0.4999, half, 1e-9)
        assertEquals(FATIGUE_STATES.RECOVERING, fatigueStateOf(stableFloat(half)))
        assertEquals(emptyList<String>(), fatiguedMuscles(atHalf, NOW))
        assertEquals(0.5001, above, 1e-9)
        assertEquals(FATIGUE_STATES.FATIGUED, fatigueStateOf(stableFloat(above)))
        assertEquals(listOf(SINGLE_SLUG), fatiguedMuscles(aboveHalf, NOW))
    }

    @Test
    fun `scores each session against only the reference left by strictly earlier sessions`() {
        val old = doneWorkoutAt(SINGLE.id, NOW - DAY.toDouble())
        val today = doneWorkoutAt(SINGLE.id, NOW.toDouble())
        val earlierReference = referenceAfter(FATIGUE_REF_VOLUME, V)
        val expected = 1 - exp(-(
            V / FATIGUE_REF_VOLUME * halfLifeDecay(DAY.toDouble(), FATIGUE_HALF_LIFE_MS) +
                V / earlierReference
            ))

        assertEquals(expected, fatigueOf(JsonArray(listOf(old, today)), NOW)[SINGLE_SLUG]!!, 1e-9)
        assertEquals(1 - exp(-V / FATIGUE_REF_VOLUME), fatigueOf(JsonArray(listOf(today)), NOW)[SINGLE_SLUG]!!, 1e-9)
    }

    @Test
    fun `keeps the 8x100x8 ten-day reproduction non-increasing as sessions leave the scan`() {
        val base = utcMillis(2026, 1, 31, 12)
        val workouts = JsonArray(listOf(-20, -10, 0).map { days -> loadedWorkout(base + days * DAY.toDouble(), 100.0) })
        val observed = (0..31 * 24).map { hour -> fatigueOf(workouts, base + hour * HOUR)["chest"]!! }

        assertTrue(observed[0] > 0.5)
        assertEquals(0.0, observed.last(), 0.0)
        for (index in 1 until observed.size) {
            assertTrue(observed[index] <= observed[index - 1] + Math.ulp(1.0))
        }
    }

    @Test
    fun `ignores imports older than the scan, including their heavier 1RM data`() {
        val today = loadedWorkout(NOW.toDouble(), 100.0, 5)
        val baseline = fatigueOf(JsonArray(listOf(today)), NOW)["chest"]!!
        val heavyImport = loadedWorkout(NOW - 90.0 * DAY, 140.0, 10)
        val highVolumeImport = loadedWorkout(NOW - 90.0 * DAY, 100.0, 20)

        assertEquals(baseline, fatigueOf(JsonArray(listOf(heavyImport, today)), NOW)["chest"]!!, 0.0)
        assertEquals(baseline, fatigueOf(JsonArray(listOf(highVolumeImport, today)), NOW)["chest"]!!, 0.0)
    }

    @Test
    fun `never increases fatigue when any one workout is deleted`() {
        val workouts = listOf(
            loadedWorkout(NOW - 40.0 * DAY, 100.0, 15),
            loadedWorkout(NOW - 3.0 * DAY, 100.0, 8),
            loadedWorkout(NOW - 2.0 * DAY, 100.0, 8),
            loadedWorkout(NOW - DAY.toDouble(), 60.0, 4),
            loadedWorkout(NOW.toDouble(), 120.0, 10),
        )
        val before = fatigueOf(JsonArray(workouts), NOW)

        workouts.forEachIndexed { deletedIndex, _ ->
            val after = fatigueOf(JsonArray(workouts.filterIndexed { index, _ -> index != deletedIndex }), NOW)
            for (slug in MUSCLES) assertTrue(after[slug]!! <= before[slug]!! + Math.ulp(1.0))
        }
    }

    @Test
    fun `rates a lighter current week below repeating the established load`() {
        val prior = listOf(-21, -14, -7).map { days -> loadedWorkout(NOW + days * DAY.toDouble(), 100.0) }
        val lighter = fatigueOf(JsonArray(prior + loadedWorkout(NOW.toDouble(), 50.0)), NOW)["chest"]!!
        val repeated = fatigueOf(JsonArray(prior + loadedWorkout(NOW.toDouble(), 100.0)), NOW)["chest"]!!
        assertTrue(lighter < repeated)
    }

    @Test
    fun `uses the last registered bodyweight for bodyweight exercises`() {
        val bwEx = EXDB.first { it.eq == "body weight" && it.bp != "cardio" }
        val slug = weightsOf(bwEx).keys.first()
        val workout = js(
            "d" to Instant.ofEpochMilli(NOW).toString(),
            "start" to NOW.toDouble(),
            "entries" to listOf(js("id" to bwEx.id, "sets" to listOf(js("done" to true, "r" to 10.0)))),
        )
        val at80 = fatigueOf(JsonArray(listOf(workout)), NOW, bodyweightKg = 80.0)[slug]!!
        val at90 = fatigueOf(JsonArray(listOf(workout)), NOW, bodyweightKg = 90.0)[slug]!!
        assertEquals(1 - exp(-800 / FATIGUE_REF_VOLUME), at80, 1e-9)
        assertEquals(1 - exp(-900 / FATIGUE_REF_VOLUME), at90, 1e-9)
        assertTrue(at90 > at80)
    }

    @Test
    fun `stays at full retention through 14 days and decays from 15 days by the 28-day half-life`() {
        fun strengthAt(age: Double) = strengthOf(JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW - age))), NOW)[SINGLE_SLUG]!!
        assertEquals(1.0, strengthAt(STRENGTH_FULL_MS), 0.0)
        assertEquals(0.5.pow(1.0 / 28), strengthAt(15.0 * DAY), 1e-9)
    }

    @Test
    fun `clamps the 42-day half-life point and later 56-day value at the half floor`() {
        fun strengthAt(age: Double) = strengthOf(JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW - age))), NOW)[SINGLE_SLUG]!!
        assertEquals(0.5, strengthAt(42.0 * DAY), 0.0)
        assertEquals(STRENGTH_FLOOR, strengthAt(56.0 * DAY), 0.0)
    }

    @Test
    fun `resets retained strength when a later completed session retrains the muscle`() {
        val workouts = JsonArray(listOf(
            doneWorkoutAt(SINGLE.id, NOW - 20.0 * DAY),
            doneWorkoutAt(SINGLE.id, NOW.toDouble()),
        ))
        assertEquals(1.0, strengthOf(workouts, NOW)[SINGLE_SLUG]!!, 0.0)
    }

    @Test
    fun `matches the saturated sum of independently decayed stimuli in chronological order`() {
        val ages = listOf(64.0 * HOUR, 40.0 * HOUR)
        val workouts = ages.map { age -> doneWorkoutAt(SINGLE.id, NOW - age) }
        val raw = ages.fold(0.0) { sum, age -> sum + V * 0.5.pow(age / FATIGUE_HALF_LIFE_MS) }
        val expected = expectedFatigue(listOf(V to ages[0], V to ages[1]))

        assertTrue(raw < FATIGUE_REF_VOLUME)
        assertEquals(expected, fatigueOf(JsonArray(workouts), NOW)[SINGLE_SLUG]!!, 1e-9)
        assertEquals(expected, fatigueOf(JsonArray(workouts.reversed()), NOW)[SINGLE_SLUG]!!, 1e-9)
    }

    @Test
    fun `saturates without pinning and returns identical results without mutating inputs or sharing state`() {
        val saturated = JsonArray(listOf(doneWorkoutAt(SINGLE.id, NOW.toDouble(), 2)))
        assertEquals(1 - exp(-2 * V / FATIGUE_REF_VOLUME), fatigueOf(saturated, NOW)[SINGLE_SLUG]!!, 1e-9)
        assertTrue(fatigueOf(saturated, NOW)[SINGLE_SLUG]!! < 1)

        val workouts = JsonArray(listOf(
            doneWorkoutAt(SINGLE.id, NOW - 64.0 * HOUR),
            doneWorkoutAt(SINGLE.id, NOW - 40.0 * HOUR),
        ))
        val before = workouts.toString()
        val firstFatigue = fatigueOf(workouts, NOW)
        val firstStrength = strengthOf(workouts, NOW)
        val secondFatigue = fatigueOf(workouts, NOW)
        val secondStrength = strengthOf(workouts, NOW)

        assertEquals(firstFatigue, secondFatigue)
        assertEquals(firstStrength, secondStrength)
        assertEquals(before, workouts.toString())
    }

    @Test
    fun `a warm-up set does not reset strength but still adds fatigue volume`() {
        val now = utcMillis(2026, 8, 1, 12)
        val oldWork = js(
            "id" to "w1", "d" to "2026-07-10", "start" to (now - 20 * DAY).toDouble(), "unit" to "kg",
            "entries" to listOf(js("id" to CHEST.id, "sets" to listOf(js("done" to true, "w" to 80.0, "r" to 8.0)))),
        )
        val warm = js(
            "id" to "w2", "d" to "2026-08-01", "start" to (now - 3600000).toDouble(), "unit" to "kg",
            "entries" to listOf(js("id" to CHEST.id, "sets" to listOf(js("done" to true, "warmup" to true, "w" to 20.0, "r" to 8.0)))),
        )
        val workouts = JsonArray(listOf(oldWork, warm))
        val strength = strengthOf(workouts, now)
        // the strength edge is 20 days old: the fresh warm-up must NOT be the latest training event
        assertTrue(strength["chest"]!! < 1)
        // but the warm-up still contributes to the fatigue stimulus (real mechanical work)
        val fatigue = fatigueOf(workouts, now)
        assertTrue(fatigue["chest"]!! > 0)
    }

    @Test
    fun `gives kg and physically equivalent stamped-pound histories the same fatigue`() {
        val kg = stampedWorkout(LOADED.id, NOW.toDouble(), "kg", 80.0)
        val lb = stampedWorkout(LOADED.id, NOW.toDouble(), "lb", 176.3696)

        assertEquals(
            fatigueOf(JsonArray(listOf(kg)), NOW, unit = "kg")[LOADED_SLUG]!!,
            fatigueOf(JsonArray(listOf(lb)), NOW, unit = "kg")[LOADED_SLUG]!!,
            1e-6,
        )
    }

    @Test
    fun `normalizes mixed stamped units before computing the causal reference volume`() {
        val starts = listOf(NOW - 3.0 * DAY, NOW - 2.0 * DAY, NOW - DAY.toDouble(), NOW.toDouble())
        val kg = starts.map { start -> stampedWorkout(LOADED.id, start, "kg", 80.0) }
        val mixed = starts.mapIndexed { index, start ->
            stampedWorkout(LOADED.id, start, if (index % 2 == 1) "lb" else "kg", if (index % 2 == 1) 176.3696 else 80.0)
        }

        assertEquals(
            fatigueOf(JsonArray(kg), NOW, unit = "kg")[LOADED_SLUG]!!,
            fatigueOf(JsonArray(mixed), NOW, unit = "kg")[LOADED_SLUG]!!,
            1e-6,
        )
    }

    @Test
    fun `treats an unstamped legacy history as being in the profile unit`() {
        val kg = stampedWorkout(LOADED.id, NOW.toDouble(), "kg", 80.0)
        val kgEntry = entryAt(kg)
        val legacyLb = JsonObject(
            kg.without("unit") +
                ("entries" to JsonArray(listOf(
                    JsonObject(kgEntry + ("sets" to JsonArray(listOf(js("done" to true, "w" to 176.3696, "r" to 8.0))))),
                ))),
        )

        assertEquals(
            fatigueOf(JsonArray(listOf(kg)), NOW, unit = "kg")[LOADED_SLUG]!!,
            fatigueOf(JsonArray(listOf(legacyLb)), NOW, unit = "lb")[LOADED_SLUG]!!,
            1e-6,
        )
    }

    @Test
    fun `uses configured bodyweight for a non-catalogue bodyweight target`() {
        val workout = stampedWorkout(LOADED.id, NOW.toDouble(), "kg", 0.0, target = js("bodyweight" to true), bw = 80.0)

        assertTrue(fatigueOf(JsonArray(listOf(workout)), NOW, unit = "kg")[LOADED_SLUG]!! > 0)
        assertEquals(1.0, strengthOf(JsonArray(listOf(workout)), NOW, unit = "kg")[LOADED_SLUG]!!, 0.0)
    }

    @Test
    fun `adds external load to bodyweight instead of replacing the body mass`() {
        val unloaded = stampedWorkout(BODYWEIGHT.id, NOW.toDouble(), "kg", 0.0, bw = 80.0)
        val unloadedEntry = entryAt(unloaded)
        val added = JsonObject(
            unloaded + ("entries" to JsonArray(listOf(
                JsonObject(unloadedEntry + ("sets" to JsonArray(listOf(js("done" to true, "w" to 10.0, "r" to 8.0))))),
            ))),
        )

        assertTrue(
            fatigueOf(JsonArray(listOf(added)), NOW, unit = "kg")[BODYWEIGHT_SLUG]!! >
                fatigueOf(JsonArray(listOf(unloaded)), NOW, unit = "kg")[BODYWEIGHT_SLUG]!!,
        )
    }

    @Test
    fun `lets explicitly configured custom bodyweight work reset strength`() {
        val id = "recovery-custom-bodyweight"
        registerCustom(listOf(Exercise(id = id, n = "Custom bodyweight", bp = "chest", tg = "chest", eq = "custom", sm = emptyList())))
        try {
            val workout = stampedWorkout(id, NOW.toDouble(), "kg", 0.0, target = js("bodyweight" to true), bw = 80.0)
            assertTrue(fatigueOf(JsonArray(listOf(workout)), NOW, unit = "kg")["chest"]!! > 0)
            assertEquals(1.0, strengthOf(JsonArray(listOf(workout)), NOW, unit = "kg")["chest"]!!, 0.0)
        } finally {
            registerCustom(emptyList())
        }
    }

    @Test
    fun `treats a future-dated workout as its own timestamp, never amplified`() {
        // A CSV import with a bad timezone can date a workout 10 days in the future. The final
        // decay clamps the age to zero instead of exponentiating, so the session counts exactly
        // like the same workout dated now.
        val future = doneWorkoutAt(SINGLE.id, NOW + 10.0 * DAY, 5)
        val nowWorkout = doneWorkoutAt(SINGLE.id, NOW.toDouble(), 5)
        val futureValue = fatigueOf(JsonArray(listOf(future)), NOW)[SINGLE_SLUG]!!
        val nowValue = fatigueOf(JsonArray(listOf(nowWorkout)), NOW)[SINGLE_SLUG]!!

        assertEquals(nowValue, futureValue, 1e-9)
        assertTrue(futureValue < 1)
    }

    @Test
    fun `counts a default custom zero-load ring push-up for fatigue and load-blind strength`() {
        val id = "recovery-ring-push-up"
        registerCustom(listOf(Exercise(id = id, n = "Ring push-up", bp = "chest", tg = "chest", eq = "custom", sm = emptyList())))
        try {
            val workout = workoutAt(id, NOW.toDouble(), listOf(
                js("done" to true, "w" to 0.0, "r" to 20.0),
                js("done" to true, "w" to 0.0, "r" to 20.0),
            ))
            assertEquals(1 - exp(-2.0 / 3), fatigueOf(JsonArray(listOf(workout)), NOW)["chest"]!!, 1e-9)
            assertEquals(1.0, strengthOf(JsonArray(listOf(workout)), NOW)["chest"]!!, 0.0)
        } finally {
            registerCustom(emptyList())
        }
    }
}



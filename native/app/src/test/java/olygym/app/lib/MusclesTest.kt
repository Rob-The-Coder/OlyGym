package olygym.app.lib

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asNum
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.str
import olygym.app.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The spec of frontend/src/lib/muscles.test.js and frontend/src/lib/muscle-order.test.js, one
 * case each, in the same order. The catalogue is the committed asset the app installs at
 * startup, read here where a JVM test can reach it.
 */
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val musclesCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is ${File(".").absolutePath})")
    json.decodeFromString<List<Exercise>>(file.readText())
}

/** JS `new Date(iso).getTime()` for a local date-time, as the tests use it. */
private fun localMillis(iso: String): Double =
    LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().toDouble()

class MusclesTest {

    // EXIDX['wl806'] et al., materialised the way the port does.
    private fun catalogueById(id: String): JsonObject {
        val ex = Catalogue[id]!!
        return js("id" to ex.id, "n" to ex.n, "bp" to ex.bp, "tg" to ex.tg, "sm" to smOf(ex))
    }

    @Before
    fun installCatalogue() {
        Catalogue.install(musclesCatalogue)
    }

    @Test
    fun `normalizes legacy primary-secondary fields and removes duplicate groups`() {
        val ex = js("tg" to "pectorals", "mg" to "triceps", "sm" to listOf("triceps", "chest"))
        assertEquals(listOf("chest", "triceps"), muscleGroupsOf(ex))
        assertEquals(js("chest" to 1.0, "triceps" to 0.4), musclesOf(ex))
    }

    @Test
    fun `uses explicit multi-group metadata when present and supports legacy single groups`() {
        assertEquals(
            listOf("chest", "triceps"),
            muscleGroupsOf(js("muscleGroups" to listOf("chest", "pectorals", "triceps"))),
        )
        assertEquals(listOf("chest"), muscleGroupsOf(js("tg" to "chest")))
        assertEquals("Chest", MUSCLE_NAME["chest"])
    }

    @Test
    fun `falls back to the legacy body-part map when an optional multi-group field is empty`() {
        val ex = js("bp" to "back", "muscleGroups" to emptyList<String>())
        assertEquals(listOf("upper-back", "lower-back"), muscleGroupsOf(ex))
        assertTrue(matchesMuscleGroups(ex, listOf("lower-back").toJson()))
    }

    @Test
    fun `falls back consistently when explicit groups contain only unknown names`() {
        val ex = js("bp" to "back", "muscleGroups" to listOf("not-a-drawable-name"))
        assertEquals(listOf("upper-back", "lower-back"), muscleGroupsOf(ex))
        assertEquals(js("upper-back" to 0.75, "lower-back" to 0.25), musclesOf(ex))
    }

    @Test
    fun `matches an exercise when any requested muscle group matches`() {
        val ex = js("muscleGroups" to listOf("chest", "triceps"))
        assertTrue(matchesMuscleGroups(ex, listOf("hamstring", "triceps").toJson()))
        assertFalse(matchesMuscleGroups(ex, listOf("hamstring", "gluteal").toJson()))
        assertTrue(matchesMuscleGroups(ex, emptyList<String>().toJson()))
    }

    @Test
    fun `counts one effective set per unique group instead of double counting duplicates`() {
        assertEquals(
            js("chest" to 2.0, "triceps" to 0.8),
            loadOf(
                listOf(
                    js("id" to "inline", "ex" to js("tg" to "chest", "sm" to listOf("chest", "triceps")), "sets" to 2),
                ).toJson(),
            ),
        )
    }

    @Test
    fun `uses multi-muscle metadata carried by a history entry when its catalogue id is unavailable`() {
        assertEquals(
            js("chest" to 1.0, "triceps" to 1.0),
            loadOfWorkouts(
                listOf(
                    js(
                        "entries" to listOf(
                            js("id" to "deleted-custom", "muscleGroups" to listOf("chest", "chest", "triceps"), "sets" to listOf(js("done" to true))),
                        ),
                    ),
                ).toJson(),
            ),
        )
    }

    @Test
    fun `maps a bench press to chest, triceps and deltoids`() {
        assertEquals(
            js("chest" to 1.0, "triceps" to 0.4, "deltoids" to 0.4),
            musclesOf(catalogueById("wl806")),
        )
    }

    @Test
    fun `maps a squat to quads, glutes and hamstrings`() {
        assertEquals(
            js("quadriceps" to 1.0, "gluteal" to 0.4, "hamstring" to 0.4),
            musclesOf(catalogueById("wl77")),
        )
    }

    @Test
    fun `maps common row variations to the upper back and biceps`() {
        for (id in listOf("wl805", "wl807", "wl171", "wl813")) {
            assertEquals(id, js("upper-back" to 1.0, "biceps" to 0.4), musclesOf(catalogueById(id)))
        }
    }

    @Test
    fun `gives every primary full weight and secondaries supporting weight`() {
        val ex = js(
            "bp" to "chest", "tg" to "abs", "mg" to "triceps", "sm" to listOf("lower back"),
            "primaries" to listOf("chest", "triceps", "chest"), "secondaries" to listOf("deltoids", "triceps"),
        )
        assertEquals(listOf("chest", "triceps", "deltoids"), muscleGroupsOf(ex))
        assertEquals(js("chest" to 1.0, "triceps" to 1.0, "deltoids" to 0.4), musclesOf(ex))
    }

    @Test
    fun `keeps the body-part fallback when primaries are absent or explicitly empty`() {
        val expected = js("upper-back" to 0.75, "lower-back" to 0.25)
        assertEquals(expected, musclesOf(js("bp" to "back")))
        assertEquals(expected, musclesOf(js("bp" to "back", "primaries" to emptyList<String>())))
    }

    @Test
    fun `keeps legacy metadata when a new array field is present but empty`() {
        val snapshot = exerciseMuscleSnapshot(js("bp" to "chest", "tg" to "abs", "primaries" to emptyList<String>()))
        assertEquals(listOf("abs"), snapshot.arr("muscleGroups").map { it.asStr() })
    }

    @Test
    fun `provides a conservative Full body fallback for legacy custom exercises`() {
        assertEquals(
            listOf("chest", "upper-back", "gluteal", "quadriceps", "hamstring", "abs"),
            muscleGroupsOf(js("bp" to "full body")),
        )
        assertEquals(
            js(
                "chest" to 0.2, "upper-back" to 0.2, "gluteal" to 0.2,
                "quadriceps" to 0.2, "hamstring" to 0.1, "abs" to 0.1,
            ),
            musclesOf(js("bp" to "full body")),
        )
    }

    @Test
    fun `preserves explicit primary and secondary arrays in history snapshots`() {
        val snapshot = exerciseMuscleSnapshot(
            js("n" to "Deadlift", "bp" to "full body", "primaries" to listOf("gluteal", "lower-back"), "secondaries" to listOf("hamstring")),
        )
        assertEquals("Deadlift", snapshot.str("n"))
        assertEquals("full body", snapshot.str("bp"))
        assertEquals(listOf("gluteal", "lower-back"), snapshot.arr("primaries").map { it.asStr() })
        assertEquals(listOf("hamstring"), snapshot.arr("secondaries").map { it.asStr() })
        assertEquals(listOf("gluteal", "lower-back", "hamstring"), snapshot.arr("muscleGroups").map { it.asStr() })
    }

    @Test
    fun `excludes warm-up sets from the by-sets-worked map`() {
        val w = js(
            "id" to "w1", "d" to "2026-08-01", "start" to 1785000000000L, "unit" to "kg",
            "entries" to listOf(
                js(
                    "id" to "wl806",
                    "sets" to listOf(
                        js("done" to true, "phase" to "warmup", "w" to 20, "r" to 8),
                        js("done" to true, "phase" to "work", "w" to 60, "r" to 8),
                    ),
                ),
            ),
        )
        val load = loadOfWorkouts(listOf(w).toJson(), null)
        assertEquals(1.0, load["chest"]!!.asNum()!!, 0.0)
    }

    // A custom exercise that was deleted from the catalogue survives in history only as the
    // muscleSnapshot finish-workout wrote. Reading it back is what keeps those sessions in the
    // body map and in Stats instead of silently contributing nothing.
    private val snapshotEntry = js(
        "id" to "gone-custom-1",
        "sets" to listOf(js("w" to 60, "r" to 8, "done" to true)),
        "muscleSnapshot" to js("n" to "Deleted custom", "bp" to "chest", "muscleGroups" to listOf("chest"), "muscleWeights" to js("chest" to 1)),
    )

    @Test
    fun `reads muscle load back out of the snapshot`() {
        assertEquals(
            js("chest" to 1.0),
            loadOfWorkouts(listOf(js("d" to "2026-08-01", "entries" to listOf(snapshotEntry))).toJson()),
        )
    }

    @Test
    fun `reports the snapshot groups as explicit metadata`() {
        assertTrue(hasExplicitMuscleMetadata(snapshotEntry))
        assertEquals(listOf("chest"), muscleGroupsOf(snapshotEntry))
    }

    @Test
    fun `still prefers the entry's own metadata when it has any`() {
        val withOwn = JsonObject(snapshotEntry + ("tg" to JsonPrimitive("quadriceps")))
        assertEquals(listOf("quadriceps"), muscleGroupsOf(withOwn))
    }

    // muscles.js counts completed work, so its warm-up boundary has to agree with the one the
    // session runtime uses — including the legacy spellings phaseForSet normalises.
    @Test
    fun `excludes every phase spelling the workout model treats as a warm-up`() {
        for (spelling in listOf("warmup", "warm-up", "warm_up", "Warmup", " warmup ")) {
            val entries = listOf(
                js("id" to "wl806", "sets" to listOf(js("w" to 100, "r" to 5, "done" to true, "phase" to spelling))),
            )
            assertEquals(
                spelling,
                js(),
                loadOfWorkouts(listOf(js("d" to "2026-08-01", "entries" to entries)).toJson()),
            )
        }
    }

    @Test
    fun `preserves calendar-week, strict trailing-day, and all-history semantics`() {
        val now = localMillis("2026-08-27T12:00:00")
        val workouts = listOf(
            js("id" to "monday", "d" to "2026-08-24", "start" to localMillis("2026-08-24T12:00:00")),
            js("id" to "sunday", "d" to "2026-08-23", "start" to localMillis("2026-08-23T12:00:00")),
            js("id" to "boundary", "d" to "2026-07-28", "start" to now - 30 * 86400000.0),
            js("id" to "inside", "d" to "2026-07-29", "start" to now - 29 * 86400000.0),
        ).toJson()
        assertEquals(
            listOf("monday"),
            muscleBalanceWindow(workouts, 7, now, "2026-08-27").mapNotNull { it.asObj()?.str("id") },
        )
        assertEquals(
            listOf("monday", "sunday", "inside"),
            muscleBalanceWindow(workouts, 30, now, "2026-08-27").mapNotNull { it.asObj()?.str("id") },
        )
        assertEquals(workouts, muscleBalanceWindow(workouts, 0, now, "2026-08-27"))
    }

    @Test
    fun `uses relative levels and canonical order to break load ties`() {
        val load = js("chest" to 2, "deltoids" to 2, "biceps" to 1)
        assertEquals(listOf("deltoids", "chest", "biceps"), rankOf(load).arr("worked").map { it.asStr() })
        val levels = levelsOf(load)
        assertEquals(4.0, levels["deltoids"]!!.asNum()!!, 0.0)
        assertEquals(4.0, levels["chest"]!!.asNum()!!, 0.0)
        assertEquals(2.0, levels["biceps"]!!.asNum()!!, 0.0)
        assertEquals(0.0, levels["abs"]!!.asNum()!!, 0.0)
    }

    @Test
    fun `keeps catalogue precedence and deleted-custom snapshot weights`() {
        val known = js("id" to "wl806", "muscleGroups" to listOf("quadriceps"), "sets" to listOf(js("done" to true)))
        assertEquals(
            js("chest" to 1.0, "triceps" to 0.4, "deltoids" to 0.4),
            loadOfWorkouts(listOf(js("entries" to listOf(known))).toJson()),
        )
        val deleted = js("id" to "deleted", "muscleSnapshot" to js("muscleWeights" to js("chest" to 1)), "sets" to listOf(js("done" to true)))
        assertEquals(js("chest" to 1.0), loadOfWorkouts(listOf(js("entries" to listOf(deleted))).toJson()))
    }

    // MUSCLE_NAME values are the i18n keys — a value no pack defines renders English in every
    // language. The Library list showed "Cardiovascular system" untranslated for every cardio
    // exercise (QA copy): the packs only ever had the dataset's own lowercase spelling.
    @Test
    fun `every display name is a key in every locale pack`() {
        val dir = listOf(File("src/main/assets/i18n"), File("app/src/main/assets/i18n")).firstOrNull { it.isDirectory }
            ?: error("i18n asset directory not found (working directory is ${File(".").absolutePath})")
        val packs = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name } ?: emptyList()
        assertEquals(1, packs.size)   // only the Italian pack ships; English is the built-in fallback
        val pack = json.decodeFromString<Map<String, String>>(packs[0].readText())
        val missing = MUSCLE_NAME.values.filter { name -> !pack.containsKey(name) }
        assertEquals(emptyList<String>(), missing)
    }

    // A custom exercise used to store its muscles in the order the chips were tapped, so the same
    // exercise read differently depending on how it was built (Discord, luckapow).
    @Test
    fun `is the same list whatever order it was picked in`() {
        assertEquals(listOf("deltoids", "chest", "triceps"), inMuscleOrder(listOf("triceps", "chest", "deltoids")))
        assertEquals(listOf("deltoids", "chest", "triceps"), inMuscleOrder(listOf("chest", "deltoids", "triceps")))
    }

    @Test
    fun `follows MUSCLES, leaves unknown names at the end and the input alone`() {
        val picked = mutableListOf("calves", "made-up", "abs")
        assertEquals(listOf("abs", "calves", "made-up"), inMuscleOrder(picked))
        assertEquals(listOf("calves", "made-up", "abs"), picked)
        assertEquals(MUSCLES, inMuscleOrder(MUSCLES.reversed()))
        assertEquals(emptyList<String>(), inMuscleOrder(null))
    }
}

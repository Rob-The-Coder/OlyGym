package olygym.app.lib

import olygym.app.data.Exercise
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The spec of frontend/src/lib/exercises.test.js, one case each, in the same order. */
class ExercisesTest {

    // The catalogue entries the spec's matchExercise block uses. benchPress/wl806 is the id the
    // localized-name cases drive the name-pack seam through.
    private val benchPress = Exercise(
        id = "wl806",
        n = "barbell bench press",
        bp = "chest",
        tg = "pectorals",
        eq = "barbell",
        sm = listOf("triceps", "deltoids"),
        desc = "Classic chest exercise using a barbell on a flat bench.",
    )

    private val lateralRaise = Exercise(
        id = "wl825",
        n = "dumbbell lateral raise",
        bp = "shoulders",
        tg = "delts",
        eq = "dumbbell",
        sm = listOf("traps"),
        desc = "Shoulder isolation movement.",
    )

    // The vitest file starts in English and only the language cases change it. JUnit may run the
    // cases in any order, so each one gets that same starting language.
    @Before
    fun resetLang() {
        I18nCore.setLangState("en")
    }

    @After
    fun restoreLang() {
        I18nCore.setLangState("en")
    }

    @Test
    fun `normalizeStr handles null, undefined and empty strings`() {
        assertEquals("", normalizeStr(null))
        assertEquals("", normalizeStr(null))
        assertEquals("", normalizeStr(""))
    }

    @Test
    fun `normalizeStr lowercases text and removes diacritics and accents`() {
        assertEquals("elevacao lateral", normalizeStr("Elevação Lateral"))
        assertEquals("supino inclinado com halteres", normalizeStr("SUPINO INCLINADO COM HALTERES"))
        assertEquals("triceps & panturrilhas", normalizeStr("Tríceps & Panturrilhas"))
        assertEquals("quadriceps / gluteos", normalizeStr("Quadríceps / Glúteos"))
    }

    @Test
    fun `matchExercise returns true for empty or whitespace-only query`() {
        assertTrue(matchExercise(benchPress, ""))
        assertTrue(matchExercise(benchPress, "   "))
        assertTrue(matchExercise(benchPress, null))
    }

    @Test
    fun `matchExercise matches exact and partial words in exercise name regardless of case`() {
        assertTrue(matchExercise(benchPress, "bench"))
        assertTrue(matchExercise(benchPress, "BENCH"))
        assertTrue(matchExercise(benchPress, "barbell"))
        assertTrue(matchExercise(benchPress, "press"))
        assertFalse(matchExercise(benchPress, "squat"))
    }

    @Test
    fun `matchExercise matches multiple tokens in ANY order (not just sequential)`() {
        assertTrue(matchExercise(benchPress, "bench barbell"))
        assertTrue(matchExercise(benchPress, "press bench barbell"))
        assertTrue(matchExercise(benchPress, "barbell press chest"))
        assertFalse(matchExercise(benchPress, "bench squat"))
    }

    @Test
    fun `matchExercise matches target muscle, equipment, secondary muscles and description`() {
        assertTrue(matchExercise(benchPress, "pectorals"))
        assertTrue(matchExercise(benchPress, "triceps barbell"))
        assertTrue(matchExercise(benchPress, "flat bench"))
        assertTrue(matchExercise(lateralRaise, "dumbbell shoulder"))
    }

    @Test
    fun `matchExercise matches accent-insensitively`() {
        val customEx = Exercise(
            id = "custom-1",
            n = "Elevação de Panturrilha",
            bp = "lower legs",
            tg = "calves",
            eq = "body weight",
            desc = "Exercício para panturrilhas em pé.",
        )
        assertTrue(matchExercise(customEx, "elevacao"))
        assertTrue(matchExercise(customEx, "elevação"))
        assertTrue(matchExercise(customEx, "panturrilha elevacao"))
        assertTrue(matchExercise(customEx, "ELEVACAO PE"))
    }

    @Test
    fun `matchExercise matches translated UI terms when a language is active`() {
        I18nCore.setLangState(
            "it",
            mapOf(
                "chest" to "peito",
                "barbell" to "barra",
                "dumbbell" to "halteres",
                "shoulders" to "ombros",
            ),
        )
        assertTrue(matchExercise(benchPress, "peito"))
        assertTrue(matchExercise(benchPress, "barra"))
        assertTrue(matchExercise(benchPress, "peito barra bench"))
        assertTrue(matchExercise(lateralRaise, "halteres ombros"))
    }

    @Test
    fun `matchExercise matches the localized exercise name as well as the English one`() {
        I18nCore.setLangState("it", emptyMap(), null, mapOf("wl806" to "supino reto com barra"))
        assertTrue(matchExercise(benchPress, "supino"))
        assertTrue(matchExercise(benchPress, "supino barra"))
        assertTrue(matchExercise(benchPress, "bench press"))
        assertFalse(matchExercise(lateralRaise, "supino"))
    }

    @Test
    fun `matchExercise rebuilds the cached haystack when the language changes`() {
        I18nCore.setLangState("it", emptyMap(), null, mapOf("wl806" to "supino reto com barra"))
        assertTrue(matchExercise(benchPress, "supino"))
        I18nCore.setLangState("en", null, null, null)
        assertFalse(matchExercise(benchPress, "supino"))
        assertTrue(matchExercise(benchPress, "bench"))
    }
}

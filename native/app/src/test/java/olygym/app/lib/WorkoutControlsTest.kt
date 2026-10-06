package olygym.app.lib

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which optional controls the session screen shows, from frontend/src/lib/workout-controls.js.
 *
 * The JS spreads the saved object over the lean default and the call sites then test truthiness, so
 * the three cases that matter are an absent key (the default), a stored null (off) and a partial
 * object (the rest of the defaults).
 */
class WorkoutControlsTest {

    @Test
    fun `with no stored choices the lean default is what the screen shows`() {
        val wc = workoutControls(null)
        assertTrue(wc.steppers)
        assertFalse(wc.pairButtons)
        assertFalse(wc.exerciseButtons)
        assertEquals(wc, workoutControls(js()))
        assertEquals(wc, workoutControls(js("unit" to "kg")))
    }

    @Test
    fun `a partial object takes the defaults it does not mention`() {
        val wc = workoutControls(js("wc" to js("exerciseButtons" to true)))
        assertTrue(wc.steppers)
        assertFalse(wc.pairButtons)
        assertTrue(wc.exerciseButtons)
    }

    @Test
    fun `all three flags can be turned the other way`() {
        val wc = workoutControls(
            js("wc" to js("steppers" to false, "pairButtons" to true, "exerciseButtons" to true))
        )
        assertFalse(wc.steppers)
        assertTrue(wc.pairButtons)
        assertTrue(wc.exerciseButtons)
    }

    @Test
    fun `a stored null reads as off, because the spread keeps it`() {
        // js() drops nulls, so the stored null has to be built explicitly. In the web,
        // { ...WC_DEFAULT, ...{ steppers: null } } is steppers: null, and the call site tests it.
        val storedNull = JsonObject(mapOf("wc" to JsonObject(mapOf("steppers" to JsonNull))))
        assertFalse(workoutControls(storedNull).steppers)
        assertFalse(workoutControls(js("wc" to js("steppers" to false))).steppers)
    }

    @Test
    fun `an unknown key is carried without changing anything`() {
        val wc = workoutControls(js("wc" to js("laterPhase" to true, "steppers" to false)))
        assertFalse(wc.steppers)
        assertFalse(wc.pairButtons)
    }

    @Test
    fun `a wc that is not an object falls back to the defaults`() {
        assertEquals(workoutControls(null), workoutControls(js("wc" to JsonNull)))
        assertEquals(workoutControls(null), workoutControls(js("wc" to "nonsense")))
    }
}

package olygym.app.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings screen's option lists, which Settings.jsx builds inline. Resetting is the one of
 * these with teeth: it must leave nothing of the old profile behind.
 */
class SettingsTest {

    @Test
    fun `a reset leaves an empty state`() {
        assertEquals(0, resetState().size)
    }

    @Test
    fun `the two shipped languages, and the one the instructions ship in`() {
        assertEquals(listOf("en", "it"), LANGUAGES.keys.toList())
        assertEquals("English", LANGUAGES["en"])
        assertEquals(listOf("en"), INSTR_LANGS)
    }

    @Test
    fun `the option lists are the ones the screen offers`() {
        assertEquals(listOf(0, 60, 90, 120, 150, 180), REST_OPTIONS)
        assertEquals(listOf("cards", "list", "compact"), WORKOUT_VIEWS)
        assertEquals(listOf("dark", "light", "system"), THEMES)
        assertEquals(listOf("none", "rir", "rpe"), EFFORT_MODES)
        assertEquals(listOf("full", "off"), GIF_SIZES)
        assertEquals(listOf(1, 2), WEIGHT_DECIMALS)
        assertEquals(listOf(MONDAY, SUNDAY), WEEK_STARTS)
        assertEquals(8, ACCENTS.size)
        assertTrue(ACCENTS.containsKey(DEFAULT_ACCENT))
    }
}

package olygym.app.lib

import olygym.app.data.Exercise
import olygym.app.data.js
import olygym.app.data.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four readings the exercise picker is built on: favourites, the equipment profile, one pass of
 * the library filters, and how often an exercise is already in use.
 */
class PickerHelpersTest {

    private val catalogue = listOf(
        Exercise(id = "wl58", n = "snatch", bp = "olympic", eq = "barbell", tg = "trapezius"),
        Exercise(id = "wl77", n = "back squat", bp = "legs", eq = "barbell", tg = "quadriceps"),
        Exercise(id = "wl39", n = "pull-up", bp = "back", eq = "body weight", tg = "lats"),
        Exercise(id = "wl101", n = "romanian deadlift", bp = "legs", eq = "barbell", tg = "hamstrings"),
    )

    /* ------------------------------------------------------------ favourites -- */

    @Test
    fun `favourites are read as a flat id list and tolerate no list at all`() {
        assertTrue(favIds(null).isEmpty())
        assertTrue(favIds(js("unit" to "kg")).isEmpty())
        assertEquals(listOf("wl58", "wl77"), favIds(js("favEx" to listOf("wl58", "wl77"))))
        assertTrue(isFav(js("favEx" to listOf("wl58")), "wl58"))
        assertFalse(isFav(js("favEx" to listOf("wl58")), "wl77"))
    }

    @Test
    fun `flipping a favourite adds it, and flipping it again takes it away`() {
        val empty = js("unit" to "kg")
        assertEquals(listOf("wl58"), toggledFavs(empty, "wl58"))
        val one = js("favEx" to listOf("wl58"))
        assertEquals(listOf("wl58", "wl77"), toggledFavs(one, "wl77"))
        assertTrue(toggledFavs(one, "wl58").isEmpty())
    }

    @Test
    fun `favourites sort to the front and both halves keep their order`() {
        val S = js("favEx" to listOf("wl39"))
        assertEquals(
            listOf("wl39", "wl58", "wl77", "wl101"),
            sortFavouritesFirst(catalogue, S).map { it.id },
        )
        // Nothing favourited is not a re-ordering.
        assertEquals(catalogue.map { it.id }, sortFavouritesFirst(catalogue, js()).map { it.id })
    }

    /* ------------------------------------------------------------- equipment -- */

    private val withHomeProfile = js(
        "equipFilterOn" to true,
        "activeEquipId" to "eq1",
        "equipProfiles" to listOf(js("id" to "eq1", "name" to "Home", "equipment" to listOf("barbell"))),
    )

    @Test
    fun `with filtering off everything is available`() {
        assertNull(activeProfile(js("equipProfiles" to listOf(js("id" to "eq1")))))
        assertTrue(exAvailable(js(), catalogue.first()))
        assertTrue(exAvailable(null, catalogue.first()))
    }

    @Test
    fun `the active profile hides what it does not list, body weight excepted`() {
        val active = activeProfile(withHomeProfile)
        assertEquals("Home", active?.str("name"))
        assertTrue(exAvailable(withHomeProfile, catalogue[0]))
        // A movement with no equipment at all is never gated.
        assertTrue(exAvailable(withHomeProfile, Exercise(id = "x", n = "burpee")))
        // Body weight is available to every profile.
        assertTrue(exAvailable(withHomeProfile, catalogue[2]))
        val bodyOnly = js(
            "equipFilterOn" to true,
            "activeEquipId" to "eq1",
            "equipProfiles" to listOf(js("id" to "eq1", "equipment" to listOf("body weight"))),
        )
        assertFalse(exAvailable(bodyOnly, catalogue[0]))
        assertTrue(exAvailable(bodyOnly, catalogue[2]))
    }

    /* --------------------------------------------------------- library filter -- */

    @Test
    fun `the pass filters by part, then search, then availability`() {
        val legs = libraryResults(catalogue, bp = "legs")
        assertEquals(listOf("wl77", "wl101"), legs.list.map { it.id })

        val searched = libraryResults(catalogue, q = "squat")
        assertEquals(listOf("wl77"), searched.list.map { it.id })

        val available = libraryResults(catalogue, available = { it.eq == "barbell" })
        assertEquals(listOf("wl58", "wl77", "wl101"), available.list.map { it.id })
        // The equipment options come from what survived the earlier filters.
        assertEquals(listOf("barbell"), available.eqOpts)
    }

    @Test
    fun `an equipment choice nothing survives is dropped, and reported as dropped`() {
        val r = libraryResults(catalogue, q = "squat", eq = "dumbbell")
        assertEquals(listOf("wl77"), r.list.map { it.id })
        assertEquals("", r.eq)
        // The chip the caller renders is the one that is actually on.
        assertEquals(listOf("barbell"), r.eqOpts)
        assertEquals("barbell", libraryResults(catalogue, eq = "barbell").eq)
    }

    @Test
    fun `body parts and equipment are derived from the catalogue, in its order`() {
        assertEquals(listOf("olympic", "legs", "back"), bodyParts(catalogue))
        // Most common first, then alphabetically: three bars, one body weight.
        assertEquals(listOf("barbell", "body weight"), allEquipment(catalogue))
    }

    /* ----------------------------------------------------------------- usage -- */

    private val profile = js(
        "weeks" to listOf(
            js(
                "id" to "w1",
                "days" to listOf(
                    js("dow" to 1, "ex" to listOf(js("id" to "wl58", "sets" to 5), js("id" to "wl77"))),
                    js("dow" to 3, "ex" to listOf(js("id" to "wl58"))),
                ),
            ),
        ),
        "workouts" to listOf(
            js("id" to "k1", "entries" to listOf(js("id" to "wl58"), js("id" to "wl39"))),
            js("id" to "k2", "entries" to listOf(js("id" to "wl77"))),
        ),
    )

    @Test
    fun `usage counts the plan and the log together`() {
        assertEquals(listOf("wl58", "wl77", "wl58"), plannedEx(profile).mapNotNull { it.str("id") })
        assertEquals(mapOf("wl58" to 3, "wl77" to 2, "wl39" to 1), usageMap(profile))
        assertTrue(usageMap(null).isEmpty())
        assertTrue(usageMap(js("weeks" to "nonsense", "workouts" to 7.0)).isEmpty())
    }

    /* ------------------------------------------------------ progression step -- */

    @Test
    fun `the step is what the config asks for, or the dataset's own default`() {
        assertEquals(1.25, progressionStepOf(js("inc" to 1.25), "reps", "wl77"), 1e-9)
        // With no catalogue installed no id is heavy, so the dataset default is the small step —
        // the heavy-lift rule itself is ProgressionTest's business.
        assertEquals(2.5, progressionStepOf(js(), "reps", "wl39"), 1e-9)
        assertEquals(5.0, progressionStepOf(js(), "time", "wl39"), 1e-9)
        assertEquals(0.0, progressionStepOf(js("inc" to 0.0), "reps", "wl39"), 1e-9)
        // An absent step is the dataset's, not zero.
        assertEquals(2.5, progressionStepOf(js("inc" to null), "reps", "wl101"), 1e-9)
    }

    @Test
    fun `a rule needs a positive step, except the one that has no progression`() {
        assertTrue(progressionStepIsValid(1.25, "linear"))
        assertTrue(progressionStepIsValid(0.0, "off"))
        assertTrue(progressionStepIsValid(null, "off"))
        assertFalse(progressionStepIsValid(0.0, "linear"))
        assertFalse(progressionStepIsValid(-1.0, "linear"))
        assertFalse(progressionStepIsValid(Double.NaN, "linear"))
    }
}

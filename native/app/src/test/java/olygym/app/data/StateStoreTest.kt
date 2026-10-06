package olygym.app.data

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The read path end to end: a file on disk, the parse, the migration and the state the screen gets.
 * It is what a device check would exercise, without a device — the store takes a File rather than a
 * Context for exactly this reason.
 */
private val NOW = LocalDate.parse("2026-10-06")   // Tuesday; its week starts Monday 2026-10-05

private const val MODERN = """
{
  "unit": "kg", "weekStart": 1, "lang": "it", "theme": "light", "accent": "sky", "wdec": 2,
  "weeks": [
    { "id": "w1", "startIso": "2026-10-05", "name": "", "days": [
      { "dow": 1, "name": "Snatch Day", "ex": [{ "id": "wl58", "sets": 5, "reps": 3 }] },
      { "dow": 3, "name": "Clean & Jerk Day", "ex": [] }
    ] }
  ],
  "workouts": [{ "d": "2026-10-01" }]
}
"""

private const val LEGACY = """
{
  "weekStart": 1,
  "routines": [
    { "id": "a", "name": "Push", "ex": [{ "id": "wl77", "sets": 5, "reps": 5 }] },
    { "id": "b", "name": "Giorno 1 · 23-29 marzo", "ex": [{ "id": "wl58", "sets": 3, "reps": 3 }] },
    { "id": "c", "name": "Freestyle", "ex": [{ "id": "wl79", "sets": 3, "reps": 5 }] }
  ],
  "week": { "1": ["a"], "4": "a" }
}
"""

class StateStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        // The module directory is the test's working directory; build/ is ignored and disposable.
        dir = File("build/state-store-test").apply { deleteRecursively(); mkdirs() }
    }

    private fun store(raw: String?): StateStore {
        val file = File(dir, StateStore.FILE)
        if (raw != null) file.writeText(raw)
        return StateStore(file) { lang -> if (lang == "it") mapOf("Plan" to "Piano") else emptyMap() }
    }

    @Test
    fun `a device with no file at all says so, and does not crash`() {
        val s = store(null)
        s.load(NOW)
        val state = s.state.value
        assertTrue(state is PlanState.Empty)
        assertEquals(false, (state as PlanState.Empty).fileExists)
        assertEquals(File(dir, StateStore.FILE).absolutePath, state.path)
    }

    @Test
    fun `a profile with weeks is read whole, with its settings`() {
        val s = store(MODERN)
        s.load(NOW)
        val state = s.state.value as PlanState.Loaded
        assertEquals("it", state.settings.lang)
        assertEquals("light", state.settings.theme)
        assertEquals("sky", state.settings.accent)
        assertEquals(2, state.settings.wdec)
        assertEquals(1, state.weeks.size)
        assertEquals(2, state.weeks[0].days.size)
        assertEquals("Clean & Jerk Day", state.weeks[0].days[1].name)
        // The raw text is kept so a later phase can write the file back without dropping keys this
        // model does not have — "workouts" above is one of them.
        assertTrue(state.raw.contains("workouts"))
    }

    @Test
    fun `reading a profile applies its language and its decimal setting`() {
        val s = store(MODERN)
        s.load(NOW)
        assertEquals("it", olygym.app.lib.I18nCore.lang)
        assertEquals("Piano", olygym.app.lib.I18nCore.t("Plan"))
    }

    @Test
    fun `a profile from before the dated-weeks model has its plan migrated on the way in`() {
        val s = store(LEGACY)
        s.load(NOW)
        val state = s.state.value as PlanState.Loaded
        // The schedule's own week, the coach's labelled week, then the leftover one — the same
        // three groups migrate-weeks.js produces.
        assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12"), state.weeks.map { it.startIso })
        // Sorted by date, so the coach's labelled week comes first, then the schedule's own week.
        assertEquals("23-29 marzo", state.weeks[0].name)
        assertEquals("Giorno 1 · 23-29 marzo", state.weeks[0].days[0].name)
        assertEquals("Push", state.weeks[1].days[0].name)
        // The old shape stored one id as a bare string there as well as an array here; both read.
        assertEquals(listOf(1, 4), state.weeks[1].days.map { it.dow })
        assertEquals("Freestyle", state.weeks[2].days[0].name)
    }

    @Test
    fun `a profile that parses but holds no plan is empty, and not the same thing as no file`() {
        val s = store("""{"weeks": [], "workouts": [{"d": "2026-10-01"}]}""")
        s.load(NOW)
        val state = s.state.value
        assertTrue(state is PlanState.Empty)
        assertEquals(true, (state as PlanState.Empty).fileExists)
    }

    @Test
    fun `a truncated file fails with a reason rather than an empty list`() {
        val s = store("""{"weeks": [{"id": "w1", "startIso":""")
        s.load(NOW)
        val state = s.state.value
        assertTrue(state is PlanState.Failed)
        assertTrue((state as PlanState.Failed).reason.isNotBlank())
        assertEquals(File(dir, StateStore.FILE).absolutePath, state.path)
    }

    @Test
    fun `keys this phase does not model do not stop it loading`() {
        val s = store("""{"weeks": [], "somethingNew": {"nested": [1, 2, 3]}, "effort": null}""")
        s.load(NOW)
        assertEquals(true, (s.state.value as PlanState.Empty).fileExists)
    }
}

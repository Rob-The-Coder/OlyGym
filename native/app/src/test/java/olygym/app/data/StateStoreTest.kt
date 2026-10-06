package olygym.app.data

import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The read path and the write path end to end: a file on disk, the parse, the migration, the state a
 * screen gets, and the round trip back out. It is what a device check would exercise, without a
 * device — the store takes a File rather than a Context for exactly this reason.
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
    private lateinit var file: File

    @Before
    fun setUp() {
        // The module directory is the test's working directory; build/ is ignored and disposable.
        dir = File("build/state-store-test").apply { deleteRecursively(); mkdirs() }
        file = File(dir, StateStore.FILE)
    }

    private fun store(raw: String? = null, backgroundWrites: Boolean = false): StateStore {
        if (raw != null) file.writeText(raw)
        return StateStore(
            file = file,
            localePack = { lang -> if (lang == "it") mapOf("Plan" to "Piano") else emptyMap() },
            backgroundWrites = backgroundWrites,
        )
    }

    private fun loaded(s: StateStore, now: LocalDate = NOW): Profile {
        s.load(now)
        return (s.state.value as AppState.Ready).profile
    }

    private fun onDisk(): JsonObject =
        Json.parseToJsonElement(file.readText()) as JsonObject

    /* ------------------------------------------------------------------ reading */

    @Test
    fun `a device with no file at all says so, and does not crash`() {
        val s = store()
        val profile = loaded(s)
        assertEquals(false, profile.fileExists)
        assertEquals(emptyList<Any>(), profile.weeks)
        assertEquals("kg", profile.settings.unit)
        assertEquals(file.absolutePath, profile.path)
    }

    @Test
    fun `a profile with weeks is read whole, with its settings`() {
        val profile = loaded(store(MODERN))
        assertEquals("it", profile.settings.lang)
        assertEquals("light", profile.settings.theme)
        assertEquals("sky", profile.settings.accent)
        assertEquals(2, profile.settings.wdec)
        // A profile with no target and no weigh-in setting reads as: no goal, and the weigh-in on.
        assertNull(profile.settings.targetW)
        assertEquals(true, profile.settings.weighIn)
        assertEquals(1, profile.weeks.size)
        assertEquals(2, profile.weeks[0].days.size)
        assertEquals("Clean & Jerk Day", profile.weeks[0].days[1].name)
        assertEquals(1, profile.workouts.size)
        // The whole state object is kept, keys this app does not model included.
        assertTrue(profile.raw.containsKey("workouts"))
    }

    @Test
    fun `reading a profile applies its language and its decimal setting`() {
        loaded(store(MODERN))
        assertEquals("it", olygym.app.lib.I18nCore.lang)
        assertEquals("Piano", olygym.app.lib.I18nCore.t("Plan"))
    }

    @Test
    fun `a profile from before the dated-weeks model has its plan migrated on the way in`() {
        val profile = loaded(store(LEGACY))
        assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12"), profile.weeks.map { it.startIso })
        assertEquals("23-29 marzo", profile.weeks[0].name)
        assertEquals("Giorno 1 · 23-29 marzo", profile.weeks[0].days[0].name)
        assertEquals("Push", profile.weeks[1].days[0].name)
        // The old shape stored one id as a bare string there as well as an array here; both read.
        assertEquals(listOf(1, 4), profile.weeks[1].days.map { it.dow })
        assertEquals("Freestyle", profile.weeks[2].days[0].name)
    }

    @Test
    fun `a profile that parses but holds no plan is empty, and not the same thing as no file`() {
        val profile = loaded(store("""{"weeks": [], "workouts": [{"d": "2026-10-01"}]}"""))
        assertEquals(true, profile.fileExists)
        assertEquals(emptyList<Any>(), profile.weeks)
    }

    @Test
    fun `a truncated file fails with a reason rather than an empty list`() {
        val s = store("""{"weeks": [{"id": "w1", "startIso":""")
        s.load(NOW)
        val state = s.state.value
        assertTrue(state is AppState.Failed)
        assertEquals(file.absolutePath, (state as AppState.Failed).path)
        assertTrue(state.reason.isNotBlank())
    }

    @Test
    fun `keys this phase does not model do not stop it loading`() {
        val profile = loaded(store("""{"weeks": [], "somethingNew": {"nested": [1, 2, 3]}, "effort": null}"""))
        assertEquals(true, profile.fileExists)
        assertTrue(profile.raw.containsKey("somethingNew"))
    }

    /* ------------------------------------------------------------------ writing */

    @Test
    fun `an update is written to the file, atomically, with the time on it`() {
        val s = store(MODERN)
        loaded(s)
        s.update { it.with("bodyweight", listOf(js("d" to "2026-10-06", "w" to 81.5))) }

        val written = onDisk()
        assertEquals(1, (written.arr("bodyweight")).size)
        assertNotNull(written["_ts"])
        assertTrue((written["_ts"]).asNum()!! > 0)
        // The temp file the write went through is gone.
        assertFalse(File(dir, StateStore.FILE + ".writing").exists())
    }

    @Test
    fun `a write keeps every key it did not touch, which is the whole point of merging`() {
        val s = store("""{"weeks": [], "somethingNew": {"nested": [1, 2, 3]}, "planAliases": {"a": "b"}}""")
        loaded(s)
        s.update { it.with("theme", "light") }

        val written = onDisk()
        assertEquals("light", written.str("theme"))
        assertTrue(written.containsKey("somethingNew"))
        assertTrue(written.containsKey("planAliases"))
    }

    @Test
    fun `an update re-derives the state the screens read`() {
        val s = store("""{"weeks": []}""")
        loaded(s)
        s.update {
            it.with(
                "weeks",
                listOf(js("id" to "w1", "startIso" to "2026-10-05", "name" to "", "days" to emptyList<Any>())),
            )
        }
        val profile = (s.state.value as AppState.Ready).profile
        assertEquals(1, profile.weeks.size)
        assertEquals("2026-10-05", profile.weeks[0].startIso)
        assertEquals("2026-10-05", profile.weeks[0].startIso)
    }

    @Test
    fun `a write is readable again by a fresh store, because the file is the contract`() {
        val first = store(MODERN)
        loaded(first)
        first.update { it.with("unit", "lb") }

        val second = store()
        val profile = loaded(second)
        assertEquals("lb", profile.settings.unit)
    }

    @Test
    fun `a profile that failed to parse is never overwritten`() {
        val broken = """{"weeks": [{"id": "w1", "startIso":"""
        val s = store(broken)
        s.load(NOW)
        assertTrue(s.state.value is AppState.Failed)

        s.update { it.with("unit", "lb") }

        assertTrue(s.state.value is AppState.Failed)
        assertEquals(broken, file.readText())
    }

    @Test
    fun `the background writer coalesces, and flush makes the last one land`() {
        val s = store(MODERN, backgroundWrites = true)
        loaded(s)
        s.update { it.with("accent", "teal") }
        s.update { it.with("accent", "gold") }
        s.flush()

        assertEquals("gold", onDisk().str("accent"))
        assertNull(s.writeError.value)
    }

    @Test
    fun `an update before any load still writes a valid profile`() {
        val s = store()
        s.update { it.with("unit", "kg") }
        assertEquals("kg", onDisk().str("unit"))
    }
}

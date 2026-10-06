package olygym.app.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two assets are generated from the frontend sources (native/tools/assets.mjs) and committed, so
 * they can drift. This is the check that catches it: both still parse, and both still hold what the
 * app expects to find in them.
 *
 * A JVM test cannot reach android.assets, so it reads the files where they sit in the module.
 */
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun asset(name: String): File {
    listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .firstOrNull { it.isFile }
        ?.let { return it }
    throw AssertionError("asset not found: $name (working directory is ${File(".").absolutePath})")
}

class AssetsTest {

    @Test
    fun `the catalogue asset is the whole Catalyst catalogue`() {
        val list = json.decodeFromString<List<Exercise>>(asset("exercises-data.json").readText())
        assertEquals(624, list.size)
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.all { it.id.startsWith("wl") && it.n.isNotBlank() })
        // The steps are what the workout screen reads; a lost array here would be silent.
        assertTrue(list.all { it.st.isNotEmpty() })
        // The body part drives the Library filter chips and is the muscle map's fallback, so every
        // entry has one. Muscle tags are the specific half and 101 entries do not carry any — that
        // is the catalogue as it ships, and lib/muscles.js falls back to bp for them.
        assertTrue(list.all { it.bp != null })
        assertTrue(list.all { it.eq != null })
        assertTrue("only ${list.count { it.tg != null || it.sm.isNotEmpty() }} carry a muscle tag",
            list.count { it.tg != null || it.sm.isNotEmpty() } >= 500)
    }

    @Test
    fun `the Italian pack still covers the English keys`() {
        val it = json.decodeFromString<Map<String, String>>(asset("i18n/it.json").readText())
        assertTrue("only ${it.size} keys", it.size >= 1000)
        assertTrue(it.keys.all { it.isNotBlank() })
        assertTrue(it.values.all { it.isNotBlank() })
        // Spots that the plan screen and its neighbours rely on.
        assertEquals("Piano", it["Plan"])
        assertEquals("Esercizi", it["Exercises"])
        assertEquals("Settimana del {0}", it["Week of {0}"])
    }

    @Test
    fun `the pack is the same shape the web app loads`() {
        // Keys are English sentences; 40 of them are the same word in Italian too (Home, Cardio,
        // Splits), so an identity mapping is not evidence of a missing translation.
        val it = json.decodeFromString<Map<String, String>>(asset("i18n/it.json").readText())
        assertEquals(1420, it.size)
        assertTrue(it.containsKey("{0} exercise"))
    }
}

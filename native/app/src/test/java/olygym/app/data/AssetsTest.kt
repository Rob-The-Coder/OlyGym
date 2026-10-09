package olygym.app.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.lib.INERT
import olygym.app.lib.MUSCLES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three assets are generated from the frontend sources (native/tools/assets.mjs) and committed, so
 * they can drift. This is the check that catches it: each still parses, and each still holds what the
 * app expects to find in it.
 *
 * A JVM test cannot reach android.assets, so it reads the files where they sit in the module.
 */
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun asset(name: String): File {
    listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .firstOrNull { it.isFile }
        ?.let { return it }
    throw AssertionError("asset not found: $name (working directory is " + File(".").absolutePath + ")")
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
        assertTrue("only " + list.count { it.tg != null || it.sm.isNotEmpty() } + " carry a muscle tag",
            list.count { it.tg != null || it.sm.isNotEmpty() } >= 500)
    }

    @Test
    fun `the Italian pack still covers the English keys`() {
        val it = json.decodeFromString<Map<String, String>>(asset("i18n/it.json").readText())
        assertTrue("only " + it.size + " keys", it.size >= 1000)
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

    @Test
    fun `the body geometry is the four views the map draws`() {
        // The map parses this off the main thread and cannot report a bad asset, so a moved viewBox or
        // a lost path list would simply be a blank silhouette on the screen.
        val root = Json.parseToJsonElement(asset("body-paths.json").readText()) as JsonObject
        assertEquals(listOf("male", "female"), root.keys.toList())
        val drawable = mutableSetOf<String>()
        root.forEach { (body, geometry) ->
            val views = geometry as JsonObject
            assertEquals(setOf("front", "back"), views.keys)
            views.forEach { (view, data) ->
                val obj = data as JsonObject
                val box = obj["vb"].toString().trim('"').split(' ')
                assertEquals(4, box.size)
                assertTrue(body + "/" + view + " has a viewBox of " + obj["vb"], box.all { it.toFloatOrNull() != null })
                val parts = obj["p"] as JsonObject
                // The front carries the head, hands and knees; the back is the smaller view (14 parts
                // on the female body), and every part it does name has paths.
                assertTrue(body + "/" + view + " has " + parts.size + " parts", parts.size >= 14)
                parts.forEach { (slug, list) ->
                    assertTrue(slug + " has no paths", (list as JsonArray).isNotEmpty())
                    drawable += slug
                }
            }
        }
        // Every muscle the readings name, and every part of the silhouette, has geometry somewhere.
        val missing = (MUSCLES + INERT).filterNot { it in drawable }
        assertEquals(emptyList<String>(), missing)
    }
}

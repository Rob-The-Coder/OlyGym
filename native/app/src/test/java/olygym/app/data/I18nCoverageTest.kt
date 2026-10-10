package olygym.app.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every string the UI asks for has an Italian translation.
 *
 * The locale asset is generated from frontend/src/locales/it.js, and a key missing there does not
 * fail — it silently ships English inside the Italian UI, which is what had happened to 78 keys:
 * Home's tiles, Stats' section labels, the filter chip, the instructional copy of the config
 * sheets, and the accessibility names this work added. The React app reads the same file and had
 * the same leak.
 *
 * A source read, the way AssetsTest checks the generated assets, because a JVM test cannot compose
 * the UI. Only literal keys are checked: t(someVariable) is translated at runtime from a table
 * this test cannot see, and pretending to check it would be worse than saying so.
 */
class I18nCoverageTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(name: String): File {
        listOf(File("src/main/assets/" + name), File("app/src/main/assets/" + name))
            .firstOrNull { it.isFile }
            ?.let { return it }
        throw AssertionError("asset not found: " + name + " (working directory is " + File(".").absolutePath + ")")
    }

    private fun sources(): List<File> {
        val roots = listOf(File("src/main/java/olygym/app"), File("app/src/main/java/olygym/app"))
        val root = roots.firstOrNull { it.isDirectory }
            ?: throw AssertionError("sources not found (working directory is " + File(".").absolutePath + ")")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** The key of every t("...") call, with the escapes Kotlin's own lexer would remove. */
    private fun keysIn(src: String): List<String> =
        Regex("(?<![A-Za-z0-9_$.])t\\(\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(src)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
            .filter { it.isNotBlank() }
            .toList()

    @Test
    fun `every literal the UI asks for is in the Italian asset`() {
        val italian = json.decodeFromString<JsonObject>(asset("i18n/it.json").readText()).keys
        assertTrue("the locale asset looks too small: " + italian.size, italian.size > 1000)

        val missing = linkedMapOf<String, String>()
        sources().forEach { f ->
            keysIn(f.readText()).forEach { key ->
                if (key !in italian) missing.putIfAbsent(key, f.path.substringAfter("olygym/app/"))
            }
        }
        assertTrue(
            "these strings would ship in English inside the Italian UI: " +
                missing.entries.joinToString { it.key + " (" + it.value + ")" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `the Italian asset is not just a copy of the English keys`() {
        // A key pasted back verbatim would still count as "present" above, so a sample of the ones
        // that were leaking is checked for real content.
        val italian = json.decodeFromString<JsonObject>(asset("i18n/it.json").readText())
        listOf("Streak", "Weight", "Overview", "Balance", "Filters", "Best", "Last", "Decrease", "Increase", "Close")
            .forEach { key ->
                val value = italian[key]?.toString()?.trim('"')
                assertTrue(key + " has no Italian translation", value != null && value != key)
            }
    }
}


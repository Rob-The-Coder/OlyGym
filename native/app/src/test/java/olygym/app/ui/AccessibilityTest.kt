package olygym.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accessibility invariants that the compiler cannot hold on its own.
 *
 * IconButton's label is a required parameter now, so a new call site without one does not build.
 * What the compiler cannot see is whether the label is *translated*, whether the optional label of
 * a Check was remembered, and whether one of the three animations the app owns still answers the
 * system's reduced-motion request. Those are the regressions this reads the sources for — the same
 * approach AssetsTest and RecoveryTest take for generated files.
 *
 * It is deliberately a source check rather than a UI test: this module has no instrumentation
 * test artifact in the offline cache, and the honest substitute is the device's own accessibility
 * tree (adb shell uiautomator dump), which is what docs/M3-EXPRESSIVE.md records per screen.
 */
class AccessibilityTest {

    private val sources: List<File> by lazy {
        val roots = listOf(File("src/main/java/olygym/app"), File("app/src/main/java/olygym/app"))
        val root = roots.firstOrNull { it.isDirectory }
            ?: throw AssertionError("sources not found (working directory is " + File(".").absolutePath + ")")
        val ui = File(root, "ui")
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun text(f: File) = f.readText()
    private fun where(f: File) = f.path.substringAfter("olygym/app/")

    /**
     * The call's own text: from the name to a little past it, which is enough for its arguments.
     * A declaration is not a call site — the three shared controls are declared with "fun", and one
     * of them is an extension, so the check looks for the keyword on the whole line rather than
     * just before the name.
     */
    private fun calls(src: String, name: String): List<String> =
        Regex("(?<![A-Za-z0-9_])" + name + "\\(").findAll(src)
            .filterNot { m ->
                val start = src.lastIndexOf('\n', m.range.first).let { if (it < 0) 0 else it + 1 }
                val end = src.indexOf('\n', m.range.first).let { if (it < 0) src.length else it }
                val line = src.substring(start, end)
                val funAt = line.indexOf("fun ")
                funAt >= 0 && funAt < line.indexOf(name)
            }
            .map { src.substring(it.range.first, minOf(src.length, it.range.first + 260)) }
            .toList()

    @Test
    fun `every icon button is named with a translated string`() {
        val bad = mutableListOf<String>()
        sources.forEach { f ->
            calls(text(f), "IconButton").forEach { call ->
                val named = call.contains("label =") || call.contains("t(\"")
                if (!named) bad += where(f) + " -> " + call.lineSequence().first().trim()
            }
        }
        assertTrue("an icon button has no accessible name: " + bad, bad.isEmpty())
    }

    @Test
    fun `every set checkbox is named`() {
        val bad = mutableListOf<String>()
        sources.forEach { f ->
            calls(text(f), "Check").forEach { call ->
                if (!call.contains("label =")) bad += where(f) + " -> " + call.lineSequence().first().trim()
            }
        }
        assertTrue("a set checkbox has no accessible name: " + bad, bad.isEmpty())
    }

    @Test
    fun `no stepper arrow is named in English only`() {
        val bad = mutableListOf<String>()
        sources.forEach { f ->
            listOf("StepButton", "StepperButton").forEach { name ->
                calls(text(f), name).forEach { call ->
                    if (!call.contains("t(\"")) bad += where(f) + " -> " + call.lineSequence().first().trim()
                }
            }
        }
        assertTrue("a stepper arrow's name is not translated: " + bad, bad.isEmpty())
    }

    @Test
    fun `the three animations the app owns all answer reduced motion`() {
        listOf(
            "ui/components/WaveProgress.kt",
            "ui/AppNavigator.kt",
            "MainActivity.kt",
        ).forEach { rel ->
            val roots = listOf(File("src/main/java/olygym/app"), File("app/src/main/java/olygym/app"))
            val f = roots.map { File(it, rel) }.firstOrNull { it.isFile }
                ?: throw AssertionError("missing source: " + rel)
            assertTrue(
                rel + " starts an animation without consulting reduceMotion()",
                text(f).contains("reduceMotion"),
            )
        }
    }
}

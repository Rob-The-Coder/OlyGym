package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.js
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The printable page of frontend/src/lib/plan-share.js. The web has no test for it; the strings the
 * printer ends up with are still worth pinning, because everything in them is assembled by hand.
 */

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * The shipped catalogue plus the three lifts these cases name, read the way MusclesTest reads it. The
 * catalogue is one module-level index every test class shares, and a class that installs a small book
 * and walks away is how the class after it started failing: this one leaves the real index behind.
 */
private val printCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    json.decodeFromString<List<Exercise>>(file.readText()) + listOf(
        Exercise(id = "snatch", n = "Snatch", bp = "Snatch"),
        Exercise(id = "clean", n = "Clean & Jerk", bp = "Clean & Jerk"),
        Exercise(id = "pullup", n = "Pull-up", bp = "Back"),
    )
}

private fun entry(id: String, vararg extra: Pair<String, Any?>): JsonObject {
    val pairs = mutableListOf<Pair<String, Any?>>("id" to id)
    pairs.addAll(extra)
    return js(*pairs.toTypedArray())
}

private fun day(dow: Int, name: String, vararg ex: JsonObject): JsonObject =
    js("dow" to dow, "name" to name, "ex" to JsonArray(ex.toList()))

private fun week(startIso: String, name: String, vararg days: JsonObject): JsonObject =
    js("id" to "w-$startIso", "startIso" to startIso, "name" to name, "days" to JsonArray(days.toList()))

private fun state(vararg weeks: JsonObject): JsonObject =
    js("unit" to "kg", "weeks" to JsonArray(weeks.toList()))

class PlanSharePrintTest {

    @Before
    fun setUp() {
        I18nCore.setLangState("en")
        Catalogue.install(printCatalogue)
    }

    private fun page(vararg weeks: JsonObject): String = planPrintHTML(state(*weeks), "")

    @Test
    fun schemeCarriesSetsRepsAndLoad() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 10, "weight" to 60))))
        assertTrue(html.contains("3 × 10 · 60 kg"))
    }

    @Test
    fun timedSchemeReadsAsMinutesAndSeconds() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3, "sec" to 45, "mode" to "time"))))
        assertTrue(html.contains("3 × 0:45"))
    }

    @Test
    fun addedWeightReadsAsAdded() {
        val html = page(
            week(
                "2026-10-05", "",
                day(1, "Day 1", entry("pullup", "sets" to 3, "reps" to 8, "weight" to 10, "bodyweight" to true)),
            ),
        )
        assertTrue(html.contains("3 × 8 · +10 kg"))
    }

    @Test
    fun missingRepsFallsBackToTen() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3))))
        assertTrue(html.contains("3 × 10"))
    }

    @Test
    fun aZeroWeightPrintsNoLoad() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 5, "weight" to 0))))
        assertTrue(html.contains("3 × 5"))
        assertFalse(html.contains("0 kg"))
    }

    @Test
    fun adjacentExercisesSharingASupersetIdBecomeOneBlock() {
        val html = page(
            week(
                "2026-10-05", "",
                day(
                    1, "Day 1",
                    entry("snatch", "sets" to 1, "reps" to 1, "sg" to "sg-1"),
                    entry("clean", "sets" to 1, "reps" to 1, "sg" to "sg-1"),
                ),
            ),
        )
        // One block, its tag, and both lifts inside it.
        assertTrue(html.contains("class=\"ss\""))
        assertTrue(html.contains("class=\"ss-tag\">Complex"))
        assertTrue(html.contains("Snatch"))
        assertTrue(html.contains("Clean &amp; Jerk"))
    }

    @Test
    fun aSupersetIdOnItsOwnIsNotABlock() {
        val html = page(
            week(
                "2026-10-05", "",
                day(
                    1, "Day 1",
                    entry("snatch", "sets" to 1, "reps" to 1, "sg" to "sg-1"),
                    entry("clean", "sets" to 1, "reps" to 1),
                    entry("pullup", "sets" to 1, "reps" to 1, "sg" to "sg-1"),
                ),
            ),
        )
        assertFalse(html.contains("class=\"ss\""))
    }

    @Test
    fun aWeekWithNoExercisesIsNotPrintedAtAll() {
        val html = page(
            week("2026-09-28", "Ghost", day(1, "Day 1")),
            week("2026-10-05", "Real", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 5))),
        )
        assertFalse(html.contains("Ghost"))
        assertTrue(html.contains("Real"))
    }

    @Test
    fun anEmptyPlanSaysNoRoutines() {
        val html = planPrintHTML(js("unit" to "kg"), "")
        assertTrue(html.contains("No routines yet."))
    }

    @Test
    fun theOwnerAndTodayAreTheSubtitle() {
        val html = planPrintHTML(
            state(week("2026-10-05", "Base", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 5)))),
            "Marco",
        )
        assertTrue(html.contains("Marco · " + todayISO()))
    }

    @Test
    fun aWeekWithNoNameOfItsOwnReadsAsTheWeekOfItsDate() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 5))))
        assertTrue(html.contains("Week of"))
    }

    @Test
    fun aDayWithNoNameOfItsOwnReadsAsTheWeekday() {
        val html = page(week("2026-10-05", "", day(3, "", entry("snatch", "sets" to 3, "reps" to 5))))
        assertTrue(html.contains("Wednesday"))
    }

    @Test
    fun namesAndNotesAreEscaped() {
        val html = page(
            week(
                "2026-10-05", "",
                day(1, "A & B", entry("snatch", "sets" to 3, "reps" to 5, "note" to "<unsafe>")),
            ),
        )
        assertTrue(html.contains("A &amp; B"))
        assertTrue(html.contains("&lt;unsafe&gt;"))
        assertFalse(html.contains("<unsafe>"))
    }

    @Test
    fun anExerciseThatIsNotInTheBookSaysUnknown() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("gone", "sets" to 3, "reps" to 5))))
        assertTrue(html.contains("Unknown exercise"))
    }

    @Test
    fun theDocumentCarriesTheHeaderTheFooterAndThePaperRules() {
        val html = page(week("2026-10-05", "", day(1, "Day 1", entry("snatch", "sets" to 3, "reps" to 5))))
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<title>Weekly Training Plan</title>"))
        assertTrue(html.contains("class=\"kicker\">OlyGym"))
        assertTrue(html.contains("Made with OlyGym"))
        assertTrue(html.contains("opengym.duarte-santos.ch"))
        // The two rules that keep an exercise, and the day it sits in, from splitting across pages.
        assertTrue(html.contains("break-inside: avoid"))
        assertTrue(html.contains("@page { margin: 16mm 15mm; }"))
    }
}

package olygym.app.lib

import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * format.js ships no test, so this is written from its behaviour — and pinned against the values
 * the browser produces for the same input. The date strings and the number separators were
 * measured on this machine for both locales before being written down here.
 */
class FormatTest {

    @Before
    fun setUp() {
        I18nCore.setLangState("en")
        setWeightDecimals(1)
    }

    @After
    fun tearDown() {
        I18nCore.setLangState("en")
        setWeightDecimals(1)
    }

    @Test
    fun `isoOf is the ISO date, whatever the locale`() {
        assertEquals("2026-03-25", isoOf(LocalDate.of(2026, 3, 25)))
        assertEquals("2026-01-05", isoOf(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `fmtDate in English, short and long, with and without the year`() {
        assertEquals("25 Mar", fmtDate("2026-03-25", long = false))
        assertEquals("Wed 25 Mar", fmtDate("2026-03-25", long = true))
        assertEquals("25 Mar 2026", fmtDate("2026-03-25", long = false, withYear = true))
    }

    @Test
    fun `fmtDate follows the UI language, like the web app`() {
        I18nCore.setLangState("it")
        assertEquals("25 mar", fmtDate("2026-03-25", long = false))
        assertEquals("mer 25 mar", fmtDate("2026-03-25", long = true))
    }

    @Test
    fun `fmtDur reads as hours and minutes, and durPart drops an unknown duration`() {
        assertEquals("45 min", fmtDur(45 * 60_000L))
        assertEquals("1h 30m", fmtDur(90 * 60_000L))
        assertEquals("2h 0m", fmtDur(120 * 60_000L))
        assertEquals(listOf("45 min"), durPart(45 * 60_000L))
        assertEquals(emptyList<String>(), durPart(59_000L))
    }

    @Test
    fun `capWords capitalises a name the way CSS capitalize does on the web`() {
        // Measured against the browser: it is a title case, not a sentence case — every word,
        // every time. The values below are the ones format.js produces for the same input.
        assertEquals("Overhead Squat", capWords("overhead squat"))
        assertEquals("2 Position Power Snatch", capWords("2 position power snatch"))
        assertEquals("Clean & Jerk", capWords("clean & jerk"))
        assertEquals("Romanian Deadlift (Rdl)", capWords("romanian deadlift (rdl)"))
        assertEquals("Back Squat - Paused", capWords("back squat - paused"))
        assertEquals("", capWords(null))
    }

    @Test
    fun `fmtNum keeps whole numbers whole and never adds precision that is not there`() {
        assertEquals("62.5", fmtNum(62.5))
        assertEquals("100", fmtNum(100.0))
        assertEquals("7,535", fmtNum(7535.0))
        setWeightDecimals(2)
        assertEquals(2, weightDecimals())
        assertEquals("62.25", fmtNum(62.25))
        assertEquals("100", fmtNum(100.0))
        setWeightDecimals(9)
        assertEquals(1, weightDecimals())   // anything that is not 2 reads as 1
    }

    @Test
    fun `fmtNum groups on the UI language's own convention`() {
        I18nCore.setLangState("it")
        // Measured on the JVM. Node on this machine ships no Italian grouping data and prints
        // "7535"; a browser with full ICU prints "7.535", which is what the port must match.
        assertEquals("7.535", fmtNum(7535.0))
        assertEquals("62,5", fmtNum(62.5))
    }

    @Test
    fun `fmtVol keeps the profile's unit`() {
        assertEquals("7,535 kg", fmtVol(7535.0, "kg"))
    }

    @Test
    fun `the counts have their own plural, because an English key cannot inflect`() {
        assertEquals("1 exercise", exCount(1))
        assertEquals("3 exercises", exCount(3))
        assertEquals("1 routine", routineCount(1))
        assertEquals("2 routines", routineCount(2))
    }

    @Test
    fun `weekStartOf only knows Monday and Sunday, and defaults to Monday`() {
        assertEquals(MONDAY, weekStartOf(null))
        assertEquals(MONDAY, weekStartOf(1))
        assertEquals(SUNDAY, weekStartOf(0))
        assertEquals(MONDAY, weekStartOf(7))   // not a getDay() index; Monday is the safe read
    }

    @Test
    fun `weekOrder rotates from the profile's first weekday`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0), weekOrder(MONDAY))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), weekOrder(SUNDAY))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0), weekOrder())
    }

    @Test
    fun `weekDayOffset is the column a weekday sits in`() {
        assertEquals(0, weekDayOffset(1, MONDAY))
        assertEquals(6, weekDayOffset(0, MONDAY))
        assertEquals(0, weekDayOffset(0, SUNDAY))
        assertEquals(6, weekDayOffset(6, SUNDAY))
    }

    @Test
    fun `startOfWeek lands on the week's first day, Monday or Sunday`() {
        // Wednesday 25 March 2026.
        assertEquals(LocalDate.of(2026, 3, 23), startOfWeek("2026-03-25", MONDAY))
        assertEquals(LocalDate.of(2026, 3, 22), startOfWeek("2026-03-25", SUNDAY))
        // The first day of the week is its own answer, in both conventions.
        assertEquals(LocalDate.of(2026, 3, 23), startOfWeek("2026-03-23", MONDAY))
        assertEquals(LocalDate.of(2026, 3, 22), startOfWeek("2026-03-22", SUNDAY))
    }

    @Test
    fun `weekKey is equal for two dates in the same week and different across the boundary`() {
        assertEquals(weekKey("2026-03-25"), weekKey("2026-03-29"))
        assertEquals("2026-03-23", weekKey("2026-03-25"))
        assertEquals("2026-03-16", weekKey("2026-03-22"))
        // A Sunday start moves the same two dates into different weeks.
        assertEquals("2026-03-22", weekKey("2026-03-25", SUNDAY))
        assertEquals("2026-03-22", weekKey("2026-03-22", SUNDAY))
    }

    @Test
    fun `uid is unique enough to key a fresh week`() {
        val ids = (1..200).map { uid() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotEmpty() })
    }

    @Test
    fun `the accent list is the eight seeds, and the default is one of them`() {
        assertEquals(8, ACCENTS.size)
        assertTrue(ACCENTS.containsKey(DEFAULT_ACCENT))
        assertEquals("#6750A4", ACCENTS[DEFAULT_ACCENT])
    }
}

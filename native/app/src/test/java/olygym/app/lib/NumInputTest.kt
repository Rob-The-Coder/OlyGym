package olygym.app.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The NumberField commit rule, which frontend/src/components/ui.jsx applies inline and nothing
 * tested: a decimal keypad in many locales offers a comma, "12.3.4" has one number in it, and an
 * unlogged RIR is not a zero.
 */
class NumInputTest {

    @Test
    fun `a comma is a decimal point`() {
        assertEquals("1.5", parseNumInput("1,5").draft)
        assertEquals(1.5, parseNumInput("1,5").value)
    }

    @Test
    fun `only the first point survives and the rest are dropped`() {
        assertEquals("1.23", parseNumInput("1.2.3").draft)
        assertEquals(1.23, parseNumInput("1.2.3").value)
    }

    @Test
    fun `a whole number field keeps nothing after the point`() {
        assertEquals("12", parseNumInput("12.7", decimal = false).draft)
        assertEquals(12.0, parseNumInput("12.7", decimal = false).value)
    }

    @Test
    fun `everything that is not a digit or a point is stripped`() {
        assertEquals("62", parseNumInput("62 kg").draft)
        // a minus sign is stripped, not honoured: the field is never negative
        assertEquals("5", parseNumInput("-5").draft)
        assertEquals(5.0, parseNumInput("-5").value)
        assertEquals("", parseNumInput("abc").draft)
        assertEquals(0.0, parseNumInput("abc").value)
    }

    @Test
    fun `a half typed point is a draft with the value it already implies`() {
        assertEquals("62.", parseNumInput("62.").draft)
        assertEquals(62.0, parseNumInput("62.").value)
    }

    @Test
    fun `empty means null for a nullable field and zero otherwise`() {
        // nullable is the caller's choice: the weight and rep fields want a zero, the effort
        // field wants to tell "nothing logged" from RIR 0.
        assertNull(parseNumInput("", nullable = true).value)
        assertNull(parseNumInput(".", nullable = true).value)
        assertEquals("", parseNumInput("", nullable = true).draft)
        assertEquals(0.0, parseNumInput("").value)
        assertEquals(0.0, parseNumInput("").value)
        assertEquals(0.0, parseNumInput("abc").value)
    }

    @Test
    fun `a logged zero is a zero, not nothing`() {
        // RIR 0 is a set taken to failure: the one case where "none" and "none left" are different.
        assertEquals(0.0, parseNumInput("0", nullable = true).value)
        assertEquals("0", parseNumInput("0", nullable = true).draft)
    }

    @Test
    fun `a large weight keeps its digits`() {
        assertEquals("300", parseNumInput("300").draft)
        assertEquals(300.0, parseNumInput("300").value)
        assertEquals("222.5", parseNumInput("222,5").draft)
        assertEquals(222.5, parseNumInput("222,5").value)
    }
}

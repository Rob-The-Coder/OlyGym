package olygym.app.lib

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * i18n-core.js ships no test, so this is written from its behaviour. The convention it protects is
 * unusual enough to be worth pinning: the key *is* the English string, and a key with no entry in
 * the pack is not a bug — it is the English fallback.
 */
class I18nCoreTest {

    @After
    fun tearDown() {
        I18nCore.setLangState("en")
    }

    @Test
    fun `English is the fallback and needs no pack`() {
        I18nCore.setLangState("en")
        assertEquals("en", I18nCore.lang)
        assertEquals("Cancel", I18nCore.t("Cancel"))
        assertEquals("en-GB", I18nCore.dateLocale().toLanguageTag())
    }

    @Test
    fun `a key with an entry is translated, a key without one is the English it already is`() {
        I18nCore.setLangState("it", mapOf("Cancel" to "Annulla"))
        assertEquals("it", I18nCore.lang)
        assertEquals("Annulla", I18nCore.t("Cancel"))
        assertEquals("Confirm", I18nCore.t("Confirm"))
        assertEquals("it-IT", I18nCore.dateLocale().toLanguageTag())
    }

    @Test
    fun `arguments are substituted on the fallback too`() {
        I18nCore.setLangState("en")
        assertEquals("2 exercises", I18nCore.t("{0} exercises", 2))
        I18nCore.setLangState("it", mapOf("Week of {0}" to "Settimana del {0}"))
        assertEquals("Settimana del 23 mar", I18nCore.t("Week of {0}", "23 mar"))
        // A translated key with no placeholder still gets the English shape's arguments replaced.
        I18nCore.setLangState("it", mapOf("{0} exercises" to "{0} esercizi"))
        assertEquals("3 esercizi", I18nCore.t("{0} exercises", 3))
    }

    @Test
    fun `an empty translation is not a translation`() {
        I18nCore.setLangState("it", mapOf("Cancel" to ""))
        assertEquals("Cancel", I18nCore.t("Cancel"))
    }

    @Test
    fun `an unknown language reads as English rather than as nothing`() {
        I18nCore.setLangState("de", mapOf("Cancel" to "Abbrechen"))
        assertEquals("en", I18nCore.lang)
        assertEquals("Cancel", I18nCore.t("Cancel"))
        assertEquals("en-GB", I18nCore.dateLocale().toLanguageTag())
    }

    @Test
    fun `switching to English drops the pack rather than leaving it loaded`() {
        I18nCore.setLangState("it", mapOf("Cancel" to "Annulla"))
        assertEquals("Annulla", I18nCore.t("Cancel"))
        I18nCore.setLangState("en")
        assertEquals("Cancel", I18nCore.t("Cancel"))
    }

    @Test
    fun `version moves on every language change, which is what a subscription keys on`() {
        val first = I18nCore.setLangState("en")
        val second = I18nCore.setLangState("it")
        assertTrue(second > first)
    }

    @Test
    fun `instructions fall back to the catalogue's own steps`() {
        val steps = listOf("Set the feet", "Pull under")
        I18nCore.setLangState("it")
        assertEquals(steps, I18nCore.instrFor("wl58", steps))
        // An id the pack does not cover is the same read, not an error.
        assertEquals(emptyList<String>(), I18nCore.instrFor("wl999", emptyList()))
    }

    @Test
    fun `no name pack ships, so the catalogue's own name is the name`() {
        I18nCore.setLangState("it")
        assertEquals("2 position power snatch", I18nCore.exerciseNameFor("wl163", "2 position power snatch"))
        assertEquals("2 position power snatch", I18nCore.exerciseNameSearchText("wl163", "2 position power snatch"))
    }

    @Test
    fun `a name pack is honoured, and an identical loanword is not repeated in brackets`() {
        // The loader only passes a pack for a language that ships one. This is the seam it uses,
        // and the same one the web tests use while the OlyGym catalogue has no pack.
        I18nCore.setLangState("it", null, null, mapOf("wl163" to "2 position power snatch"))
        assertEquals("2 position power snatch", I18nCore.exerciseNameFor("wl163", "2 position power snatch"))

        I18nCore.setLangState("it", null, null, mapOf("wl163" to "Strappo in due posizioni"))
        assertEquals(
            "Strappo in due posizioni (2 position power snatch)",
            I18nCore.exerciseNameFor("wl163", "2 position power snatch"),
        )
        assertEquals(
            "Strappo in due posizioni 2 position power snatch",
            I18nCore.exerciseNameSearchText("wl163", "2 position power snatch"),
        )
    }

    @Test
    fun `baseLang is the identity while nothing is derived`() {
        assertEquals("it", I18nCore.baseLang("it"))
        assertTrue(I18nCore.INSTR_LANGS.contains(I18nCore.baseLang("en")))
        assertEquals(Locale.forLanguageTag("en-GB"), I18nCore.dateLocale())
    }
}

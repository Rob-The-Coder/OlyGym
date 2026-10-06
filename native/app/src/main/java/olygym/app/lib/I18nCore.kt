package olygym.app.lib

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import olygym.app.data.asStr
import olygym.app.data.jsText

/**
 * Runtime half of the i18n module: the language, the pack and the readers. A port of
 * frontend/src/lib/i18n-core.js, with the same English-string-as-key convention — the key is the
 * English sentence, and an absent key is the English fallback rather than a missing string.
 *
 * The browser shell around it (lazy pack loading, the React subscription) has no analogue here:
 * the stores get the pack once, at startup, and a language change is a restart.
 */
object I18nCore {
    val LANGS = linkedMapOf("en" to "English", "it" to "Italiano")

    /** Instruction packs, keyed by exercise id. Only English ships, and English is the catalogue. */
    val INSTR_LANGS = listOf("en")

    /** No translated exercise-name pack ships. The seam is kept for when one does. */
    val EXERCISE_NAME_LANGS = emptyList<String>()

    private val DATE_LOCALES = mapOf("en" to "en-GB", "it" to "it-IT")

    var lang: String = "en"
        private set

    /** Bumped on every setLangState, so a caller can key a recomposition on it. */
    var version: Int = 0
        private set

    private var dict: Map<String, String> = emptyMap()
    private var instr: Map<String, List<String>>? = null
    private var exerciseNames: Map<String, String>? = null

    fun dateLocale(): Locale = Locale.forLanguageTag(DATE_LOCALES[lang] ?: "en-GB")

    /**
     * The language whose packs a locale loads. Nothing is derived in this fork — the seam is kept
     * so the loader and the membership tests below keep one code path.
     */
    fun baseLang(l: String): String = l

    /** Translate a source string; {0},{1}… are replaced with args, on the English fallback too. */
    fun t(s: String, vararg args: Any?): String {
        var v = dict[s]?.takeIf { it.isNotEmpty() } ?: s
        args.forEachIndexed { i, a -> v = v.replace("{$i}", a?.toString() ?: "") }
        return v
    }

    /**
     * A stored message, as the helpers write them into a plan's why: [key, arg, ...]. The args go
     * through jsText, so an integral number keeps its "30" rather than becoming "30.0".
     */
    fun tMessage(message: JsonArray): String {
        val key = message.firstOrNull().asStr() ?: return ""
        return t(key, *message.drop(1).map { it.jsText() }.toTypedArray())
    }

    /** Instructions for an exercise in the current language (the catalogue's steps are English). */
    fun instrFor(exId: String, st: List<String>): List<String> = instr?.get(exId) ?: st

    /**
     * Built-in catalogue names are bilingual when a complete translated name pack is active.
     * User-created exercises have no entry in the pack and keep their exact chosen name.
     */
    fun exerciseNameFor(exId: String, canonical: String): String {
        val translated = exerciseNames?.get(exId) ?: return canonical
        // Some names are the established term in the target language too; repeating an identical
        // loanword in parentheses adds noise rather than context.
        return if (translated.lowercase(Locale.forLanguageTag(lang)) == canonical.lowercase(Locale.ENGLISH)) {
            translated
        } else {
            "$translated ($canonical)"
        }
    }

    /** Search both the localized and canonical English title without changing persisted data. */
    fun exerciseNameSearchText(exId: String, canonical: String): String {
        val translated = exerciseNames?.get(exId)
        return if (translated != null) "$translated $canonical" else canonical
    }

    /** Called once the pack has been read. Returns the new version. */
    fun setLangState(
        newLang: String,
        newDict: Map<String, String>? = null,
        newInstr: Map<String, List<String>>? = null,
        newExerciseNames: Map<String, String>? = null,
    ): Int {
        lang = if (LANGS.containsKey(newLang)) newLang else "en"
        dict = if (lang == "en") emptyMap() else (newDict ?: emptyMap())
        instr = if (lang == "en" || !INSTR_LANGS.contains(baseLang(lang))) null else newInstr
        exerciseNames = if (lang == "en") null else newExerciseNames
        version++
        return version
    }
}

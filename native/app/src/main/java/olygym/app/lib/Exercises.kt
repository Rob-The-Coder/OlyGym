package olygym.app.lib

import java.text.Normalizer
import java.util.WeakHashMap
import kotlin.math.abs
import olygym.app.data.Catalogue
import olygym.app.data.Exercise

/*
 * The catalogue's pure helpers — a port of frontend/src/lib/exercises.js.
 *
 * The React module owns one list and one id index. Those live here in data/Exercises.kt's
 * Catalogue, installed once at startup, and the index-dependent helpers read it. An "exercise
 * object" is the catalogue's own Exercise data class.
 */

/** exercises.js re-exports the dataset as EXDB and aliases the same list CATALOGUE. */
val EXDB: List<Exercise>
    get() = Catalogue.list

val CATALOGUE: List<Exercise>
    get() = Catalogue.list

// No dataset-wide secondary-muscle corrections yet for the OlyGym catalogue, which already
// carries its own sm tags. Kept as the overlay exercises.js applies, so the read is unchanged
// when one arrives.
private val SECONDARY_ADDITIONS: Map<String, List<String>> = emptyMap()

/** Secondary muscles for an exercise, with the conservative additions overlaid. */
fun smOf(ex: Exercise?): List<String> {
    // exercises.js wraps a non-array sm in a one-element list; Exercise.sm is typed as a list,
    // so that branch has no input it can serve.
    val base = ex?.sm ?: emptyList()
    val additions = ex?.id?.let { SECONDARY_ADDITIONS[it] } ?: emptyList()
    return (base + additions).distinct()
}

// Equipment options present in a given list of exercises, most common first (issue #6).
fun equipmentOf(list: List<Exercise>): List<String> {
    val counts = LinkedHashMap<String, Int>()
    list.forEach { e ->
        val eq = e.eq
        if (!eq.isNullOrEmpty()) counts[eq] = (counts[eq] ?: 0) + 1
    }
    return counts.keys.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { it })
}

/** The distinct body parts in the built-in catalogue, sorted. */
val BODYPARTS: List<String>
    get() = Catalogue.list.mapNotNull { it.bp }.distinct().sorted()

// Custom (user-created) exercises live in synced state S.customEx (issue #11) and are merged
// into the id index so every Catalogue lookup keeps working unchanged.
fun registerCustom(list: List<Exercise>?) {
    Catalogue.registerCustom(list ?: emptyList())
}

/** Full searchable catalogue — customs first so your own exercises are easy to find. */
fun allExercises(customEx: List<Exercise>?): List<Exercise> =
    (customEx ?: emptyList()) + Catalogue.list

private val DIACRITICS = Regex("[\\u0300-\\u036f]")
private val WHITESPACE = Regex("\\s+")
private val TOKENS = Regex("[^a-z0-9]+")

// Normalizes text by lowercasing and stripping diacritics/accents (e.g. "elevação" -> "elevacao").
fun normalizeStr(s: String?): String =
    Normalizer.normalize(s ?: "", Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

private fun searchableText(value: Any?): String = when (value) {
    null -> ""
    is List<*> -> value.joinToString(" ") { searchableText(it) }
    else -> value.toString()
}

private fun isSubsequence(needle: String, hay: String): Boolean {
    var i = 0
    for (ch in hay) {
        if (ch == needle.getOrNull(i)) i++
        if (i == needle.length) return true
    }
    return false
}

// Allow one missing, extra or substituted character, or an adjacent transposition, in long query
// tokens. Short tokens stay exact/substring-only: words such as "row" and "curl" are too common
// for fuzzy matching to be useful.
//
// Only the exercise's own name words are ever compared this way. Body part, target and equipment
// words are shared by a whole slice of the catalogue, so one accidental neighbour ("wrist" ~
// "waist") would list hundreds of unrelated exercises ahead of the real hits (QA C26).
private fun nearWord(a: String, b: String): Boolean {
    if (a.length < 5 || abs(a.length - b.length) > 1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    if (i == a.length) return b.length - i <= 1
    if (a.length == b.length) {
        return a.drop(i + 1) == b.drop(i + 1) ||
            (a.getOrNull(i) == b.getOrNull(i + 1) && a.getOrNull(i + 1) == b.getOrNull(i) &&
                a.drop(i + 2) == b.drop(i + 2))
    }
    return if (a.length > b.length) a.drop(i + 1) == b.drop(i) else a.drop(i) == b.drop(i + 1)
}

/**
 * Fuzzy match score for one exercise against a query. Best hits: exact field match, then field
 * prefix, then word-boundary starts, then substrings (closer to the start scores better), and
 * finally typo-tolerant ordered subsequences. Fields are weighted, the name dominating. 0 means
 * no match.
 */
fun searchScore(exercise: Exercise?, query: String?): Double {
    val needle = searchableText(query).lowercase().trim()
    if (needle.isEmpty()) return 1.0
    val fields = listOf(
        "n" to 100.0, "tg" to 40.0, "eq" to 40.0, "sm" to 30.0,
        "muscleGroups" to 30.0, "primaries" to 30.0, "secondaries" to 30.0,
        "desc" to 10.0, "cues" to 10.0,
    )
    // Token-level matching: every query word must match somewhere (any order), so "press bench"
    // finds "Bench Press". The score sums each token's best hit.
    val tokens = needle.split(TOKENS).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return 0.0
    var total = 0.0
    for (token in tokens) {
        var best = 0.0
        for ((field, weight) in fields) {
            val hay = searchableText(searchField(exercise, field)).lowercase()
            if (hay.isEmpty()) continue
            if (hay == token) best = maxOf(best, weight * 4)
            else if (hay.startsWith(token)) best = maxOf(best, weight * 3)
            val idx = hay.indexOf(token)
            if (idx > 0) best = maxOf(best, weight * 2 - minOf(idx, 20) * 0.5)
            if (hay.split(TOKENS).any { it.startsWith(token) }) best = maxOf(best, weight * 2.5)
            if (isSubsequence(token, hay)) {
                best = maxOf(best, weight + maxOf(0.0, 10.0 - (hay.length - token.length)))
            }
        }
        if (best == 0.0) return 0.0 // every token must match
        total += best
    }
    return total
}

/**
 * The read of one scoring field. exercises.js reads them off any object; the native Exercise
 * models n/tg/eq/sm/desc, and muscleGroups/primaries/secondaries/cues exist only on the web
 * custom-exercise shape, so they read as absent here.
 */
private fun searchField(exercise: Exercise?, field: String): Any? = when (field) {
    "n" -> exercise?.n
    "tg" -> exercise?.tg
    "eq" -> exercise?.eq
    "sm" -> exercise?.sm
    "desc" -> exercise?.desc
    else -> null
}

fun matchesExerciseSearch(exercise: Exercise?, query: String?): Boolean =
    searchScore(exercise, query) > 0

// Equipment with no meaningful load in kg: your own body, or a band whose "weight" is a colour.
private val BODYWEIGHT_EQ = setOf("body weight", "band", "resistance band")

fun isBodyweightEq(idOrEx: Any?): Boolean {
    val eq = when (idOrEx) {
        is String -> Catalogue[idOrEx]?.eq
        is Exercise -> idOrEx.eq
        else -> null
    }
    return BODYWEIGHT_EQ.contains(eq)
}

// An id that resolves to nothing — a plan built against another dataset, a custom deleted on
// another device — still has to render. A placeholder keeps it visible instead of taking the
// whole view down on the first ex.n. The web placeholder also carries missing: true; Exercise
// has no such field and no consumer reads it.
fun exOr(id: String): Exercise = Catalogue[id] ?: Exercise(
    id = id,
    n = I18nCore.t("Unknown exercise"),
    bp = "",
    tg = "",
    eq = "",
    sm = emptyList(),
    st = emptyList(),
)

private class Corpus(val version: Int, val s: String, val nameWords: List<String>)

// The haystack is built once per exercise and cached, keyed by the i18n version (bumped by every
// setLangState) so switching language rebuilds the translated terms. The web module caches in a
// WeakMap; Exercise's structural equality makes a WeakHashMap the same read.
private val corpusCache = WeakHashMap<Exercise, Corpus>()

private fun corpusOf(e: Exercise?): Corpus {
    val v = I18nCore.version
    val hit = if (e != null) corpusCache[e] else null
    if (hit != null && hit.version == v) return hit
    val sm = e?.sm ?: emptyList()
    val name = normalizeStr(I18nCore.exerciseNameSearchText(e?.id ?: "", e?.n ?: ""))
    val parts = listOf(
        name,
        e?.tg ?: "", I18nCore.t(e?.tg ?: ""),
        e?.eq ?: "", I18nCore.t(e?.eq ?: ""),
        e?.bp ?: "", I18nCore.t(e?.bp ?: ""),
    ) + sm + sm.map { I18nCore.t(it) } + listOf(e?.desc ?: "")
    val entry =
        Corpus(v, normalizeStr(parts.joinToString(" ")), name.split(WHITESPACE).filter { it.isNotEmpty() })
    if (e != null) corpusCache[e] = entry
    return entry
}

private fun queryTokens(query: String?): List<String> =
    normalizeStr(query ?: "").split(WHITESPACE).filter { it.isNotEmpty() }

// Every token has to appear in the corpus; a token listed in fuzzy may instead be one edit away
// from a name word.
private fun matchTokens(e: Exercise?, tokens: List<String>, fuzzy: Set<String>): Boolean {
    val corpus = corpusOf(e)
    return tokens.all { tok ->
        corpus.s.contains(tok) || (fuzzy.contains(tok) && corpus.nameWords.any { nearWord(tok, it) })
    }
}

/** Single-exercise check; every token may fall back to the typo tolerance. */
fun matchExercise(e: Exercise?, query: String?): Boolean {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return true
    if (e == null) return false
    return matchTokens(e, tokens, tokens.toSet())
}

// Search a list, exact hits first: a token that appears literally in at least one exercise is
// taken at its word for the whole list, and only a token with no exact hit anywhere is allowed
// the typo tolerance (QA C26).
fun searchExercises(list: List<Exercise>, query: String?): List<Exercise> {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return list
    val fuzzy = tokens.filter { tok -> list.none { corpusOf(it).s.contains(tok) } }.toSet()
    return list.filter { matchTokens(it, tokens, fuzzy) }
}

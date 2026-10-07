package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Exercise
import olygym.app.data.asStr

/*
 * Match the coach's Italian to an exercise in the catalogue — a port of
 * frontend/src/lib/plan-aliases.js.
 *
 * The coach writes in gym Italian: one idea has three names, the words arrive in any order
 * ("tirate strappo alte" is a snatch high pull) and a good half of the modifiers — "no piedi",
 * "touch n go", "sosp bassa" — have no Catalyst equivalent at all. So this is not a dictionary
 * lookup: a name is turned into the catalogue's words, the catalogue is searched for the entry
 * that covers the most of them, and whatever it cannot cover stays in the note in the coach's
 * own words.
 *
 * Three outcomes, the three tiers agreed before this was written (OLYGYM_PLAN.md WS5):
 *   1. the catalogue has it                    -> the exercise, and the coach's words only when the
 *                                                catalogue name says a word he never said
 *   2. the base lift exists, the rest does not -> the base, plus the coach's words in the note
 *   3. nothing at all                          -> a custom exercise under the coach's own name
 * plus the shapes that are not exercises on their own: a fragment ("+ sosp bassa", "touch n go",
 * "due normali") describes the exercise before it, so it lands in that exercise's note rather than
 * inventing a second one.
 *
 * matchComponent is exported by the JS, so it stays public for its spec; the importer builds on
 * matchName. The complex is split by CoachSheet.kt's splitComplex, the same read coach-sheet.js
 * hands the importer.
 */

/** One matched component of a coach's name, and the item shape matchName returns. */
data class NameMatch(
    val tier: Int,
    val id: String,
    val name: String,
    val bp: String?,
    val note: String,
    val exact: Boolean,
    val raw: String,
)

data class NameMatches(val items: List<NameMatch>, val ignored: List<String>)

/**
 * One component before matchName folds the fragments in: the same fields as [NameMatch], plus
 * fragment for a part that names no exercise of its own. This is matchComponent's own return
 * shape, the JS object with { tier, id, name, bp, note, exact, raw } or { fragment, raw }.
 */
data class ComponentMatch(
    val tier: Int = 0,
    val id: String = "",
    val name: String = "",
    val bp: String? = null,
    val note: String = "",
    val exact: Boolean = false,
    val raw: String = "",
    val fragment: Boolean = false,
)

private val NON_ALNUM = Regex("[^a-z0-9]+")
private val WHITESPACE = Regex("\\s+")

/** Lower case, no accents, no punctuation: the key both the matcher and S.planAliases use. */
fun normName(text: String?): String =
    normalizeStr(text).replace(NON_ALNUM, " ").replace(WHITESPACE, " ").trim()

// What the coach says -> what the catalogue says. Longest phrase first, so "spinta in piedi" is a
// push jerk and not a "spinta" plus an "in piedi". An empty value is a word that carries no
// exercise meaning ("di", "secondi", "kg") and is dropped.
private val GLOSSARY: Map<String, String> = linkedMapOf(
    // heads — the lift itself
    "strappo" to "snatch", "strappi" to "snatch", "girata" to "clean", "girate" to "clean",
    "slancio" to "clean-jerk",
    "tirata" to "pull", "tirate" to "pull", "stacco" to "deadlift", "stacchi" to "deadlift",
    "gambe avanti" to "front squat", "gambe dietro" to "back squat",
    "rdl" to "romanian deadlift rdl",
    "spinta di forza" to "push press", "spinte di forza" to "push press",
    "spinta in piedi" to "push jerk", "spinte in piedi" to "push jerk",
    "spinta in spaccata" to "split jerk", "spinte in spaccata" to "split jerk",
    "spinta dalla mezza spaccata" to "push jerk", "spinte strappo no piedi" to "snatch push press",
    "snatch press" to "snatch press", "sots press" to "sots press", "oh squat" to "overhead squat",
    "snatch balance" to "snatch balance", "push press" to "push press", "push jerk" to "push jerk",
    "power jerk" to "power jerk", "power clean" to "power clean", "hip snatch" to "hip snatch",
    "military press con bilanciere" to "press",
    "piegamenti alle parallele" to "dip", "piegamenti alla parallele" to "dip", "parallele" to "dip",
    "piegamenti" to "dip", "trazioni" to "pull-up", "trazione" to "pull-up",
    "trazioni con elastico" to "pull-up", "chin ups" to "chin-up", "chin up" to "chin-up",
    "alzate laterali" to "dumbbell lateral raise", "alzate a y con disco" to "y raise",
    "rematore con manubrio" to "single arm dumbbell row",
    "stacchi rumeni ad una gamba" to "single leg romanian deadlift",
    "stacchi rumeni a una gamba" to "single leg romanian deadlift",
    "stacchi rumeni" to "romanian deadlift rdl",
    "good morning" to "good morning", "plank" to "plank", "dead bug" to "dead bug",
    "box jump" to "box jump", "pogo jump" to "pogo jump", "lu raises" to "lu raise",
    "lu raise" to "lu raise",
    "seesaw bent over row" to "seesaw bent over row", "pullover" to "pullover",
    "one arm pullover" to "pullover",
    "clamshell side plank" to "side plank clamshell", "side bend alla spalliera" to "side bend",
    "wall sit" to "wall sit",
    // modifiers — where the bar starts, how fast, what is paused
    "sosp alta" to "hang", "sosp bassa" to "low hang", "sosp" to "hang", "da sosp" to "hang",
    "dai blocchi" to "block", "blocchi" to "block", "da blocco" to "block",
    "dal deficit" to "on riser", "da deficit" to "on riser", "deficit" to "on riser",
    // «no piedi» is Catalyst's "with no jump" (feet flat, no stomp), and «in piedi» is the power
    // version caught standing — not "from power position", which is the *hips* (see «inguine»).
    "no piedi" to "with no jump", "touch n go" to "touch and go", "tng" to "touch and go",
    "in continuita" to "touch and go", "con ritorno" to "", "in piedi" to "power",
    "inguine" to "from power position", "spaccata" to "split", "spinta strappo" to "snatch push press",
    "spinte strappo" to "snatch push press", "spinta" to "jerk", "spinte" to "jerk",
    "incastro" to "", "cavalletti" to "rack", "carico" to "",
    "in buca" to "pause", "stop in buca" to "pause", "stop sopra ginocchio" to "pause",
    "stop pre" to "pause", "stop post" to "pause", "stop" to "pause", "pausa" to "pause",
    "fermo" to "pause",
    "alta" to "high", "alte" to "high", "georgiane" to "georgian", "velocita" to "speed",
    "strappo di forza" to "muscle snatch", "strappi di forza" to "muscle snatch",
    "girata di forza" to "muscle clean", "girate di forza" to "muscle clean",
    // words that are structure, tempo or noise rather than an exercise
    "e" to "and", "di" to "", "del" to "", "della" to "", "delle" to "", "dalla" to "", "dal" to "",
    "da" to "", "dai" to "",
    "con" to "with", "presa" to "", "muovere" to "", "muovo" to "",
    "senza" to "without", "una" to "", "un" to "", "due" to "", "tre" to "", "secondi" to "",
    "each" to "", "a" to "", "al" to "",
    "ai" to "", "alla" to "", "allo" to "", "in" to "", "il" to "", "la" to "", "le" to "",
    "lo" to "", "gli" to "", "ma" to "", "fino" to "",
    "sopra" to "", "sotto" to "", "testa" to "", "petto" to "", "disco" to "", "caricamento" to "",
    "parallelo" to "parallel",
    "parallela" to "parallel", "roba" to "", "stessa" to "", "stesso" to "", "normale" to "",
    "normali" to "", "kg" to "",
    "max" to "", "uscita" to "", "discesa" to "", "salita" to "", "risalita" to "",
    "lenta" to "slow", "lentissima" to "slow",
    "lentissimo" to "slow", "controllata" to "", "controllato" to "", "dinamiche" to "",
    "dinamica" to "",
    "impugnatura" to "grip", "media" to "", "dietro la testa" to "behind the neck",
    "ginocchio" to "", "gin" to "",
    "buca" to "", "prima" to "", "pre" to "", "post" to "", "fase" to "", "piedi" to "",
)

private val PHRASES: List<String> =
    GLOSSARY.keys.filter { it.contains(' ') }.sortedByDescending { it.length }
private val SINGLES: List<String> =
    GLOSSARY.keys.filter { !it.contains(' ') }.sortedByDescending { it.length }

// Words the translation drops that still say something about how to lift — a tempo, a paused
// position, a plate. A name holding one of these is never a plain tier 1: the catalogue has the
// lift but not what the coach asked for, and in a year nobody remembers which of the two it was.
private val MEANINGFUL: Set<String> = setOf(
    "dinamiche", "dinamica", "lentissima", "lentissimo", "lenta", "lento", "discesa", "salita",
    "risalita", "velocita", "uscita", "ritorno", "caricamento", "controllata", "controllato",
    "impugnatura", "parallelo", "testa", "petto", "disco", "max", "continuita", "ginocchio", "gin",
    "buca", "sopra", "sotto", "prima", "pre", "post", "fermo", "ferma", "isometrico",
)

// Where the bar starts, or how the feet are allowed to work: a component made of nothing but one of
// these is its own movement of the complex, not a note on the one above. "+ sosp bassa" after
// "strappo" is a snatch from the low hang. A style word — "touch n go", "dinamiche", "con ritorno" —
// is not: the catalogue has no exercise named after it, and the note is where it belongs.
private val POSITION: Set<String> = setOf(
    "hang", "low hang", "block", "on riser", "power", "from power position", "with no jump",
    "with no contact",
)
// …and the words those phrases are made of, for peeling them off a name word by word.
private val POSITION_WORD: Set<String> = POSITION.flatMap { it.split(" ") }.toSet()

// Words that mean "this is an exercise", in the coach's language or in the catalogue's. A
// component with none of these describes the exercise above it instead of naming a new one.
private val HEAD_WORDS: Set<String> = setOf(
    "strappo", "strappi", "girata", "girate", "slancio", "tirata", "tirate", "stacchi", "stacco",
    "gambe", "rdl", "spinta", "spinte", "piegamenti", "trazioni", "alzate", "rematore",
    "snatch", "clean", "jerk", "press", "pull", "push", "squat", "deadlift", "plank", "good",
    "box", "pogo", "dead", "wall", "sots", "oh", "seesaw", "lu", "chin", "military", "bug",
    "jump", "raise", "raises", "row", "pullover", "dip", "bend", "morning", "balance", "sit",
    "clamshell", "side",
)

// Words that carry no exercise at all in a catalogue name: they never have to be covered, and they
// never count as something the coach did not say.
private val FUNCTION: Set<String> = setOf("and", "with", "from", "the", "of", "in", "on", "to")

// With a pull, a deadlift or an RDL the second word names the grip, and "slancio" there is the
// clean rather than the whole clean and jerk: "tirate slancio" is a clean pull and "stacchi
// strappo" a snatch deadlift. Read as "girata" each lands on the catalogue name by itself.
private val GRIP_HEAD = Regex("\\b(stacchi|stacco|tirata|tirate|rdl)\\b")
private val SLANCIO = Regex("\\bslancio\\b")
private val NUMERIC = Regex("\\d+")

// The body part a custom exercise gets, since there is no catalogue entry to copy one from.
private val CUSTOM_BP: Map<String, String> = mapOf(
    "y raise" to "Accessory - Upper Body",
    "lu raise" to "Accessory - Upper Body",
    "pullover" to "Accessory - Upper Body",
    "seesaw bent over row" to "Accessory - Upper Body",
    "pogo jump" to "Jumping & Plyometrics",
    "single leg romanian deadlift" to "Accessory - Lower/Whole Body",
)
private const val DEFAULT_BP = "General Exercises"

private class Entry(val ex: Exercise, val n: String, val words: List<String>)

private class Candidate(val entry: Entry, val cover: Int, val extra: Int)

private class Head(val english: String, val rest: String)

// The JS builds this once per module load; the native catalogue is installed at app start and per
// test class, so the cache is keyed by the installed list instead of being built once forever.
private var indexFor: List<Exercise>? = null
private var indexCache: List<Entry> = emptyList()

private fun catalogueIndex(): List<Entry> {
    val list = EXDB
    if (indexFor === list) return indexCache
    val built = ArrayList<Entry>()
    val seen = HashSet<String>()
    for (ex in list) {
        val n = normName(ex.n)
        // The catalogue holds a couple of duplicate names (clean-jerk twice). The first wins, the
        // way the exercise id index behaves everywhere else.
        if (!seen.add(n)) continue
        built.add(Entry(ex, n, n.split(" ").filter { it.isNotEmpty() }))
    }
    indexFor = list
    indexCache = built
    return built
}

private fun exactName(candidate: String?): Entry? {
    val n = normName(candidate)
    if (n.isEmpty()) return null
    return catalogueIndex().find { it.n == n }
}

private fun words(text: String?): List<String> =
    normName(text).split(" ").filter { it.isNotEmpty() }

// A rep count the coach wrote ("1-2 secondi", "3 dinamiche") is not an exercise word, so a bare
// "3" can never match. Numbers stay in the *catalogue's* names, where they tell "1-14 front squat"
// apart from a plain front squat.
private fun exerciseWords(list: List<String>): List<String> =
    list.filter { it !in FUNCTION && !NUMERIC.matches(it) }

/** How many of wanted the entry's own words account for, and how many it says on top. */
private fun coverage(entry: Entry, wanted: List<String>): Pair<Int, Int> {
    val pool = entry.words.filter { it !in FUNCTION }.toMutableList()
    var cover = 0
    for (w in wanted) {
        val at = pool.indexOf(w)
        if (at < 0) continue
        pool.removeAt(at)
        cover++
    }
    return cover to (entry.words.count { it !in FUNCTION } - cover)
}

/**
 * The catalogue entry covering the most of these words, then the one adding the fewest — word
 * order does not matter ("pull snatch high" is a snatch high pull), and the catalogue's own order
 * settles the ties, which puts the snatch family before the clean family: the right bias here.
 *
 * With [plain] only names the coach's words fully account for are considered. That is what keeps a
 * half-understood name on its own lift: "strappo con fase lenta" must not become the first
 * catalogue name that happens to contain both *slow* and *snatch*.
 */
private fun bestMatch(parts: List<String>, plain: Boolean = false): Candidate? {
    val wanted = exerciseWords(parts)
    if (wanted.isEmpty()) return null
    var best: Candidate? = null
    var bestCover = 0
    var bestExtra = Int.MAX_VALUE
    for (entry in catalogueIndex()) {
        val (cover, extra) = coverage(entry, wanted)
        if (cover == 0 || (plain && extra != 0)) continue
        if (cover > bestCover || (cover == bestCover && extra < bestExtra)) {
            best = Candidate(entry, cover, extra)
            bestCover = cover
            bestExtra = extra
        }
    }
    return best
}

private fun translate(text: String?): String {
    val raw = text ?: ""
    val gripped = if (GRIP_HEAD.containsMatchIn(normName(raw))) SLANCIO.replace(raw, "girata") else raw
    var s = " " + normName(gripped) + " "
    for (p in PHRASES) s = s.replace(" " + p + " ", " " + (GLOSSARY[p] ?: "") + " ")
    return s.split(" ").map { GLOSSARY[it] ?: it }.filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * The lift a component starts with, and whatever the coach wrote after it — the base a partly
 * understood name falls back to, and whether there is a modifier beyond that base at all.
 */
private fun headOf(text: String?): Head {
    val n = normName(text)
    for (p in PHRASES + SINGLES) {
        if (n != p && !n.startsWith(p + " ")) continue
        return Head(GLOSSARY[p] ?: "", n.substring(p.length).trim())
    }
    return Head("", n)
}

private fun hasHead(text: String?): Boolean = words(text).any { it in HEAD_WORDS }

private fun carriesMeaning(text: String?): Boolean = words(text).any { it in MEANINGFUL }

private fun isPosition(text: String?): Boolean = words(translate(text)).any { it in POSITION }

/**
 * Match one component of the coach's name.
 *
 *   { tier: 1|2|3, id, name, bp, note, exact, raw }
 *
 * or { fragment: true, raw } when it is not an exercise but a note on the one before it.
 * aliases is S.planAliases: a component the user corrected on the review screen, keyed by
 * normName, wins over the matcher — which is what makes a fix stick for every later week.
 */
fun matchComponent(raw: String?, aliases: JsonObject? = null): ComponentMatch {
    val text = (raw ?: "").trim()
    if (text.isEmpty()) return ComponentMatch(fragment = true, raw = text)
    val corrected = aliases?.get(normName(text))?.asStr()
    val fixed = if (corrected.isNullOrEmpty()) null else EXDB.find { it.id == corrected }
    if (fixed != null) {
        return ComponentMatch(
            tier = 1, id = fixed.id, name = fixed.n, bp = fixed.bp, exact = true, note = "",
            raw = text,
        )
    }

    // Checked before any matching, so a bare "3 dinamiche", "touch n go" or "sosp bassa" can never
    // land on a catalogue name that happens to share a word with it.
    if (!hasHead(text)) return ComponentMatch(fragment = true, raw = text)

    val candidate = translate(text)
    // A translation that names a catalogue entry outright is the answer, before any word-overlap
    // scoring: "spinta in spaccata" is a split jerk, not the "jerk from split" that shares its words.
    val exact = exactName(candidate)
    if (exact != null) {
        val plain = !carriesMeaning(text)
        return ComponentMatch(
            tier = 1, id = exact.ex.id, name = exact.ex.n, bp = exact.ex.bp, exact = plain,
            note = if (plain) "" else text, raw = text,
        )
    }

    val parts = words(candidate)
    val full = bestMatch(parts)
    if (full != null && full.cover == exerciseWords(parts).size) {
        val entry = full.entry.ex
        // "Wall sit con disco" and "Gambe avanti prima stop in buca" match a catalogue name word for
        // word and still ask for something that name does not say. The note is what keeps that on
        // the record, so a name carrying one of those words is never a plain tier 1.
        val plain = full.extra == 0 && !carriesMeaning(text)
        return ComponentMatch(
            tier = 1, id = entry.id, name = entry.n, bp = entry.bp, exact = plain,
            note = if (plain) "" else text, raw = text,
        )
    }

    // Half understood: the best name the coach's own words fully account for, else the head lift.
    // Deliberately not "the name sharing the most words": a head that resolves to nothing ("y raise",
    // "pogo jump") is a custom exercise, not the first catalogue name containing *raise*. The head is
    // only allowed to pick a longer name when the coach wrote something past it to leave in the note.
    val head = headOf(text)
    val exactHead = exactName(head.english)
    // When the component is nothing but a head ("Pogo jump", "y raise") the catalogue either has
    // that name or has nothing: sharing one word with a longer name is not an answer.
    val whole = head.rest.isEmpty()
    val base: Entry? = if (whole) {
        exactHead
    } else {
        bestMatch(parts, true)?.entry
            ?: exactHead
            ?: bestMatch(words(head.english), true)?.entry
            ?: bestMatch(words(head.english))?.entry
    }
    if (base != null) {
        return ComponentMatch(
            tier = 2, id = base.ex.id, name = base.ex.n, bp = base.ex.bp, exact = false, note = text,
            raw = text,
        )
    }

    return ComponentMatch(
        tier = 3, id = "", name = text,
        bp = CUSTOM_BP[normName(translate(text))] ?: CUSTOM_BP[normName(text)] ?: DEFAULT_BP,
        exact = false, note = "", raw = text,
    )
}

/**
 * A component that names no lift of its own but does name a position — "+ sosp bassa", "+ dai
 * blocchi" — is the exercise above, done from there: a movement of the complex in its own right.
 * The lift comes from the component above (its head, not its full name, so "strappo sosp alta"
 * followed by "sosp bassa" is a snatch from the low hang, not a hang snatch from the low hang).
 * When Catalyst has that name it is used; when it does not — it has no low hang at all — the
 * movement is one of the user's own exercises, under the name the coach's words describe.
 */
private fun promote(part: String, last: NameMatch): NameMatch? {
    // The lift it is a position of: the exercise above with its own position words taken off, so
    // "stacchi slancio da sosp alta" (a hang clean deadlift) followed by "sosp bassa" is a clean
    // deadlift from the low hang — the grip and the lift travel, the hang does not.
    val lift = words(last.name).filter { it !in POSITION_WORD }.joinToString(" ")
        .ifEmpty { headOf(last.raw).english }
    if (lift.isEmpty()) return null
    val candidate = listOf(translate(part), lift).filter { it.isNotEmpty() }.joinToString(" ")
    val known = bestMatch(words(candidate))
    if (known != null && known.cover == exerciseWords(words(candidate)).size) {
        return NameMatch(
            tier = if (known.extra != 0) 2 else 1,
            id = known.entry.ex.id,
            name = known.entry.ex.n,
            bp = known.entry.ex.bp,
            note = if (known.extra != 0) part else "",
            exact = known.extra == 0,
            raw = part,
        )
    }
    return NameMatch(
        tier = 3, id = "", name = candidate,
        bp = last.bp?.takeIf { it.isNotEmpty() } ?: DEFAULT_BP,
        note = part, exact = false, raw = part,
    )
}

/**
 * Match a whole row: the complexes split on "+", a component that names no exercise folded into
 * the note of the one before it.
 *
 *   { items: [{ tier, id, name, bp, note, exact, raw }], ignored: [raw] }
 *
 * items is what becomes the routine's exercises — in order, one per component, sharing a superset
 * group when the row had more than one.
 */
fun matchName(rawName: String?, aliases: JsonObject? = null): NameMatches {
    val items = mutableListOf<NameMatch>()
    val ignored = mutableListOf<String>()
    for (part in splitComplex(rawName)) {
        val hit = matchComponent(part, aliases)
        if (hit.fragment) {
            val last = items.lastOrNull()
            if (last == null) { ignored.add(part); continue }
            val promoted = if (isPosition(part)) promote(part, last) else null
            if (promoted != null) { items.add(promoted); continue }
            // "+ touch n go", "dinamiche", "due normali": the coach describing what he just named.
            items[items.size - 1] = last.copy(
                note = listOf(last.note, part).filter { it.isNotEmpty() }.joinToString(" · "),
            )
            continue
        }
        items.add(NameMatch(hit.tier, hit.id, hit.name, hit.bp, hit.note, hit.exact, hit.raw))
    }
    return NameMatches(items.toList(), ignored.toList())
}

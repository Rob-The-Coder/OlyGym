package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The spec of frontend/src/lib/plan-aliases.test.js, one case each, in the same order. The
 * catalogue is the committed asset the app installs at startup, read here where a JVM test can
 * reach it, exactly as MediaTest and RecoveryTest do.
 */

private val planJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val planCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    planJson.decodeFromString<List<Exercise>>(file.readText())
}

class PlanAliasesTest {

    @Before
    fun installCatalogue() {
        Catalogue.install(planCatalogue)
    }

    // Ids come from the catalogue by name, never written by hand: a rename in the dataset should
    // fail here in one place instead of pointing at an id that quietly belongs to something else.
    private fun idOf(name: String): String {
        val ex = EXDB.find { it.n == name }
            ?: error("'$name' is not in the catalogue any more")
        return ex.id
    }

    private fun match(text: String, aliases: JsonObject? = null): ComponentMatch =
        matchComponent(text, aliases)

    @Test
    fun `reads the heads`() {
        assertEquals(idOf("snatch"), match("Strappo").id)
        assertEquals(1, match("Strappo").tier)
        assertEquals(idOf("clean"), match("Girata").id)
        assertEquals(idOf("clean-jerk"), match("Slancio").id)
        assertEquals(idOf("front squat"), match("Gambe avanti").id)
        assertEquals(idOf("back squat"), match("Gambe dietro").id)
        assertEquals(idOf("dip"), match("Piegamenti alle parallele").id)
        assertEquals(idOf("dip"), match("Piegamenti alla parallele").id)
        assertEquals(idOf("pull-up"), match("Trazioni con elastico").id)
        assertEquals(idOf("chin-up"), match("Chin ups").id)
        assertEquals(idOf("dumbbell lateral raise"), match("Alzate laterali").id)
        assertEquals(idOf("single arm dumbbell row"), match("Rematore con manubrio").id)
        assertEquals(idOf("press"), match("Military press con bilanciere").id)
        assertEquals(idOf("side plank clamshell"), match("Clamshell side plank").id)
        assertEquals(idOf("good morning"), match("Good morning").id)
        assertEquals(idOf("power jerk"), match("Power jerk").id)
        assertEquals(idOf("press in snatch sots press"), match("Sots press").id)
        assertEquals(idOf("overhead squat"), match("Oh squat").id)
    }

    @Test
    fun `reads the jerks apart from each other`() {
        assertEquals(idOf("push press"), match("Spinta di forza").id)
        assertEquals(idOf("push press"), match("spinte di forza").id)
        assertEquals(idOf("push jerk"), match("Spinta in piedi").id)
        assertEquals(idOf("split jerk"), match("Spinta in spaccata").id)
        assertEquals(idOf("push jerk"), match("Spinta dalla mezza spaccata").id)
        assertEquals(idOf("snatch push press"), match("Spinte strappo no piedi").id)
    }

    @Test
    fun `reads «di forza» as the muscle version, not the power one`() {
        // The user's correction (2026-09-30): "strappo di forza" is a muscle snatch — the bar never
        // comes back down to the thighs and the knees do not re-bend. It used to read as power snatch.
        assertEquals(idOf("muscle snatch"), match("Strappo di forza").id)
        assertEquals(idOf("muscle snatch"), match("strappi di forza").id)
        assertEquals(idOf("muscle clean"), match("Girata di forza").id)
        // Still a push press: this one the user confirmed, and it is the same two words.
        assertEquals(idOf("push press"), match("Spinte di forza").id)
    }

    @Test
    fun `keeps the coach’s tempo words on a muscle snatch`() {
        val paused = match("strappo di forza con pausa sotto e sopra ginocchio di 5 secondi")
        assertEquals(2, paused.tier)
        assertEquals(idOf("muscle snatch"), paused.id)
        assertEquals("strappo di forza con pausa sotto e sopra ginocchio di 5 secondi", paused.note)
    }

    @Test
    fun `takes the grip from the lift that follows a pull, a deadlift or an RDL`() {
        // «tirate slancio» is a clean pull, not a "pull" plus a clean and jerk.
        assertEquals(idOf("clean pull"), match("Tirate slancio").id)
        assertEquals(idOf("snatch pull"), match("Tirate strappo").id)
        assertEquals(idOf("snatch high pull"), match("Tirata alta strappo").id)
        assertEquals(idOf("snatch high pull"), match("Tirate strappo alte").id)
        assertEquals(idOf("clean deadlift"), match("Stacchi slancio").id)
        assertEquals(idOf("snatch deadlift"), match("Stacchi strappo").id)
        assertEquals(idOf("snatch deadlift on riser"), match("Stacchi strappo dal deficit").id)
        assertEquals(idOf("clean deadlift on riser"), match("Stacchi slancio da deficit").id)
        assertEquals(idOf("snatch grip romanian deadlift (rdl)"), match("RDL strappo").id)
        assertEquals(idOf("romanian deadlift (rdl)"), match("RDL slancio").id)
    }

    @Test
    fun `reads the modifiers that the catalogue does have`() {
        assertEquals(idOf("hang snatch"), match("Strappo da sosp alta").id)
        assertEquals(idOf("block snatch"), match("Strappo dai blocchi").id)
        assertEquals(idOf("snatch from power position"), match("Strappo dall’inguine").id)
        // «in piedi» is the power version caught standing; «inguine» is the hips (from power position).
        assertEquals(idOf("power snatch"), match("Strappo in piedi").id)
        assertEquals(idOf("snatch from power position"), match("Strappo dall’inguine").id)
        assertEquals(idOf("pause front squat"), match("Gambe avanti stop in buca").id)
        assertEquals(idOf("pause parallel back squat"), match("Gambe dietro stop a parallelo").id)
        assertEquals(idOf("snatch with no contact"), match("Strappo no contact").id)
    }

    @Test
    fun `keeps a catalogue name that says more than the coach did, without a note`() {
        // "Strappo no contact" *is* "snatch with no contact": nothing was lost, so nothing goes in
        // the note, and the word order of "tirate strappo alte" is the catalogue's business.
        assertEquals("", match("Strappo no contact").note)
        assertEquals("", match("Tirate strappo alte").note)
    }

    @Test
    fun `reads «no piedi» as Catalyst’s "with no jump"`() {
        // The user's correction (2026-09-30): "no piedi" is the feet staying flat, no jump and no
        // stomp — an exercise Catalyst names, not a note on a plain snatch.
        assertEquals(idOf("snatch with no jump"), match("Strappo no piedi").id)
        assertEquals(1, match("Strappo no piedi").tier)
        assertEquals(idOf("clean with no jump"), match("Girata no piedi").id)
        assertEquals(idOf("power clean with no jump"), match("Girata in piedi no piedi").id)
        assertEquals(idOf("power clean"), match("Girata in piedi").id)
    }

    @Test
    fun `level 2 — the base lift plus the coach’s own words`() {
        // "sosp bassa" (a low hang) has no Catalyst name: the catalogue's hang snatch is the base and
        // the coach's words say which hang he wanted.
        val lowHang = match("Strappo sosp bassa")
        assertEquals(2, lowHang.tier)
        assertEquals(idOf("hang snatch"), lowHang.id)
        assertEquals("Strappo sosp bassa", lowHang.note)

        // The catalogue has wall sit, not the plate on the belly.
        val plate = match("Wall sit con disco")
        assertEquals(idOf("wall sit"), plate.id)
        assertEquals("Wall sit con disco", plate.note)
        assertEquals("Gambe avanti prima stop in buca", match("Gambe avanti prima stop in buca").note)
    }

    @Test
    fun `level 3 — nothing in the catalogue, so the coach’s name becomes the exercise`() {
        for (name in listOf("Pogo jump", "Y raise", "Lu raises", "one arm pullover", "Stacchi rumeni ad una gamba")) {
            val hit = match(name)
            assertEquals(3, hit.tier)
            assertEquals("", hit.id)
            assertEquals(name, hit.name)
        }
    }

    @Test
    fun `gives a custom exercise a body part the map can draw`() {
        assertEquals("Pogo jump", match("Pogo jump").name)
        assertEquals("Jumping & Plyometrics", matchComponent("Pogo jump").bp)
        assertEquals("Accessory - Upper Body", matchComponent("one arm pullover").bp)
    }

    @Test
    fun `a fragment names no exercise of its own`() {
        assertTrue(match("sosp bassa").fragment)
        assertTrue(match("touch n go").fragment)
        assertTrue(match("due normali").fragment)
        assertTrue(match("3 dinamiche").fragment)
        assertTrue(match("massima velocità in uscita").fragment)
        // …which is also how a bare "3" cannot land on the catalogue's "3 position snatch".
        assertTrue(match("tre in velocità").fragment)
    }

    @Test
    fun `lets a corrected component win over the matcher`() {
        val key = normName("Piegamenti alle parallele")
        val aliases = js(key to idOf("dip"))
        assertEquals(idOf("dip"), match("Piegamenti alle parallele", aliases).id)
        // A correction keyed to something else is the correction: the matcher does not second-guess it.
        val other = js(key to idOf("push press"))
        assertEquals(idOf("push press"), match("Piegamenti alle parallele", other).id)
    }

    @Test
    fun `ignores a correction pointing at an exercise that no longer exists`() {
        assertEquals(idOf("snatch"), match("Strappo", js("strappo" to "wl-does-not-exist")).id)
    }

    @Test
    fun `splits a complex into its exercises`() {
        val items = matchName("Strappo + strappo sosp alta").items
        assertEquals(listOf(idOf("snatch"), idOf("hang snatch")), items.map { it.id })
        assertEquals(listOf("snatch", "hang snatch"), items.map { it.name })
    }

    @Test
    fun `makes a position its own movement of the complex`() {
        // The user's correction: "Strappo + strappo sosp alta + sosp bassa" is three movements —
        // snatch, hang snatch, low hang snatch — and Catalyst has no low hang at all, so the third is
        // the user's own exercise, named after what the coach's words describe.
        val result = matchName("Strappo + strappo sosp alta + sosp bassa")
        assertEquals(listOf("snatch", "hang snatch", "low hang snatch"), result.items.map { it.name })
        assertEquals(3, result.items[2].tier)
        assertEquals("", result.items[2].id)
        assertEquals("sosp bassa", result.items[2].note)
        assertEquals(emptyList<String>(), result.ignored)
    }

    @Test
    fun `carries the lift and the grip down into the next position`() {
        val items = matchName("Stacchi slancio + stacchi slancio da sosp alta + sosp bassa").items
        assertEquals(
            listOf("clean deadlift", "hang clean deadlift", "low hang clean deadlift"),
            items.map { it.name },
        )
    }

    @Test
    fun `folds a fragment that names no position into the exercise before it`() {
        val result = matchName("Strappo + strappo sosp alta + touch n go")
        assertEquals(2, result.items.size)
        assertEquals("touch n go", result.items[1].note)
        assertEquals(emptyList<String>(), result.ignored)
    }

    @Test
    fun `reports a fragment that has nothing to attach to`() {
        val result = matchName("touch n go")
        assertEquals(emptyList<NameMatch>(), result.items)
        assertEquals(listOf("touch n go"), result.ignored)
    }

    @Test
    fun `keeps the order the coach trained in`() {
        val items = matchName("High Pull + Hip snatch + snatch balance + oh squat").items
        assertEquals(
            listOf(
                idOf("snatch high pull"),
                idOf("hip snatch"),
                idOf("snatch balance"),
                idOf("overhead squat"),
            ),
            items.map { it.id },
        )
    }
}

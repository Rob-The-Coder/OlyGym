package olygym.app.lib

import java.io.File
import kotlinx.serialization.json.Json
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The spec of frontend/src/lib/media.test.js and video.test.js (the videoMode half), one case each,
 * in the same order. The catalogue is the committed asset the app installs at startup, read here
 * where a JVM test can reach it.
 */

private val mediaJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val mediaCatalogue: List<Exercise> by lazy {
    val file = listOf(File("src/main/assets/exercises-data.json"), File("app/src/main/assets/exercises-data.json"))
        .firstOrNull { it.isFile }
        ?: error("exercises-data.json not found (working directory is " + File(".").absolutePath + ")")
    mediaJson.decodeFromString<List<Exercise>>(file.readText())
}

class MediaTest {

    @org.junit.Before
    fun installCatalogue() {
        Catalogue.install(mediaCatalogue)
    }

    private fun ex(yt: String?): Exercise = Exercise(id = "wl1", n = "snatch", yt = yt)

    @Test
    fun `reads the id out of every link shape the catalogue and the coaches use`() {
        val id = "T11EcgGww-M"
        assertEquals(id, videoIdOf(ex("https://www.youtube.com/watch?v=" + id)))
        assertEquals(id, videoIdOf(ex("https://www.youtube.com/watch?list=PL1&v=" + id)))
        assertEquals(id, videoIdOf(ex("https://youtu.be/" + id)))
        assertEquals(id, videoIdOf(ex("https://www.youtube.com/embed/" + id)))
        assertEquals(id, videoIdOf(ex("https://www.youtube.com/shorts/" + id)))
    }

    @Test
    fun `gives back null for a custom exercise, an empty link or something that is not a video`() {
        assertNull(videoIdOf(Exercise(id = "custom-1", n = "my lift")))
        assertNull(videoIdOf(ex("")))
        assertNull(videoIdOf(ex("https://example.com/watch?v=T11EcgGww-M")))
        assertNull(videoIdOf(null))
    }

    @Test
    fun `falls back to the catalogue when only the id is handed over`() {
        // Routine entries carry the id alone; the catalogue entry behind it holds the link.
        assertNull(videoIdOf(Exercise(id = "wl1", n = "snatch")))
        assertEquals("T11EcgGww-M", videoIdOf(Exercise(id = "wl163")))
    }

    @Test
    fun `builds the poster url for the size asked, maxresdefault by default`() {
        assertEquals(
            "https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg",
            thumbUrl(ex("https://youtu.be/T11EcgGww-M")),
        )
        assertEquals(
            "https://img.youtube.com/vi/T11EcgGww-M/mqdefault.jpg",
            thumbUrl(ex("https://youtu.be/T11EcgGww-M"), "mqdefault"),
        )
        assertNull(thumbUrl(Exercise(id = "custom")))
    }

    @Test
    fun `walks big to small, so a missing maxresdefault steps down instead of going blank`() {
        assertEquals(
            listOf(
                "https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg",
                "https://img.youtube.com/vi/T11EcgGww-M/hqdefault.jpg",
            ),
            imageChain(ex("https://youtu.be/T11EcgGww-M")),
        )
        assertEquals(
            listOf("https://img.youtube.com/vi/T11EcgGww-M/mqdefault.jpg"),
            imageChain(ex("https://youtu.be/T11EcgGww-M"), listOf("mqdefault")),
        )
    }

    @Test
    fun `is empty, not a list of nulls, when there is nothing to show`() {
        assertEquals(emptyList<String>(), imageChain(Exercise(id = "custom-1")))
    }

    @Test
    fun `has the poster frame first, which is what the detail views show`() {
        assertEquals(
            "https://img.youtube.com/vi/T11EcgGww-M/maxresdefault.jpg",
            imageChain(ex("https://youtu.be/T11EcgGww-M")).first(),
        )
    }

    @Test
    fun `has a usable YouTube link on every built-in exercise`() {
        val noId = EXDB.filter { videoIdOf(it) == null }
        assertEquals(emptyList<String>(), noId.map { it.id })
    }

    @Test
    fun `is button by default, and for anything that is not one of the two other values`() {
        assertEquals("button", videoMode(null))
        assertEquals("button", videoMode("button"))
        assertEquals("button", videoMode("nonsense"))
    }

    @Test
    fun `passes the two explicit values through`() {
        assertEquals("off", videoMode("off"))
        assertEquals("inline", videoMode("inline"))
    }

    @Test
    fun `the platform player opens the watch page for the id, and nothing when there is none`() {
        assertEquals("https://www.youtube.com/watch?v=T11EcgGww-M", watchUrl(ex("https://youtu.be/T11EcgGww-M")))
        assertNull(watchUrl(Exercise(id = "custom-1")))
    }
}

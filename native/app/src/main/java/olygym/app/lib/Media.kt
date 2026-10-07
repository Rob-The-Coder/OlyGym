package olygym.app.lib

import olygym.app.data.Catalogue
import olygym.app.data.Exercise

/*
 * The demo media: every catalogue entry links the YouTube film of the lift (Catalyst Athletics
 * makes one per exercise), and the app shows the video poster frame, hotlinked from
 * img.youtube.com at runtime and never downloaded or committed. A port of frontend/src/lib/media.js,
 * with one deliberate difference: the picture is the same, but the video itself belongs to the
 * platform player rather than to an iframe (see watchUrl).
 */

// "ex" is normally a catalogue entry, but the sheets also hand over routine targets by id alone.
private fun videoLink(ex: Exercise?): String? =
    ex?.yt?.takeIf { it.isNotBlank() } ?: ex?.id?.let { Catalogue[it]?.yt }

private val VIDEO_ID = Regex(
    "(?:youtube\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
)

/** Null for a custom exercise, or for anything whose link is not a YouTube video. */
fun videoIdOf(ex: Exercise?): String? {
    val link = videoLink(ex) ?: return null
    val match = VIDEO_ID.find(link) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
}

/**
 * maxresdefault 1280x720 is the pretty one, hqdefault 480x360 (4:3, letterboxed, cropped by the
 * caller) always exists, and mqdefault 320x180 is the cheap one for the list rows.
 */
fun thumbUrl(ex: Exercise?, size: String = "maxresdefault"): String? =
    videoIdOf(ex)?.let { "https://img.youtube.com/vi/$it/$size.jpg" }

/**
 * The order the image walks on failure, best first. Empty means there is no media at all: the caller
 * draws its own neutral tile instead.
 */
fun imageChain(
    ex: Exercise?,
    sizes: List<String> = listOf("maxresdefault", "hqdefault"),
): List<String> = sizes.mapNotNull { thumbUrl(ex, it) }

/** off / button / inline; anything else (undefined, a legacy value) means the default. */
fun videoMode(value: String?): String = if (value == "off" || value == "inline") value else "button"

/**
 * What the demo badge opens. The web embeds a player in the page; here the video belongs to
 * whichever app the phone gives YouTube links to, which is the platform feature for it -- no
 * WebView, no player left playing behind a closed sheet, and the picture is the same poster.
 */
fun watchUrl(ex: Exercise?): String? = videoIdOf(ex)?.let { "https://www.youtube.com/watch?v=$it" }

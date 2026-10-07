package olygym.app.lib

import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.arr
import olygym.app.data.asObj
import olygym.app.data.bool
import olygym.app.data.str

/*
 * The update check — a port of frontend/src/lib/update.js. The GitHub Releases API is public and
 * needs no token, and this app reads it with its own HTTP client rather than a browser's fetch.
 *
 * The web app's versions are its own (v1.3.x, server releases with no APK); the native builds are
 * tagged apart so the same repository can carry both, and the check only ever looks at its own line.
 */

const val UPDATE_REPO = "Rob-The-Coder/OlyGym"
const val UPDATE_TAG_PREFIX = "native-v"
const val RELEASES_API = "https://api.github.com/repos/" + UPDATE_REPO + "/releases?per_page=30"
const val RELEASES_PAGE = "https://github.com/" + UPDATE_REPO + "/releases"

/** An APK smaller than this is not one. */
const val MIN_APK_BYTES = 100_000L

/** A newer native release: what to download, and the hash GitHub publishes for it. */
data class UpdateOffer(val version: String, val apkUrl: String?, val sha256: String?)

/** 1 when [a] is newer than [b], -1 when older, 0 when they are the same. "native-v" already gone. */
fun compareSemver(a: String, b: String): Int {
    val pa = a.removePrefix("v").split(".")
    val pb = b.removePrefix("v").split(".")
    for (i in 0 until 3) {
        val diff = (pa.getOrNull(i)?.toIntOrNull() ?: 0) - (pb.getOrNull(i)?.toIntOrNull() ?: 0)
        if (diff > 0) return 1
        if (diff < 0) return -1
    }
    return 0
}

/**
 * The newest published native release newer than [current], or null when there is nothing to offer.
 * Drafts and pre-releases are not offered, and a tag that is not on this app's own line is ignored.
 */
fun updateFrom(releases: JsonArray, current: String, prefix: String = UPDATE_TAG_PREFIX): UpdateOffer? {
    val candidates = releases.mapNotNull { it.asObj() }
        .filter { it.bool("draft") != true && it.bool("prerelease") != true }
        .mapNotNull { release ->
            val tag = release.str("tag_name") ?: return@mapNotNull null
            if (!tag.startsWith(prefix)) return@mapNotNull null
            release to tag.removePrefix(prefix)
        }
    val newest = candidates.maxWithOrNull { a, b -> compareSemver(a.second, b.second) } ?: return null
    if (compareSemver(newest.second, current) <= 0) return null

    // Asset names are file names: ".apk.sha256" must not be read as the APK itself.
    val assets = newest.first.arr("assets").mapNotNull { it.asObj() }
    val apk = assets.firstOrNull { (it.str("name") ?: "").endsWith(".apk", ignoreCase = true) }
    // GitHub hashes every uploaded asset, so the checksum arrives with the release JSON itself.
    val digest = apk?.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
    return UpdateOffer(newest.second, apk?.str("browser_download_url"), digest)
}

/** The SHA-256 of [bytes] as lower-case hex. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

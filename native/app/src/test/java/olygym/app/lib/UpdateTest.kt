package olygym.app.lib

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import olygym.app.data.js
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The spec of frontend/src/lib/update.test.js, plus the tag filter this port needs so the app never
 * offers itself a release from the web app's own version line. No network here: updateFrom takes the
 * releases payload as data, exactly as the API hands it over.
 */

private fun asset(name: String, digest: String? = null): JsonObject =
    if (digest == null) {
        js("name" to name, "browser_download_url" to "https://example.test/" + name)
    } else {
        js("name" to name, "browser_download_url" to "https://example.test/" + name, "digest" to digest)
    }

private fun release(
    tag: String,
    vararg assets: JsonObject,
    draft: Boolean = false,
    prerelease: Boolean = false,
): JsonObject = js(
    "tag_name" to tag,
    "assets" to JsonArray(assets.toList()),
    "draft" to draft,
    "prerelease" to prerelease,
)

private fun releases(vararg list: JsonObject): JsonArray = JsonArray(list.toList())

private val APK = asset("app-release.apk", "sha256:" + "a".repeat(64))

class UpdateTest {

    @Test
    fun aNewerVersionIsNewer() {
        assertEquals(1, compareSemver("1.3.0", "1.2.11"))
        assertEquals(1, compareSemver("2.0.0", "1.9.9"))
        assertEquals(-1, compareSemver("1.2.11", "1.3.0"))
        assertEquals(0, compareSemver("1.3.0", "1.3.0"))
    }

    @Test
    fun aVPrefixAndMissingPartsDoNotMatter() {
        assertEquals(0, compareSemver("v2.0.0", "2.0.0"))
        assertEquals(-1, compareSemver("1.2", "1.2.1"))
        assertEquals(0, compareSemver("0.1", "0.1.0"))
    }

    @Test
    fun theSameVersionIsNoUpdate() {
        assertNull(updateFrom(releases(release("native-v0.1.0", APK)), "0.1.0"))
    }

    @Test
    fun anOlderReleaseIsNoUpdate() {
        assertNull(updateFrom(releases(release("native-v0.0.9", APK)), "0.1.0"))
    }

    @Test
    fun aNewerReleaseOffersItsVersionWithoutThePrefix() {
        val offer = updateFrom(releases(release("native-v0.2.0", APK)), "0.1.0")
        assertEquals("0.2.0", offer?.version)
    }

    @Test
    fun theWebsOwnVersionLineIsIgnored() {
        assertNull(updateFrom(releases(release("v1.3.9", APK)), "0.1.0"))
    }

    @Test
    fun theHighestNativeReleaseWins() {
        val offer = updateFrom(
            releases(
                release("native-v0.2.0", APK),
                release("native-v0.10.0", APK),
                release("native-v0.3.0", APK),
            ),
            "0.1.0",
        )
        assertEquals("0.10.0", offer?.version)
    }

    @Test
    fun theApkAssetIsTheOneEndingInApk() {
        val offer = updateFrom(
            releases(release("native-v0.2.0", asset("app-release.apk.sha256"), APK)),
            "0.1.0",
        )
        assertEquals("https://example.test/app-release.apk", offer?.apkUrl)
    }

    @Test
    fun theChecksumIsTheOneGithubComputed() {
        val offer = updateFrom(releases(release("native-v0.2.0", APK)), "0.1.0")
        assertEquals("a".repeat(64), offer?.sha256)
    }

    @Test
    fun aDigestThatIsNotSha256IsNoChecksum() {
        val offer = updateFrom(releases(release("native-v0.2.0", asset("app-release.apk", "md5:abc"))), "0.1.0")
        assertNull(offer?.sha256)
    }

    @Test
    fun aReleaseWithNoApkHasNoDownload() {
        val offer = updateFrom(releases(release("native-v0.2.0", asset("notes.txt"))), "0.1.0")
        assertEquals("0.2.0", offer?.version)
        assertNull(offer?.apkUrl)
    }

    @Test
    fun draftsAndPrereleasesAreNotOffered() {
        assertNull(updateFrom(releases(release("native-v0.2.0", APK, draft = true)), "0.1.0"))
        assertNull(updateFrom(releases(release("native-v0.2.0", APK, prerelease = true)), "0.1.0"))
    }

    @Test
    fun noReleasesIsNothingToOffer() {
        assertNull(updateFrom(releases(), "0.1.0"))
    }

    @Test
    fun theHashOfAKnownInputIsTheKnownHash() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun theHashIsSixtyFourLowerCaseHexCharacters() {
        val hash = sha256Hex("OlyGym".toByteArray())
        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
    }

    @Test
    fun differentBytesHashDifferently() {
        assertEquals(false, sha256Hex("a".toByteArray()) == sha256Hex("b".toByteArray()))
    }
}

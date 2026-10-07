package olygym.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import olygym.app.lib.MIN_APK_BYTES
import olygym.app.lib.RELEASES_API
import olygym.app.lib.sha256Hex
import olygym.app.lib.updateFrom

/*
 * The in-app updater: the other half of frontend/src/lib/update.js.
 *
 * The web build has to hand the APK to the system browser, because github.com's asset CDN sends no
 * CORS headers and the WebView cannot read the body. Kotlin has no such limit, so this reads the
 * releases JSON and downloads the file itself, checks it against the SHA-256 GitHub published for
 * the upload, and hands it to the package installer.
 */

object Updater {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object Latest : State
        data class Available(val version: String, val apkUrl: String?, val sha256: String?) : State
        data class Downloading(val received: Long, val total: Long) : State
        data class Failed(val reason: String) : State
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** One request per process: Settings is opened often, and github.com need not hear about it. */
    @Volatile private var asked = false

    /** The installed version, which is what a release is compared against. */
    fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifEmpty { "0.0.0" }

    /**
     * The automatic check. Silent by design: nothing to say when the app is current or when
     * github.com cannot be reached, and asking is not worth a dialog the user did not open.
     */
    fun checkSilently(current: String) {
        if (asked) return
        asked = true
        // A failure stays quiet: the user did not ask, and a row that says "update failed" because
        // the phone was in a tunnel is worse than no answer at all.
        scope.launch { fetch(current).let { if (it !is State.Failed) _state.value = it } }
    }

    /** The check a person asked for. The answer is the caller's to show. */
    suspend fun checkNow(current: String): State {
        asked = true
        val next = fetch(current)
        _state.value = next
        return next
    }

    /**
     * Download the offered update and hand it to the installer. Nothing to do when the state is not
     * an offer with a download in it.
     */
    fun install(context: Context) {
        val offer = _state.value as? State.Available ?: return
        val url = offer.apkUrl ?: return
        _state.value = State.Downloading(0, 0)
        val app = context.applicationContext
        scope.launch {
            val next = runCatching {
                val file = download(app, url, offer.sha256)
                openInstaller(app, file)
                State.Available(offer.version, offer.apkUrl, offer.sha256)
            }.getOrElse { State.Failed(it.message ?: "") }
            _state.value = next
        }
    }

    /** Whether this app may open the package installer at all. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Send the user to the one system screen that can grant that. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:" + context.packageName))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun fetch(current: String): State = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                // 404 is a repository that is gone or not public; an empty array is one with no
                // releases yet. Neither is worth showing anyone, and both mean "nothing to offer".
                if (conn.responseCode == 404) return@withContext State.Latest
                if (conn.responseCode !in 200..299) return@withContext State.Failed("GitHub said " + conn.responseCode)
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val releases = json.parseToJsonElement(body) as? JsonArray ?: return@withContext State.Latest
                val offer = updateFrom(releases, current) ?: return@withContext State.Latest
                State.Available(offer.version, offer.apkUrl, offer.sha256)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            State.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private fun download(context: Context, url: String, sha256: String?): File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            val total = conn.contentLengthLong
            val file = File(context.cacheDir, "olygym-update.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        received += n
                        _state.value = State.Downloading(received, total)
                    }
                }
            }
            if (file.length() < MIN_APK_BYTES) error("the download is too small to be an APK")
            // GitHub hashes every uploaded asset, so a mismatch means the file or the release was
            // tampered with between here and there. A release with no digest is installed unverified.
            if (sha256 != null && sha256Hex(digest.digest()) != sha256.lowercase().trim()) {
                error("the checksum does not match")
            }
            return file
        } finally {
            conn.disconnect()
        }
    }

    private fun openInstaller(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}

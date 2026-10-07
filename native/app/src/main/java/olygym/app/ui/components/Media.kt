package olygym.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.str
import olygym.app.lib.imageChain
import olygym.app.lib.videoMode
import olygym.app.lib.watchUrl
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.theme.FullShape
import olygym.app.ui.ui

/*
 * The two pieces of the media layer: the picture, and the block a detail view draws with it.
 *
 * The picture is the poster frame of the exercise's YouTube video, hotlinked and never downloaded
 * or committed, exactly as the web does it. Compose has no network image and the app carries no
 * image library on purpose, so the fetch is the platform's own HttpURLConnection and
 * BitmapFactory with an LruCache in front -- which is all Coil would do here.
 */

private const val IMAGE_CACHE_BYTES = 8 * 1024 * 1024

private object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        // One huge poster must not evict the whole list's worth of thumbnails.
        if (bitmap.byteCount <= IMAGE_CACHE_BYTES / 4) cache.put(url, bitmap)
    }
}

private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/**
 * One picture from an image chain: the first URL that loads wins, a URL that fails steps to the next
 * (YouTube has no maxresdefault for older uploads), and an exhausted chain draws [fallback]. The
 * fallback is also what shows while the first fetch is in flight, which is what keeps a list of six
 * hundred rows from ever being blank.
 */
@Composable
fun RemoteImage(
    chain: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    var step by remember(chain) { mutableStateOf(0) }
    var bitmap by remember(chain) { mutableStateOf<Bitmap?>(null) }
    val url = chain.getOrNull(step)

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        bitmap = null
        val cached = ImageCache.get(url)
        bitmap = cached ?: fetchBitmap(url).also { if (it != null) ImageCache.put(url, it) }
        if (bitmap == null) step += 1
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        fallback()
    }
}

/**
 * The exercise's demo picture at 16:9, with the badge that hands the video to whichever app the
 * phone gives YouTube links to. A port of the Media block in frontend/src/components/Media.jsx,
 * with two deliberate differences: there is no iframe (the platform player is the video, see
 * lib/Media.kt) and no collapsed/expanded dance, which belongs to the workout card and is not
 * ported. Settings has no "demo videos" row yet, so the profile's own key is what is honoured.
 */
@Composable
fun ExerciseMedia(ex: Exercise, modifier: Modifier = Modifier) {
    val chain = imageChain(ex)
    if (chain.isEmpty()) return
    val context = LocalContext.current
    val mode = videoMode(currentProfile()?.raw?.str("video"))
    val url = watchUrl(ex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        RemoteImage(
            chain = chain,
            contentDescription = Catalogue.nameOf(ex.id),
            modifier = Modifier.fillMaxSize(),
            fallback = { MediaTile() },
        )
        if (mode != "off" && url != null) {
            val colors = MaterialTheme.colorScheme
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(FullShape)
                    .background(colors.inverseSurface.copy(alpha = 0.9f))
                    .clickable { openVideo(context, url) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.PLAY, Modifier.size(18.dp), tint = colors.inverseOnSurface, stroke = 1.8f)
                Text(
                    text = t("video"),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inverseOnSurface,
                )
            }
        }
    }
}

/** What shows while the poster loads, and where the web would draw its neutral tile. */
@Composable
private fun MediaTile() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            Glyph.DUMBBELL,
            Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openVideo(context: Context, url: String) {
    val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    if (!opened) ui.toast(t("Could not open the video"))
}

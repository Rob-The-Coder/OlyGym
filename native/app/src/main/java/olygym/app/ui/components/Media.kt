package olygym.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import olygym.app.data.Catalogue
import olygym.app.data.Exercise
import olygym.app.data.str
import olygym.app.lib.embedUrl
import olygym.app.lib.imageChain
import olygym.app.lib.videoMode
import olygym.app.ui.currentProfile
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import olygym.app.ui.t
import olygym.app.ui.theme.FullShape

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
 * The exercise's demo picture at 16:9, with the badge that plays the video in place. A port of the
 * Media block in frontend/src/components/Media.jsx: the badge mounts the embed, and 'inline' mounts
 * it with the sheet instead. No collapsed/expanded dance here — that belongs to the workout's chip,
 * where the header has to give the player its width without pushing the set table away.
 */
@Composable
fun ExerciseMedia(ex: Exercise, modifier: Modifier = Modifier) {
    val chain = imageChain(ex)
    if (chain.isEmpty()) return
    val mode = videoMode(currentProfile()?.raw?.str("video"))
    val embed = embedUrl(ex)?.takeIf { mode != "off" }
    var asked by remember(ex.id) { mutableStateOf(false) }
    val playing = embed != null && (mode == "inline" || asked)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        if (playing) {
            VideoPlayer(embed!!, Modifier.fillMaxSize())
        } else {
            RemoteImage(
                chain = chain,
                contentDescription = Catalogue.nameOf(ex.id),
                modifier = Modifier.fillMaxSize(),
                fallback = { MediaTile() },
            )
            if (embed != null) {
                val colors = MaterialTheme.colorScheme
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(FullShape)
                        .background(colors.inverseSurface.copy(alpha = 0.9f))
                        .clickable { asked = true }
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
}

/**
 * The workout header's own picture: the demo poster at the size a thumbnail wants — the web's 88px
 * chip in .ex-head .exmedia — with the play mark over it, and the expand the web gives it.
 *
 * A tap grows the chip to the whole width of the header and mounts the player; Minimize collapses it
 * and unmounts the player with it. The caller hoists the chip out of the header row while it is open
 * (the web's `.exmedia.open { order:-1; width:100% }`), because a 16:9 block is the row's entire
 * width — and that is also what the web's own comment wants: a full-width picture pushed the first
 * set row below the fold on the one screen where the phone is in your hand.
 *
 * 'inline' is deliberately not honoured here (the web's own restriction): a session can have several
 * exercises on screen at once, and several live players is several videos loading and playing at the
 * same time. The poster is what a workout wants, and playback takes the tap.
 *
 * Settings → Exercise pictures turns it off (gifSize 'off'; any legacy value reads as on, the way the
 * web reads it), and video 'off' keeps the picture and drops the mark.
 */
@Composable
fun WorkoutMedia(
    ex: Exercise,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val raw = currentProfile()?.raw ?: return
    if (raw.str("gifSize") == "off") return
    // The cheap end of the chain: this is an 88dp chip, and mqdefault is 320x180.
    val chain = imageChain(ex, listOf("mqdefault", "hqdefault"))
    if (chain.isEmpty()) return
    val embed = embedUrl(ex)?.takeIf { videoMode(raw.str("video")) != "off" }
    // Opening only ever happens from the badge, so it is the same act as asking for the video: one
    // state, not two that have to agree across the chip and the player.
    val playing = embed != null && open

    if (playing) {
        Column(modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                VideoPlayer(embed!!, Modifier.fillMaxSize())
            }
            // The web overlays Minimize on the picture (its .giftoggle, bottom-left). A Compose
            // control cannot take a touch that lands on an AndroidView — the WebView is a real child
            // view and wins the dispatch, measured on the device — so the control sits under the
            // player instead, where it works.
            Button(
                text = t("Minimize"),
                onClick = onClose,
                variant = ButtonVariant.GHOST,
                size = ButtonSize.XS,
                icon = Glyph.MINIMIZE,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }

    Box(
        modifier = modifier
            .width(88.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        RemoteImage(
            chain = chain,
            contentDescription = Catalogue.nameOf(ex.id),
            modifier = Modifier.fillMaxSize(),
            fallback = { MediaTile() },
        )
        if (embed != null) {
            // The web draws a bare white ▶ with a drop shadow. On a poster that can be bright, a dark
            // disc is the same idea with a guaranteed contrast, and the chip is the whole tap target.
            // The chip is the whole tap target and it was anonymous: the glyph inside is a Canvas,
            // and the poster's own description belongs to the image, not to this button.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics(mergeDescendants = true) { contentDescription = t("Play the demo") }
                    .clickable(onClickLabel = t("Play the demo"), role = Role.Button) { onOpen() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.PLAY, Modifier.size(16.dp), tint = Color.White, stroke = 1.8f)
                }
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


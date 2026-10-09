package olygym.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as Paint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/*
 * The demo video, in the app.
 *
 * A WebView around the same youtube-nocookie embed the web builds. That is the only route that can
 * play YouTube at all — YouTube serves no media URL an app may fetch, which is why the web uses an
 * iframe in the first place — and the shipping Capacitor app *is* a WebView running this same
 * iframe, so it is proven on these phones rather than hoped for. media3/ExoPlayer and the
 * (deprecated) YouTube Android Player API are the two routes that do not work.
 *
 * The caller mounts it only while it is wanted, and this destroys the view on the way out: a
 * WebView left behind a closed sheet keeps playing, which is the web's own reason for mounting its
 * iframe on demand too. One renderer process per mounted player is the cost, and the reason the
 * workout refuses to auto-load one per exercise.
 *
 * Not ported from the web's iframe: `allowFullScreen`. The fullscreen button needs a
 * WebChromeClient with its own view plumbing, and on a phone the player is already as wide as the
 * screen.
 */

/** The origin the embed document is loaded as: the host the iframe is served from. */
private const val EMBED_ORIGIN = "https://www.youtube-nocookie.com"

/**
 * The page the WebView actually loads: a one-iframe document, loaded *as* the embed host.
 *
 * Loading the embed URL directly does not work any more — the player answers "Video player
 * configuration error / 153", because a WebView sends no `Referer` and YouTube's embed requires one.
 * A real embedding page has an origin; `loadDataWithBaseURL` gives this one the nocookie host as its
 * base, so the iframe request carries the referrer a browser would have sent. Measured on the device:
 * direct load 153, this document plays.
 */
private fun embedDocument(url: String): String = """
    <!DOCTYPE html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
    iframe{display:block;width:100%;height:100%;border:0}</style>
    </head><body>
    <iframe src="$url" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
    </body></html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Paint.BLACK)
                settings.javaScriptEnabled = true
                // autoplay=1 is in the URL, and the tap that mounted this is the user gesture; without
                // this the embed still waits for a second one.
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadDataWithBaseURL(EMBED_ORIGIN, embedDocument(url), "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            // About:blank first: destroy() alone can leave the audio thread alive for a moment.
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

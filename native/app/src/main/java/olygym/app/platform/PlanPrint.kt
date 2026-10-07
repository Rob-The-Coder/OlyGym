package olygym.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.print.PrintJob
import android.print.PrintManager
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.webkit.WebView
import android.webkit.WebViewClient

/*
 * Hand a self-contained HTML page to the platform's print flow.
 *
 * Android renders the page itself: the system print sheet offers "Save as PDF", "Save to Drive" and
 * a real printer, and the platform lays the HTML out against whatever paper is chosen. That is the
 * whole reason there is no PDF library in the app.
 */

/** The activity behind a composition's context, or null when there is none (a preview, a test). */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Render [html] off-screen and open the print dialog under [jobName].
 */
fun printHtml(context: Context, html: String, jobName: String) {
    val activity = context.findActivity() ?: return
    val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val web = WebView(activity)
    web.settings.javaScriptEnabled = false
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            // The WebView's own adapter does the layout and the drawing, against the paper the print
            // sheet chose. Nothing about pages or PDFs is written here.
            val job = manager.print(jobName, view.createPrintDocumentAdapter(jobName), null)
            releaseWhenDone(view, job, root, Handler(Looper.getMainLooper()))
        }
    }
    // A WebView that was never attached and measured prints blank pages, so it goes into the window
    // for the life of the print job: underneath everything and covered by the app, so nothing on
    // screen changes and no touch is stolen.
    root.addView(web, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // The page is the profile's own JSON, never a remote document: no script, no network. The base
    // URL is deliberately null.
    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}

/**
 * Take the renderer out again once the job is over.
 *
 * PrintManager owns the job's lifetime, and its terminal states -- completed, cancelled, failed --
 * are the signal used here, because the page is written to the chosen destination before any of them
 * arrives. The adapter's own onFinish is the documented hook, but it belongs to the adapter rather
 * than to the job, and a renderer destroyed before the write leaves the print system drawing from
 * nothing.
 */
private fun releaseWhenDone(view: WebView, job: PrintJob, root: ViewGroup, handler: Handler) {
    val tick = object : Runnable {
        override fun run() {
            if (job.isCompleted || job.isCancelled || job.isFailed) {
                root.removeView(view)
                view.destroy()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }
    handler.postDelayed(tick, 1000)
}

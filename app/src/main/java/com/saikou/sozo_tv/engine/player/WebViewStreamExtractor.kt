package com.saikou.sozo_tv.engine.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

data class ExtractedStream(
    val url: String,
    val headers: Map<String, String>,
    val playType: String,   // "hls" | "mp4"
)

/**
 * Headless-WebView stream sniffer — Kotlin port of soplay's webview_stream_extractor.dart.
 *
 * Some "Cloud" providers (e.g. asilmedia) resolve to an HTML content page, not a direct stream.
 * ExoPlayer can't play a page. This loads the page in a hidden WebView and intercepts its network
 * traffic (shouldInterceptRequest) to capture the first real .m3u8/.mp4/.mpd the page fires, plus
 * that request's headers — then playback uses THAT url. The page's own JS builds the signed stream
 * url, which is exactly what we need.
 */
object WebViewStreamExtractor {

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    // Direct-media extensions that mean "already a stream, no extraction needed".
    private val DIRECT_EXT = listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".webm")

    // What we sniff for in the WebView's network traffic (first match wins).
    private val STREAM_PATS = listOf(".m3u8", ".mpd", ".mp4")

    // Tracker/ad noise to swallow so the page settles faster.
    private val BLOCK = listOf(
        "mc.yandex.ru", "yastatic.net", "yandex.net", "google-analytics.com",
        "googletagmanager.com", "doubleclick.net", "googlesyndication.com", "adservice",
    )

    /** True when [url] is an HTML page we must open in a WebView to discover the real stream. */
    fun needsExtraction(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        // Already a direct stream/file → no extraction.
        if (DIRECT_EXT.any { path.contains(it) }) return false
        // Only clear content pages need sniffing. Extensionless/query endpoints are usually
        // already-direct streams — don't spend a 20s WebView on them (false positive penalty).
        return path.endsWith(".html") || path.endsWith(".htm") ||
            path.endsWith(".php") || path.endsWith("/")
    }

    /**
     * Loads [pageUrl] in a hidden WebView with [pageHeaders] (Referer/UA) and returns the first
     * .m3u8/.mp4/.mpd request it fires, merging that request's headers over the page headers.
     * Returns null on timeout. Suspends; the WebView is created/destroyed on the main thread.
     */
    suspend fun extract(
        context: Context,
        pageUrl: String,
        pageHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long = 20_000L,
    ): ExtractedStream? = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val captured = java.util.concurrent.atomic.AtomicBoolean(false)

        val cleanup = {
            main.post {
                runCatching { webView?.stopLoading(); webView?.destroy() }
                webView = null
            }
        }
        val uri = Uri.parse(pageUrl)
        val referer = pageHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
            ?: "${uri.scheme}://${uri.host}/"

        android.util.Log.i("StreamExtract", "sniffing page: $pageUrl")

        main.post {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = pageHeaders["User-Agent"] ?: MOBILE_UA
                visibility = View.GONE
            }
            webView = wv

            val timeout = Runnable {
                if (!captured.get()) {
                    android.util.Log.w("StreamExtract", "timeout, no stream captured for $pageUrl")
                    cleanup(); if (cont.isActive) cont.resume(null)
                }
            }
            main.postDelayed(timeout, timeoutMs)

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    val lower = url.lowercase()

                    if (BLOCK.any { lower.contains(it) }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (captured.get()) return null
                    val hit = STREAM_PATS.firstOrNull { lower.substringBefore('?').contains(it) } ?: return null
                    if (!captured.compareAndSet(false, true)) return null
                    main.removeCallbacks(timeout)

                    val headers = buildMap {
                        request.requestHeaders?.forEach { (k, v) -> put(k, v) }
                        pageHeaders.forEach { (k, v) -> putIfAbsent(k, v) }
                        putIfAbsent("User-Agent", MOBILE_UA)
                        putIfAbsent("Referer", referer)
                    }
                    val playType = if (lower.substringBefore('?').contains(".mp4")) "mp4" else "hls"
                    android.util.Log.i("StreamExtract", "captured $playType: $url")
                    cleanup()
                    if (cont.isActive) cont.resume(ExtractedStream(url, headers, playType))
                    // Swallow so a single-use token isn't consumed before ExoPlayer opens it.
                    return WebResourceResponse("application/octet-stream", null, ByteArrayInputStream(ByteArray(0)))
                }
            }

            wv.loadUrl(pageUrl, mapOf("Referer" to referer))
        }
        cont.invokeOnCancellation { cleanup() }
    }
}

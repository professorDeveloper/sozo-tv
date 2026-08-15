package com.saikou.sozo_tv.engine.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
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

    /**
     * How long the page is left running after the first stream url shows up, so its own retry /
     * cookie handshake can finish before the WebView is destroyed.
     */
    private const val SETTLE_MS = 3_000L

    // Direct-media extensions that mean "already a stream, no extraction needed".
    private val DIRECT_EXT = listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".webm")

    // What we sniff for in the WebView's network traffic (first match wins).
    private val STREAM_PATS = listOf(".m3u8", ".mpd", ".mp4")

    /**
     * Captured request headers that must NOT be replayed to ExoPlayer.
     *
     * `range` is the dangerous one: the page often probes a stream with a partial-range request,
     * and replaying that range makes ExoPlayer fetch a truncated manifest. `accept-encoding` lets
     * OkHttp negotiate its own compression - a stale value yields a body it will not decode.
     * The rest are connection-scoped or browser-only; `sec-fetch-*` in particular describes a
     * fetch that is not the one being made, and some CDNs reject the mismatch.
     */
    private val DROP_HEADERS = setOf(
        "range", "accept-encoding", "host", "content-length", "connection",
        "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "sec-fetch-user",
        "upgrade-insecure-requests", "x-requested-with",
    )

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
        // Set once the coroutine has been resumed, so neither the timeout nor the settle timer
        // can resume it twice.
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        // The FIRST stream url the page requests, which is the master playlist. Everything the
        // page asks for after it is a variant the master already points at.
        val hit = java.util.concurrent.atomic.AtomicReference<ExtractedStream?>(null)

        val cleanup = {
            main.post {
                runCatching { webView?.stopLoading(); webView?.destroy() }
                webView = null
            }
        }
        val uri = Uri.parse(pageUrl)
        // Referer for LOADING the embed: whatever sent us here (the catalog page).
        val referer = pageHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
            ?: "${uri.scheme}://${uri.host}/"

        // Referer for REPLAYING the stream: the embed page itself, because the embed's own JS is
        // what fetches the manifest. Reusing the incoming Referer sent the catalog's address on a
        // request the catalog never made, and CDNs that check it answered 403.
        val streamReferer = pageUrl
        // Origin carries no path and no trailing slash, unlike Referer.
        val streamOrigin = "${uri.scheme}://${uri.host}"

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
                if (settled.compareAndSet(false, true)) {
                    android.util.Log.w("StreamExtract", "timeout, no stream captured for $pageUrl")
                    cleanup(); if (cont.isActive) cont.resume(null)
                }
            }
            main.postDelayed(timeout, timeoutMs)

            /**
             * Fires [SETTLE_MS] after the first stream url appears, not immediately.
             *
             * The page is left running in between, on purpose. Hosts like juicycodes answer the
             * first manifest request with 403 + Set-Cookie and expect the retry to carry that
             * cookie; swallowing the request meant the WebView never completed that handshake and
             * ExoPlayer arrived with no session at all. Letting the page finish puts the cookies
             * in the shared CookieManager, which is the same jar OkHttp reads through
             * AndroidCookieJar - and the segments need that session just as much as the manifest
             * does, so there is no version of this that works without it.
             */
            val settle = Runnable {
                if (!settled.compareAndSet(false, true)) return@Runnable
                main.removeCallbacks(timeout)
                // Push the WebView's cookies to the store OkHttp reads before we tear it down.
                runCatching { CookieManager.getInstance().flush() }
                val found = hit.get()
                android.util.Log.i("StreamExtract", "settled on: ${found?.url}")
                cleanup()
                if (cont.isActive) cont.resume(found)
            }

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    val lower = url.lowercase()

                    if (BLOCK.any { lower.contains(it) }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (settled.get()) return null
                    STREAM_PATS.firstOrNull { lower.substringBefore('?').contains(it) } ?: return null

                    val headers = buildMap {
                        request.requestHeaders
                            ?.filterKeys { it.lowercase() !in DROP_HEADERS }
                            ?.forEach { (k, v) -> put(k, v) }
                        pageHeaders.forEach { (k, v) -> putIfAbsent(k, v) }
                        putIfAbsent("User-Agent", MOBILE_UA)
                        // put, not putIfAbsent: pageHeaders carries the Referer that led to the
                        // embed, and for the stream that value is simply wrong.
                        put("Referer", streamReferer)
                        // The page fetched this stream as a cross-origin XHR, so the browser also
                        // sent an Origin. WebResourceRequest.requestHeaders does not expose it -
                        // the network stack adds it after this callback - so replaying only a
                        // Referer produced a request no browser would ever make.
                        putIfAbsent("Origin", streamOrigin)
                    }
                    val playType = if (lower.substringBefore('?').contains(".mp4")) "mp4" else "hls"
                    android.util.Log.i("StreamExtract", "captured $playType: $url")
                    android.util.Log.i("StreamExtract", "replaying headers: ${headers.keys.sorted()}")

                    // First hit wins and starts the settle window; later ones are ignored.
                    //
                    // Taking the newest instead looked reasonable and was wrong: the page fetches
                    // the master, then the variants it references, and playing those decodes fine
                    // in the WebView. Handing ExoPlayer a variant made it ask for one without the
                    // master fetch that precedes it in a real session, and the CDN answered 403 -
                    // besides throwing away quality switching, which lives in the master.
                    if (hit.compareAndSet(null, ExtractedStream(url, headers, playType))) {
                        main.postDelayed(settle, SETTLE_MS)
                    }
                    // Return null: the request goes out for real. See [settle] - the cookie
                    // handshake this triggers is the whole point.
                    return null
                }
            }

            wv.loadUrl(pageUrl, mapOf("Referer" to referer))
        }
        cont.invokeOnCancellation { cleanup() }
    }
}

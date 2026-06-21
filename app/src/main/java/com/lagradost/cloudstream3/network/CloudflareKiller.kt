package com.lagradost.cloudstream3.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Clean-room, minimal stand-in for CloudStream's app-module `CloudflareKiller`.
 *
 * The real class solves Cloudflare's JS challenge with a headless WebView and
 * lives in the CloudStream **app** module — which we do not embed (we only ship
 * the `library` artifact). Plugins are compiled against the full app, so any
 * provider that references `CloudflareKiller` crashes at load/run time with
 * `NoClassDefFoundError` (e.g. GuardaSerieProvider in the logs) before it can do
 * anything.
 *
 * This passthrough satisfies the symbol so those plugins load and run. Providers
 * that don't actually sit behind Cloudflare work normally; a Cloudflare-gated one
 * simply receives the unsolved response instead of taking the whole app down.
 *
 * Implements `okhttp3.Interceptor` because plugins pass it as the `interceptor`
 * argument to `app.get(...)`. okhttp 5.x keeps the `okhttp3` package, so this
 * resolves against the runtime the `library` brings in.
 */
class CloudflareKiller : Interceptor {
    /** Real class exposes this; some plugins read it. Always empty here. */
    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request())

    /** Plugins occasionally fetch stored CF cookies; none are kept here. */
    fun getCookieHeaders(url: String): Headers = Headers.headersOf()

    companion object {
        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").mapNotNull {
                val k = it.substringBefore("=").trim()
                val v = it.substringAfter("=", "").trim()
                if (k.isEmpty()) null else k to v
            }.toMap()
    }
}

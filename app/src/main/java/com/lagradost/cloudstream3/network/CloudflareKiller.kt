package com.lagradost.cloudstream3.network

import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
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
 * This satisfies the symbol so those plugins load and run, and delegates the
 * actual work to [CloudflareInterceptor] — the WebView-based solver already in
 * the tree for the Aniyomi side. It stores the clearance in the shared WebView
 * cookie jar, so the player replays it on the stream request. Previously this
 * was a passthrough and a Cloudflare-gated provider simply received the
 * unsolved 403.
 *
 * Implements `okhttp3.Interceptor` because plugins pass it as the `interceptor`
 * argument to `app.get(...)`. okhttp 5.x keeps the `okhttp3` package, so this
 * resolves against the runtime the `library` brings in.
 */
class CloudflareKiller : Interceptor {
    /** Real class exposes this; some plugins read it. */
    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    private val cookieJar = AndroidCookieJar()

    // Built lazily: plugins construct this at load time, before the WebView is
    // usable, and a failure here must not take the provider down with it.
    private val solver: Interceptor? by lazy {
        runCatching {
            CloudflareInterceptor(
                MyApp.context.applicationContext,
                cookieJar,
            ) { SOZO_USER_AGENT }
        }.getOrNull()
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        solver?.intercept(chain) ?: chain.proceed(chain.request())

    /** Plugins fetch stored CF cookies to build their own requests. */
    fun getCookieHeaders(url: String): Headers {
        val cookies = runCatching {
            cookieJar.get(url.toHttpUrl())
        }.getOrDefault(emptyList())
        if (cookies.isEmpty()) return Headers.headersOf()
        savedCookies[url] = cookies.associate { it.name to it.value }
        return Headers.headersOf(
            "Cookie",
            cookies.joinToString("; ") { "${it.name}=${it.value}" },
        )
    }

    companion object {
        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").mapNotNull {
                val k = it.substringBefore("=").trim()
                val v = it.substringAfter("=", "").trim()
                if (k.isEmpty()) null else k to v
            }.toMap()
    }
}

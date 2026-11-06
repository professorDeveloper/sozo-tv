package com.saikou.sozo_tv.engine.player

import java.net.URI
import java.net.URLEncoder

/** Shared by WebView capture and the proxy; no Android runtime required. */
object StreamRequestRules {
    private val dropped = setOf("range", "accept-encoding", "host", "content-length", "connection",
        "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "sec-fetch-user",
        "upgrade-insecure-requests", "x-requested-with")

    fun headers(page: Map<String, String>, captured: Map<String, String>, frameUrl: String,
                userAgent: String, cookie: String? = null): Map<String, String> {
        val out = linkedMapOf<String, String>()
        fun put(name: String, value: String) {
            if (name.lowercase() in dropped) return
            out.keys.firstOrNull { it.equals(name, true) }?.let { out.remove(it) }
            out[name] = value
        }
        // A page's Referer/Origin identifies its parent, not the frame requesting media.
        page.filterKeys { !it.equals("Referer", true) && !it.equals("Origin", true) }
            .forEach { (k, v) -> put(k, v) }
        val frame = URI(frameUrl)
        put("Referer", frameUrl)
        put("Origin", "${frame.scheme}://${frame.rawAuthority}")
        put("User-Agent", out.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: userAgent)
        captured.forEach { (k, v) -> put(k, v) }
        if (!cookie.isNullOrBlank() && out.keys.none { it.equals("Cookie", true) }) put("Cookie", cookie)
        return out
    }

    fun playType(url: String): String = when {
        url.substringBefore('?').contains(".mpd", true) -> "dash"
        url.substringBefore('?').contains(".mp4", true) -> "mp4"
        else -> "hls"
    }

    fun needsManifestProxy(url: String): Boolean = runCatching {
        val path = URI(url).path.orEmpty()
        path.startsWith("/stream/") && path.endsWith(".m3u8")
    }.getOrDefault(false)

    fun segmentRelay(url: String, endpoint: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443) ||
            !Regex("p(?:16|19)-ad-site-sign-sg\\.tiktokcdn\\.com").matches(uri.host.orEmpty()) ||
            !uri.path.startsWith("/ad-site-i18n-sg/") || !uri.path.endsWith(".image")) null
        else "$endpoint?url=${URLEncoder.encode(url, "UTF-8")}"
    }.getOrNull()

    fun resolve(base: String, relative: String): String = URI(base.replace(" ", "%20"))
        .resolve(relative.trim().replace(" ", "%20")).normalize().toASCIIString()
}

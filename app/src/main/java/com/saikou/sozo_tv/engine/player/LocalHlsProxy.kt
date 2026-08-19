package com.saikou.sozo_tv.engine.player

import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process loopback HLS proxy — Kotlin/NanoHTTPD port of soplay's local_hls_proxy.dart.
 *
 * Upstream HLS/DASH hosts that bind their signed manifest URLs to the client IP/cookies (e.g.
 * uzmovi/uzdown) reject ExoPlayer when the manifest was solved by one socket and the player opens
 * another. Routing the player through this loopback server forces every upstream socket — manifest,
 * variant playlists, segments, keys — through the same OkHttp client (which shares the WebView
 * cookie jar), so IP + cookies + signed headers match end to end.
 *
 * Activates only when a source arrives with `useLocalProxy: true`. For uzmovi it also applies the
 * `uzmovi-rc4-v1` request transform (RC4-signed X-ATT-DeviceId / X-Match / X-Path + randomized URL).
 */
class LocalHlsProxy(private val client: OkHttpClient) {

    private var server: ProxyServer? = null
    private var port: Int = 0
    private val sessions = ConcurrentHashMap<String, Session>()
    private val rng = SecureRandom()

    private inner class ProxyServer : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(session: IHTTPSession): Response =
            runCatching { handle(session) }.getOrElse { error(502) }
    }

    @Synchronized
    private fun ensureStarted() {
        if (server != null) return
        val s = ProxyServer()
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = s
        port = s.listeningPort
        android.util.Log.i("HlsProxy", "listening on 127.0.0.1:$port")
    }

    /** Register [upstreamUrl] and return the loopback URL ExoPlayer should open instead. */
    fun register(
        upstreamUrl: String,
        headers: Map<String, String>,
        localProxy: JSONObject,
        requestTransform: JSONObject,
    ): String {
        ensureStarted()
        val id = randomId()
        val parsed = URI(upstreamUrl)
        val fullPath = parsed.rawPath ?: "/"   // encoded — used to build the loopback URL below
        // basePath must be DECODED to match NanoHTTPD's decoded session.uri in handle(). Using the
        // encoded rawPath here made startsWith() fail on paths with %20/spaces, doubling the path
        // and corrupting the RC4 X-Match signature (uzdown then returns an empty 200 body).
        val decodedPath = parsed.path ?: "/"
        val ls = decodedPath.lastIndexOf('/')
        val basePath = if (ls >= 0) decodedPath.substring(0, ls) else ""
        sessions[id] = Session(
            origin = "${parsed.scheme}://${parsed.authority}",
            basePath = basePath,
            cdnQuery = parsed.rawQuery ?: "",
            headers = HashMap(headers),
            transform = RequestTransform.fromMaps(localProxy, requestTransform),
        )
        val query = parsed.rawQuery?.let { "?$it" } ?: ""
        return "http://127.0.0.1:$port/hls/$id$fullPath$query"
    }

    private fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val path = session.uri ?: ""
        val match = Regex("^/hls/([a-f0-9]+)(/.*)$").find(path) ?: return error(404)
        val sid = match.groupValues[1]
        var cdnPath = match.groupValues[2]
        val sess = sessions[sid] ?: return error(410)

        var origin = sess.origin
        val hMatch = Regex("^/_h/([A-Za-z0-9_-]+)(/.*)$").find(cdnPath)
        if (hMatch != null) {
            try {
                origin = "https://${b64UrlDecode(hMatch.groupValues[1])}"
                cdnPath = hMatch.groupValues[2]
            } catch (_: Exception) {
                return error(400)
            }
        }

        val resolved = if (cdnPath.startsWith(sess.basePath) || origin != sess.origin) cdnPath
        else "${sess.basePath}$cdnPath"
        val incomingQuery = session.queryParameterString
        val queryString = when {
            !incomingQuery.isNullOrEmpty() -> "?$incomingQuery"
            origin == sess.origin && sess.cdnQuery.isNotEmpty() -> "?${sess.cdnQuery}"
            else -> ""
        }
        val upstreamUrl = "$origin$resolved$queryString"

        val upstreamHeaders = LinkedHashMap<String, String>()
        val keepOrigin = sess.transform?.keepsOriginHeaders == true
        sess.headers.forEach { (k, v) ->
            val lower = k.lowercase()
            if (lower == "host" || lower == "content-length" ||
                (!keepOrigin && (lower == "origin" || lower == "referer"))
            ) return@forEach
            upstreamHeaders[k] = v
        }
        session.headers["range"]?.takeIf { it.isNotEmpty() }?.let { upstreamHeaders["Range"] = it }
        session.headers["if-range"]?.takeIf { it.isNotEmpty() }?.let { upstreamHeaders["If-Range"] = it }
        upstreamHeaders.putIfAbsent("Accept-Encoding", "identity")
        if (upstreamHeaders.keys.none { it.equals("User-Agent", true) }) {
            upstreamHeaders["User-Agent"] = SOZO_USER_AGENT
        }

        val transformed = sess.transform?.apply(origin, resolved, upstreamHeaders)
        val requestUrl = transformed?.url ?: upstreamUrl
        val requestHeaders = transformed?.headers ?: upstreamHeaders

        val reqBuilder = Request.Builder().url(requestUrl)
        requestHeaders.forEach { (k, v) -> runCatching { reqBuilder.header(k, v) } }
        val resp = client.newCall(reqBuilder.build()).execute()
        val status = resp.code
        if (status >= 400) {
            android.util.Log.w("HlsProxy", "upstream $status: $upstreamUrl")
            resp.close()
            return error(status)
        }
        val contentType = (resp.header("content-type") ?: "").lowercase()
        val isManifest = contentType.contains("mpegurl") || contentType.contains("m3u8") ||
            resolved.endsWith(".m3u8")

        if (isManifest) {
            val bytes = resp.body?.bytes() ?: ByteArray(0)   // reads + closes
            if (origin == sess.origin) {
                val ls = resolved.lastIndexOf('/')
                if (ls >= 0) sess.basePath = resolved.substring(0, ls)
            }
            val text = String(bytes, Charsets.UTF_8)
            val rewritten = runCatching { rewriteM3u8(text, "/hls/$sid", sess.origin, upstreamUrl) }
                .getOrDefault(text)
            return NanoHTTPD.newFixedLengthResponse(
                statusOf(200), "application/vnd.apple.mpegurl", rewritten,
            )
        }

        val len = resp.body?.contentLength() ?: -1L
        val stream = resp.body?.byteStream()   // closed by NanoHTTPD when done → releases the response
        val out = if (len >= 0) {
            NanoHTTPD.newFixedLengthResponse(statusOf(status), contentType.ifEmpty { "application/octet-stream" }, stream, len)
        } else {
            NanoHTTPD.newChunkedResponse(statusOf(status), contentType.ifEmpty { "application/octet-stream" }, stream)
        }
        copyHeader(resp, out, "content-range")
        copyHeader(resp, out, "accept-ranges")
        copyHeader(resp, out, "cache-control")
        copyHeader(resp, out, "etag")
        copyHeader(resp, out, "last-modified")
        return out
    }

    private fun copyHeader(resp: okhttp3.Response, out: NanoHTTPD.Response, name: String) {
        resp.header(name)?.takeIf { it.isNotEmpty() }?.let { out.addHeader(name, it) }
    }

    private fun error(code: Int): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(statusOf(code), "text/plain", "")

    private fun statusOf(code: Int): NanoHTTPD.Response.IStatus =
        NanoHTTPD.Response.Status.values().firstOrNull { it.requestStatus == code }
            ?: object : NanoHTTPD.Response.IStatus {
                override fun getDescription(): String = "$code"
                override fun getRequestStatus(): Int = code
            }

    private fun rewriteM3u8(content: String, base: String, sessionOrigin: String, upstreamUrl: String): String {
        // String-based (NOT java.net.URI, which throws on the raw spaces uzmovi segment paths
        // contain). Encodes spaces in the output so the rewritten loopback URLs stay valid.
        val sessionAuthority = sessionOrigin.substringAfter("://")
        val upstreamDir = upstreamUrl.substringBefore('?').substringBeforeLast('/', upstreamUrl.substringBefore('?'))

        fun enc(s: String) = s.replace(" ", "%20")

        fun rewriteUrl(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return raw
            return when {
                t.startsWith("http://", true) || t.startsWith("https://", true) -> {
                    val schemeEnd = t.indexOf("://") + 3
                    val slash = t.indexOf('/', schemeEnd).let { if (it < 0) t.length else it }
                    val authority = t.substring(schemeEnd, slash)
                    val rest = enc(t.substring(slash))
                    if (authority == sessionAuthority) "$base$rest"
                    else "$base/_h/${b64UrlEncode(authority)}$rest"
                }
                t.startsWith("//") -> {
                    val slash = t.indexOf('/', 2).let { if (it < 0) t.length else it }
                    val authority = t.substring(2, slash)
                    val rest = enc(t.substring(slash))
                    if (authority == sessionAuthority) "$base$rest"
                    else "$base/_h/${b64UrlEncode(authority)}$rest"
                }
                t.startsWith("/") -> "$base${enc(t)}"                 // absolute path, same authority
                else -> {                                            // relative to the manifest dir
                    val abs = "$upstreamDir/$t"
                    val schemeEnd = abs.indexOf("://").let { if (it < 0) 0 else it + 3 }
                    val slash = abs.indexOf('/', schemeEnd).let { if (it < 0) abs.length else it }
                    "$base${enc(abs.substring(slash))}"
                }
            }
        }

        val keyAttr = Regex("URI=\"([^\"]+)\"")
        return content.split("\n").joinToString("\n") { line ->
            val stripped = line.trim()
            when {
                stripped.isEmpty() -> line
                stripped.startsWith("#") -> keyAttr.replace(line) { m -> "URI=\"${rewriteUrl(m.groupValues[1])}\"" }
                else -> rewriteUrl(line)
            }
        }
    }

    private fun randomId(): String {
        val hex = "0123456789abcdef"
        return (0 until 24).map { hex[rng.nextInt(16)] }.joinToString("")
    }

    private fun b64UrlEncode(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun b64UrlDecode(s: String): String =
        String(Base64.decode(s, Base64.URL_SAFE), Charsets.UTF_8)

    private class Session(
        val origin: String,
        @Volatile var basePath: String,
        val cdnQuery: String,
        val headers: Map<String, String>,
        val transform: RequestTransform?,
    )

    private class TransformedRequest(val url: String, val headers: Map<String, String>)

    /** uzmovi-rc4-v1: RC4-signed headers + randomized upstream URL (the real path travels in X-Match). */
    private class RequestTransform private constructor(
        private val pageHost: String,
        private val deviceId: String,
        private val firstPathLength: Int,
        private val secondPathLength: Int,
        private val extension: String,
        private val deviceHeader: String,
        private val matchHeader: String,
        private val pathHeader: String,
        private val targetHost: String?,
    ) {
        val keepsOriginHeaders: Boolean get() = true

        fun apply(origin: String, logicalPath: String, headers: Map<String, String>): TransformedRequest? {
            val host = runCatching { URI(origin).host?.lowercase() }.getOrNull() ?: return null
            val expected = targetHost?.lowercase()
            val isUzdown = host.endsWith("uzdown.space")
            val isTarget = if (!expected.isNullOrEmpty()) host == expected || isUzdown else isUzdown
            if (!isTarget) return null

            val requestUrl = "$origin/${randomToken(firstPathLength)}/${randomToken(secondPathLength)}$extension"
            val now = System.currentTimeMillis()
            val matchPayload = JSONObject().put("path", logicalPath).put("time", now).toString()
            val next = LinkedHashMap(headers)
            next[deviceHeader] = deviceId
            next[matchHeader] = Base64.encodeToString(
                rc4(pageHost.toByteArray(Charsets.UTF_8), matchPayload.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
            next[pathHeader] = randomToken(40)
            return TransformedRequest(requestUrl, next)
        }

        companion object {
            private const val UZMOVI_TYPE = "uzmovi-rc4-v1"
            private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            private val rng = SecureRandom()

            fun fromMaps(localProxy: JSONObject, requestTransform: JSONObject): RequestTransform? {
                val type = str(requestTransform, "type") ?: str(localProxy, "transform") ?: str(localProxy, "type")
                if (type != UZMOVI_TYPE) return null
                val randomPath = requestTransform.optJSONObject("randomPath") ?: localProxy.optJSONObject("randomPath")
                val headerNames = requestTransform.optJSONObject("headerNames") ?: localProxy.optJSONObject("headerNames")
                val pageHost = str(requestTransform, "pageHost") ?: str(localProxy, "pageHost") ?: "uzmovi.net"
                val menuData = str(requestTransform, "menuData") ?: str(localProxy, "menuData") ?: return null
                val deviceId = buildDeviceId(menuData) ?: return null
                return RequestTransform(
                    pageHost = pageHost,
                    deviceId = deviceId,
                    firstPathLength = intOf(randomPath, "first") ?: 30,
                    secondPathLength = intOf(randomPath, "second") ?: 10,
                    extension = str(randomPath, "extension") ?: ".mpd",
                    deviceHeader = str(headerNames, "deviceId") ?: "X-ATT-DeviceId",
                    matchHeader = str(headerNames, "match") ?: "X-Match",
                    pathHeader = str(headerNames, "path") ?: "X-Path",
                    targetHost = str(localProxy, "targetHost"),
                )
            }

            private fun randomToken(length: Int): String {
                val n = if (length <= 0) 1 else length
                return (0 until n).map { CHARS[rng.nextInt(CHARS.length)] }.joinToString("")
            }

            private fun buildDeviceId(menuData: String): String? = runCatching {
                Base64.encodeToString(rc4("movie".toByteArray(Charsets.UTF_8), decodeBase64Flexible(menuData)), Base64.NO_WRAP)
            }.getOrNull()

            private fun rc4(key: ByteArray, input: ByteArray): ByteArray {
                val s = IntArray(256) { it }
                var j = 0
                for (i in 0 until 256) {
                    j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
                    val t = s[i]; s[i] = s[j]; s[j] = t
                }
                val out = ByteArray(input.size)
                var i = 0; j = 0
                for (n in input.indices) {
                    i = (i + 1) and 0xff
                    j = (j + s[i]) and 0xff
                    val t = s[i]; s[i] = s[j]; s[j] = t
                    out[n] = (input[n].toInt() xor s[(s[i] + s[j]) and 0xff]).toByte()
                }
                return out
            }

            private fun decodeBase64Flexible(value: String): ByteArray {
                val normalized = value.trim().replace('-', '+').replace('_', '/')
                val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
                return Base64.decode(padded, Base64.DEFAULT)
            }

            private fun str(o: JSONObject?, key: String): String? =
                o?.optString(key)?.takeIf { it.isNotEmpty() }

            private fun intOf(o: JSONObject?, key: String): Int? =
                o?.let { if (it.has(key)) it.optInt(key) else null }
        }
    }
}

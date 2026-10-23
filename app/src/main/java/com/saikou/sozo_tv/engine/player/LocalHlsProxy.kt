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

    companion object {
        /** Stable cache identity across local proxy restarts; never decode arbitrary remote URLs. */
        fun canonicalUpstreamUrl(url: String): String? = runCatching {
            val uri = URI(url)
            if (uri.host !in setOf("127.0.0.1", "localhost", "::1")) return null
            val parts = uri.path.split('/').drop(1)
            if (parts.size < 3 || !parts[1].matches(Regex("[a-f0-9]+"))) return null
            fun decode(value: String) = String(Base64.decode(value, Base64.URL_SAFE), Charsets.UTF_8)
            val target = when (parts[0]) {
                "hls" -> if (parts.size == 3) decode(parts[2]) else return null
                "dash" -> parts.drop(2).mapIndexed { index, value -> if (index % 2 == 0) decode(value) else value }.joinToString("")
                else -> return null
            }
            val parsed = URI(target)
            target.takeIf { parsed.scheme in setOf("https", "http") && parsed.host != null }
        }.getOrNull()
    }

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

    /**
     * Shut the loopback server and forget every session. Each player screen owns one proxy; left
     * running, every episode opened leaked a listening socket and a NanoHTTPD thread. A later
     * [register] starts it again.
     */
    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        port = 0
        sessions.clear()
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
        require(parsed.scheme in setOf("http", "https") && parsed.host != null)
        sessions[id] = Session(headers = HashMap(headers),
            transform = RequestTransform.fromMaps(localProxy, requestTransform))
        return "http://127.0.0.1:$port/hls/$id/${b64UrlEncode(upstreamUrl)}"
    }

    private fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET && session.method != NanoHTTPD.Method.HEAD) return error(405)
        val path = session.uri.orEmpty()
        val parts = path.split('/').drop(1)
        if (parts.size < 3 || parts[0] !in setOf("hls", "dash")) return error(404)
        val sid = parts[1]
        val sess = sessions[sid] ?: return error(410)
        val upstreamUrl = runCatching {
            if (parts[0] == "hls") b64UrlDecode(parts[2])
            else parts.drop(2).mapIndexed { i, value ->
                if (i % 2 == 0) b64UrlDecode(value) else value
            }.joinToString("")
        }.getOrNull() ?: return error(400)
        val uri = runCatching { URI(upstreamUrl) }.getOrNull() ?: return error(400)
        if (uri.scheme !in setOf("http", "https") || uri.host == null) return error(400)
        val origin = "${uri.scheme}://${uri.rawAuthority}"
        val upstreamHeaders = LinkedHashMap<String, String>()
        sess.headers.filterKeys { it.lowercase() !in setOf("host", "content-length", "accept-encoding") }
            .forEach { (k, v) -> upstreamHeaders[k] = v }
        session.headers["range"]?.let { upstreamHeaders["Range"] = it }
        session.headers["if-range"]?.let { upstreamHeaders["If-Range"] = it }
        upstreamHeaders["Accept-Encoding"] = "identity"
        if (upstreamHeaders.keys.none { it.equals("User-Agent", true) }) upstreamHeaders["User-Agent"] = SOZO_USER_AGENT
        val transformed = sess.transform?.apply(origin, uri.path, upstreamHeaders)
        val relay = StreamRequestRules.segmentRelay(upstreamUrl,
            com.saikou.sozo_tv.BuildConfig.SOZO_API_BASE_URL.trimEnd('/') + "/contents/media-segment")
        val requestUrl = relay ?: transformed?.url ?: upstreamUrl
        // Never leak a provider's cookies or authorization into our segment relay.
        val requestHeaders = if (relay != null) emptyMap() else transformed?.headers ?: upstreamHeaders
        val request = Request.Builder().url(requestUrl).apply {
            requestHeaders.forEach { (k, v) -> header(k, v) }
            if (session.method == NanoHTTPD.Method.HEAD) head()
        }.build()
        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) { val code = resp.code; resp.close(); return error(code) }
        val contentType = resp.header("content-type").orEmpty().lowercase()
        val effectiveUrl = resp.request.url.toString()
        val isManifest = contentType.contains("mpegurl") || uri.path.endsWith(".m3u8") ||
            resp.body?.source()?.peek()?.let { it.request(7) && it.readUtf8(7) == "#EXTM3U" } == true
        val isDash = contentType.contains("dash+xml") || uri.path.endsWith(".mpd")
        if ((isManifest || isDash) && session.method != NanoHTTPD.Method.HEAD) {
            val text = resp.use { it.body?.string().orEmpty() }
            val rewritten = if (isDash) DashManifestRewriter.rewrite(text, effectiveUrl) { target ->
                // Literal chunks are opaque; Media3 must still see and expand DASH $tokens$.
                val token = Regex("\\$[^$]+\\$")
                if (!token.containsMatchIn(target)) "/hls/$sid/${b64UrlEncode(target)}"
                else {
                    var end = 0
                    val chunks = mutableListOf<String>()
                    token.findAll(target).forEach { match ->
                        chunks.add(b64UrlEncode(target.substring(end, match.range.first)))
                        chunks.add(match.value)
                        end = match.range.last + 1
                    }
                    chunks.add(b64UrlEncode(target.substring(end)))
                    "/dash/$sid/" + chunks.joinToString("/")
                }
            } else {
                if (!text.trimStart().startsWith("#EXTM3U")) return error(502)
                rewriteM3u8(text, "/hls/$sid", effectiveUrl)
            }
            return NanoHTTPD.newFixedLengthResponse(statusOf(200),
                if (isDash) "application/dash+xml" else "application/vnd.apple.mpegurl", rewritten)
        }
        val body = resp.body ?: run { resp.close(); return error(502) }
        val len = body.contentLength()
        val out = if (len >= 0) NanoHTTPD.newFixedLengthResponse(statusOf(resp.code),
            contentType.ifEmpty { "application/octet-stream" }, body.byteStream(), len)
        else NanoHTTPD.newChunkedResponse(statusOf(resp.code), contentType.ifEmpty { "application/octet-stream" }, body.byteStream())
        listOf("content-range", "accept-ranges", "cache-control", "etag", "last-modified").forEach { copyHeader(resp, out, it) }
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

    private fun rewriteM3u8(content: String, base: String, upstreamUrl: String): String {
        fun rewrite(raw: String): String {
            val absolute = StreamRequestRules.resolve(upstreamUrl, raw)
            if (!absolute.startsWith("http://") && !absolute.startsWith("https://")) return raw
            return "$base/${b64UrlEncode(absolute)}"
        }
        val attributes = Regex("URI=\"([^\"]+)\"")
        return content.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.trimStart().startsWith("#") -> attributes.replace(line) { "URI=\"${rewrite(it.groupValues[1])}\"" }
                else -> rewrite(line.trim())
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

    private class Session(val headers: Map<String, String>, val transform: RequestTransform?)

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

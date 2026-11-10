package com.saikou.sozo_tv.engine.cloudstream

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Plays a CloudStream link through the plugin's own [Interceptor].
 *
 * `MainAPI.getVideoInterceptor` is how a plugin keeps a stream playable after
 * it has handed the link over: NetMirror adds the hotlink cookie and Origin to
 * every segment request, KissKH decrypts its `.txt` subtitles on the way in.
 * CloudStream's player builds its data source on an OkHttp client with that
 * interceptor added. Ours plays through ExoPlayer or mpv on the Dart side,
 * which cannot run Kotlin, so without this those sources opened the playlist
 * and then failed on the first segment, or showed ciphertext as captions.
 *
 * So a link that comes with an interceptor is served from here instead:
 * `http://127.0.0.1:<port>/p/<token>/<scheme>/<authority>/<path>?<query>`.
 * The path keeps the upstream structure, so relative URIs inside a playlist
 * resolve back into the proxy by themselves; absolute ones are rewritten in
 * HLS playlists and DASH manifests. Bodies stream through with their status,
 * length and range, so seeking works.
 *
 * Bound to loopback only: it fetches with a plugin's cookies and must not be
 * reachable from the network.
 */
object CsStreamProxy {
    private const val TAG = "CsStreamProxy"
    private const val MAX_ENTRIES = 256
    private const val ENTRY_TTL_MS = 6 * 60 * 60 * 1000L

    private class Entry(
        val client: OkHttpClient,
        val headers: Map<String, String>,
        val createdAt: Long,
    )

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) =
            size > MAX_ENTRIES
    }
    private val random = SecureRandom()

    @Volatile
    private var server: Server? = null

    /**
     * Registers a stream and returns the token its URLs carry. [base] is the
     * client the plugin's own requests use, so cookies, DNS and timeouts match
     * what produced the link.
     */
    fun register(base: OkHttpClient, interceptor: Interceptor, headers: Map<String, String>): String? {
        val srv = ensureServer() ?: return null
        val client = base.newBuilder()
            .addInterceptor(interceptor)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val token = ByteArray(12).also(random::nextBytes)
            .joinToString("") { "%02x".format(it) }
        synchronized(entries) { entries[token] = Entry(client, headers, System.currentTimeMillis()) }
        Log.i(TAG, "registered stream on port ${srv.listeningPort}")
        return token
    }

    /** The loopback URL for [target] under [token]; null when [target] is not http(s). */
    fun urlFor(token: String, target: String, headers: Map<String, String> = emptyMap()): String? {
        if (headers.isNotEmpty()) {
            val parent = entry(token) ?: return null
            val child = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            synchronized(entries) { entries[child] = Entry(parent.client, parent.headers + headers, System.currentTimeMillis()) }
            return urlFor(child, target)
        }
        val port = server?.listeningPort ?: return null
        val uri = try { URI(target) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val authority = uri.rawAuthority ?: return null
        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "http://127.0.0.1:$port/p/$token/$scheme/$authority$path$query"
    }

    private fun ensureServer(): Server? {
        server?.let { if (it.isAlive) return it }
        synchronized(this) {
            server?.let { if (it.isAlive) return it }
            return try {
                Server().also {
                    it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                    server = it
                }
            } catch (t: Throwable) {
                Log.e(TAG, "could not start: ${t.message}")
                null
            }
        }
    }

    private fun entry(token: String): Entry? = synchronized(entries) {
        val e = entries[token] ?: return null
        if (System.currentTimeMillis() - e.createdAt > ENTRY_TTL_MS) {
            entries.remove(token)
            return null
        }
        e
    }

    private class Server : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(session: IHTTPSession): Response {
            // /p/<token>/<scheme>/<authority>/<path…>
            val parts = session.uri.removePrefix("/").split('/', limit = 5)
            if (parts.size < 4 || parts[0] != "p") return notFound()
            val token = parts[1]
            val e = entry(token) ?: return notFound()
            val scheme = parts[2]
            if (scheme != "http" && scheme != "https") return notFound()
            val authority = parts[3]
            val rest = if (parts.size == 5) "/" + parts[4] else "/"
            val query = session.queryParameterString?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
            val target = "$scheme://$authority$rest$query"

            val req = Request.Builder().url(target).get()
            e.headers.forEach { (k, v) -> req.header(k, v) }
            session.headers["range"]?.let { req.header("Range", it) }

            return try {
                val resp = e.client.newCall(req.build()).execute()
                val body = resp.body
                val type = resp.header("Content-Type") ?: ""
                val status = statusOf(resp.code)
                if (isPlaylist(target, type)) {
                    val text = body.string()
                    val out = when {
                        text.trimStart().startsWith("#EXTM3U") -> rewriteHls(text, resp.request.url.toString(), token)
                        text.contains("<MPD") -> rewriteDash(text, resp.request.url.toString(), token)
                        else -> text
                    }
                    newFixedLengthResponse(status, type.ifEmpty { "application/vnd.apple.mpegurl" }, out)
                        .also { it.addHeader("Access-Control-Allow-Origin", "*") }
                } else {
                    val length = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val stream = body.byteStream()
                    val r = if (length >= 0) {
                        newFixedLengthResponse(status, type.ifEmpty { "application/octet-stream" }, stream, length)
                    } else {
                        newChunkedResponse(status, type.ifEmpty { "application/octet-stream" }, stream)
                    }
                    resp.header("Content-Range")?.let { r.addHeader("Content-Range", it) }
                    resp.header("Accept-Ranges")?.let { r.addHeader("Accept-Ranges", it) }
                    r
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetch failed: ${t.javaClass.simpleName}: ${t.message}")
                newFixedLengthResponse(statusOf(502), "text/plain", "upstream failed")
            }
        }

        private fun notFound() =
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")

        /** The upstream status as it was; NanoHTTPD's enum lacks several of them. */
        private fun statusOf(code: Int): Response.IStatus =
            Response.Status.lookup(code) ?: object : Response.IStatus {
                override fun getDescription() = "$code"
                override fun getRequestStatus() = code
            }
    }

    private fun isPlaylist(url: String, contentType: String): Boolean {
        val t = contentType.lowercase()
        if (t.contains("mpegurl") || t.contains("dash+xml")) return true
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")
    }

    /** Absolute URIs in an HLS playlist, as lines and as `URI="…"`, into the proxy. */
    internal fun rewriteHls(text: String, base: String, token: String): String {
        val baseUri = try { URI(base) } catch (_: Exception) { return text }
        val attr = Regex("URI=\"([^\"]+)\"")
        return text.lineSequence().joinToString("\n") { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> raw
                line.startsWith("#") -> attr.replace(raw) { m ->
                    val abs = absolute(baseUri, m.groupValues[1])
                    "URI=\"${abs?.let { urlFor(token, it) } ?: m.groupValues[1]}\""
                }
                else -> absolute(baseUri, line)?.let { urlFor(token, it) } ?: raw
            }
        }
    }

    /** Absolute URLs in the parts of a DASH manifest that locate media. */
    internal fun rewriteDash(text: String, base: String, token: String): String {
        val baseUri = try { URI(base) } catch (_: Exception) { return text }
        val baseUrlTag = Regex("(<BaseURL[^>]*>)([^<]+)(</BaseURL>)")
        val mediaAttrs = Regex("\\b(media|initialization|sourceURL)=\"(https?://[^\"]+)\"")
        val withBase = baseUrlTag.replace(text) { m ->
            val abs = absolute(baseUri, m.groupValues[2].trim())
            m.groupValues[1] + (abs?.let { urlFor(token, it) } ?: m.groupValues[2]) + m.groupValues[3]
        }
        return mediaAttrs.replace(withBase) { m ->
            "${m.groupValues[1]}=\"${urlFor(token, m.groupValues[2]) ?: m.groupValues[2]}\""
        }
    }

    /** [ref] made absolute only when it names its own host; relative refs stay relative. */
    private fun absolute(base: URI, ref: String): String? {
        return try { base.resolve(ref).takeIf { it.scheme == "http" || it.scheme == "https" }?.toString() }
        catch (_: Exception) { null }
    }
}

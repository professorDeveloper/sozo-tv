package com.saikou.sozo_tv.engine.torrent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import torrServer.TorrServer

/** Process-wide native server, with bounded disk cache and cancellable metadata waiting. */
class TorrentEngine(context: Context) {
    private val context = context.applicationContext
    data class ResolvedTorrent(val url: String, val hash: String)
    companion object {
        private val startLock = Mutex()
        @Volatile private var port = 0
        private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    }

    private suspend fun ensureStarted(): Int = startLock.withLock {
        if (port > 0) return@withLock port
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "torrent_tmp")
            dir.deleteRecursively()
            check(dir.mkdirs() || dir.isDirectory) { "Could not create torrent cache" }
            go.Seq.load()
            val started = TorrServer.startTorrentServer(dir.absolutePath, 0L).toInt()
            check(started > 0) { "Torrent server did not start" }
            port = started
            // These are the same cache settings as mobile. Never stop Go via os.Exit.
            runCatching {
                val settings = post("settings", JSONObject().put("action", "get"))
                settings.put("UseDisk", true).put("CacheSize", 128 * 1024 * 1024)
                    .put("PreloadCache", 12).put("ConnectionsLimit", 100)
                    .put("RemoveCacheOnDrop", true).put("TorrentsSavePath", dir.absolutePath)
                post("settings", JSONObject().put("action", "set").put("sets", settings))
            }
            started
        }
    }

    suspend fun resolve(link: String, episodeNumber: Int? = null): ResolvedTorrent = withContext(Dispatchers.IO) {
        val localPort = ensureStarted()
        val added = post("torrents", JSONObject().put("action", "add").put("link", link).put("save_to_db", false))
        val hash = added.optString("hash").also { check(it.isNotBlank()) { "Torrent server rejected the link" } }
        try {
            val file = withTimeout(60_000) {
                var status = added
                while (true) {
                    val files = TorrentFiles.parse(status.optJSONArray("file_stats"))
                    if ((status.optJSONArray("file_stats")?.length() ?: 0) > 0) return@withTimeout TorrentFiles.select(files, episodeNumber)
                    check(status.optInt("stat", -1) != 4) { "Torrent closed before metadata arrived" }
                    delay(700)
                    status = post("torrents", JSONObject().put("action", "get").put("hash", hash))
                }
                @Suppress("UNREACHABLE_CODE") error("No torrent metadata")
            }
            val name = URLEncoder.encode(file.path.substringAfterLast('/'), "UTF-8").replace("+", "%20")
            val encodedHash = URLEncoder.encode(hash, "UTF-8")
            ResolvedTorrent("http://127.0.0.1:$localPort/stream/$name?link=$encodedHash&index=${file.id}&play", hash)
        } catch (t: Throwable) {
            withContext(kotlinx.coroutines.NonCancellable) { drop(hash) }
            throw t
        }
    }

    suspend fun drop(hash: String) = withContext(Dispatchers.IO) {
        if (port > 0) runCatching { post("torrents", JSONObject().put("action", "drop").put("hash", hash)) }
        Unit
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder().url("http://127.0.0.1:$port/$path")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Torrent server returned HTTP ${response.code}" }
            response.body?.string()?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        }
    }
}

internal object TorrentFiles {
    data class Entry(val id: Int, val path: String, val size: Long)
    private val formats = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts", "m2ts", "flv", "wmv", "ogv")
    fun parse(arr: JSONArray?): List<Entry> = (0 until (arr?.length() ?: 0)).mapNotNull { index ->
        val value = arr?.optJSONObject(index) ?: return@mapNotNull null
        val id = value.optInt("id", -1)
        val path = value.optString("path")
        if (id <= 0 || path.substringAfterLast('.', "").lowercase() !in formats) return@mapNotNull null
        Entry(id, path, value.optLong("length"))
    }
    fun select(files: List<Entry>, episodeNumber: Int?): Entry {
        val candidates = files.filterNot { it.path.contains("sample", true) }.ifEmpty { files }
        check(candidates.isNotEmpty()) { "This torrent contains no playable video" }
        if (episodeNumber != null) {
            val episode = Regex("(?i)(?:s\\d{1,2}e|episode[ ._-]*|\\be)0*${episodeNumber}(?!\\d)")
            candidates.filter { episode.containsMatchIn(it.path) }.maxByOrNull { it.size }?.let { return it }
            check(candidates.size == 1) { "This torrent contains several videos; no matching episode $episodeNumber was found" }
        }
        return candidates.maxBy { it.size }
    }
}

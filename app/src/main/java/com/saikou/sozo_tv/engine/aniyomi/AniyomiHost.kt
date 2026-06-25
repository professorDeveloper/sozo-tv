package com.saikou.sozo_tv.engine.aniyomi

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AniyomiHost(private val context: Context) {

    companion object {
        private const val TAG = "AniyomiHost"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }

    private data class SourceMeta(
        val id: String,
        val name: String,
        val lang: String,
        val baseUrl: String,
        val pkg: String,
        val className: String,
        val apkUrl: String,
        val iconUrl: String,
        val nsfw: Boolean,
        val repoName: String,
    )

    private val sources = LinkedHashMap<String, SourceMeta>()

    fun registerMeta(entry: JSONObject, repoName: String) {
        val id = entry.optString("id")
        if (id.isEmpty()) return
        sources[id] = SourceMeta(
            id = id,
            name = entry.optString("name"),
            lang = entry.optString("lang"),
            baseUrl = entry.optString("baseUrl"),
            pkg = entry.optString("pkg"),
            className = entry.optString("className"),
            apkUrl = entry.optString("apkUrl"),
            iconUrl = entry.optString("iconUrl"),
            nsfw = entry.optBoolean("nsfw", false),
            repoName = repoName,
        )
    }

    fun removeSources(ids: List<String>) {
        ids.forEach { sources.remove(it) }
    }

    private fun langRank(lang: String): Int = when (lang.trim().lowercase()) {
        "en" -> 0
        "all" -> 1
        else -> 2
    }

    fun providersJson(): String {
        val picked = LinkedHashMap<String, SourceMeta>()
        for (s in sources.values) {
            val key = s.name.trim().lowercase()
            if (key.isEmpty()) continue
            val cur = picked[key]
            if (cur == null || langRank(s.lang) < langRank(cur.lang)) picked[key] = s
        }
        val arr = JSONArray()
        for (s in picked.values) {
            arr.put(JSONObject().apply {
                put("id", "an:${s.id}")
                put("name", s.name)
                put("lang", s.lang)
                put("baseUrl", s.baseUrl)
                put("icon", s.iconUrl)
                put("nsfw", s.nsfw)
                put("repo", s.repoName)
                put("mode", "client")
                put("group", "aniyomi")
            })
        }
        return arr.toString()
    }

    // --- runtime: load source + convert to soplay JSON ---

    private fun ensureApk(meta: SourceMeta): File? {
        if (meta.apkUrl.isEmpty()) return null
        val dir = File(context.filesDir, "aniyomi").apply { mkdirs() }
        val file = File(dir, (meta.pkg.ifEmpty { meta.id }).replace('/', '_') + ".apk")
        if (file.exists() && file.length() > 0) return file
        return try {
            val conn = (URL(meta.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; instanceFollowRedirects = true
                connectTimeout = 20000; readTimeout = 60000
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "apk ${meta.apkUrl} -> ${conn.responseCode}"); return null
            }
            conn.inputStream.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
            file
        } catch (t: Throwable) {
            Log.e(TAG, "apk download failed: ${t.message}"); null
        }
    }

    private fun sourceFor(id: String): AnimeCatalogueSource? {
        val meta = sources[id] ?: return null
        val apk = ensureApk(meta) ?: return null
        return AniyomiRuntime.source(context, apk.absolutePath, meta.pkg, meta.id)
    }

    fun configurable(id: String): ConfigurableAnimeSource? = sourceFor(id) as? ConfigurableAnimeSource

    private fun cardJson(a: SAnime, id: String) = JSONObject().apply {
        put("provider", "an:$id")
        put("externalId", a.url)
        put("title", a.title)
        put("slug", a.url)
        put("contentUrl", a.url)
        put("thumbnail", a.thumbnail_url)
        put("type", "Anime")
    }

    private fun newAnime(url: String) = SAnimeImpl().apply { this.url = url; title = "" }

    fun getMainPageJson(id: String, page: Int): String {
        val src = sourceFor(id)
        val sections = JSONArray()
        val banner = JSONArray()
        if (src != null) {
            try {
                val pop = runBlocking { src.getPopularAnime(page) }
                pop.animes.firstOrNull()?.let {
                    val t = runCatching { it.title }.getOrDefault("<unset>")
                    Log.i(TAG, "popular[0] title='$t' url='${it.url}'")
                }
                val items = JSONArray()
                for (a in pop.animes.take(30)) items.put(cardJson(a, id))
                if (items.length() > 0) {
                    var i = 0
                    while (i < items.length() && i < 12) { banner.put(items.get(i)); i++ }
                    sections.put(JSONObject().apply {
                        put("key", "popular"); put("label", "Popular")
                        put("viewAll", JSONObject().apply { put("type", "an"); put("slug", "popular") })
                        put("items", items)
                    })
                }
            } catch (t: Throwable) { Log.e(TAG, "getPopular $id: ${t.message}") }
            try {
                if (src.supportsLatest) {
                    val lat = runBlocking { src.getLatestUpdates(page) }
                    val items = JSONArray()
                    for (a in lat.animes.take(30)) items.put(cardJson(a, id))
                    if (items.length() > 0) sections.put(JSONObject().apply {
                        put("key", "latest"); put("label", "Latest")
                        put("viewAll", JSONObject().apply { put("type", "an"); put("slug", "latest") })
                        put("items", items)
                    })
                }
            } catch (t: Throwable) { Log.e(TAG, "getLatest $id: ${t.message}") }
        }
        return JSONObject().apply {
            put("provider", "an:$id"); put("banner", banner); put("sections", sections)
        }.toString()
    }

    fun getSectionJson(id: String, data: String, page: Int): String {
        val src = sourceFor(id)
        val items = JSONArray()
        var hasNext = false
        if (src != null) try {
            val pg = runBlocking {
                if (data == "latest" && src.supportsLatest) src.getLatestUpdates(page)
                else src.getPopularAnime(page)
            }
            for (a in pg.animes) items.put(cardJson(a, id))
            hasNext = pg.hasNextPage
        } catch (t: Throwable) { Log.e(TAG, "getSection $id: ${t.message}") }
        return JSONObject().apply {
            put("provider", "an:$id"); put("items", items); put("page", page)
            put("totalPages", if (hasNext) page + 1 else page)
        }.toString()
    }

    fun searchJson(id: String, query: String): String {
        val src = sourceFor(id)
        val items = JSONArray()
        if (src != null) try {
            val pg = runBlocking { src.getSearchAnime(1, query, AnimeFilterList()) }
            for (a in pg.animes) items.put(cardJson(a, id))
        } catch (t: Throwable) { Log.e(TAG, "search $id: ${t.message}") }
        return JSONObject().apply {
            put("provider", "an:$id"); put("items", items)
            put("query", query); put("page", 1); put("totalPages", 1)
        }.toString()
    }

    fun getGenresJson(id: String): String = "[]"

    private fun statusLabel(status: Int): String? = when (status) {
        1 -> "Ongoing"
        2 -> "Completed"
        3 -> "Licensed"
        4 -> "Publishing finished"
        5 -> "Cancelled"
        6 -> "On hiatus"
        else -> null
    }

    fun loadJson(id: String, url: String): String {
        val src = sourceFor(id) ?: return "{}"
        val anime = newAnime(url)
        val details = try { runBlocking { src.getAnimeDetails(anime) } }
        catch (t: Throwable) { Log.e(TAG, "details $id: ${t.message}"); anime }
        val eps = try { runBlocking { src.getEpisodeList(anime) } }
        catch (t: Throwable) { Log.e(TAG, "episodes $id: ${t.message}"); emptyList() }
        // A single-episode entry is a movie; multiple episodes is a series.
        val isMovie = eps.size <= 1
        val episodes = JSONArray()
        eps.sortedBy { it.episode_number }.forEachIndexed { i, e ->
            val num = if (e.episode_number > 0) e.episode_number.toInt() else (i + 1)
            episodes.put(JSONObject().apply {
                put("episode", num)
                put("label", if (isMovie) "Play" else e.name.ifEmpty { "Episode $num" })
                put("mediaRef", e.url)
            })
        }
        val title = try { details.title } catch (_: Throwable) { "" }
        val author = try { details.author } catch (_: Throwable) { null }
        val status = statusLabel(try { details.status } catch (_: Throwable) { 0 })
        val desc = buildString {
            status?.let { append("• ").append(it) }
            val d = try { details.description } catch (_: Throwable) { null }
            if (!d.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(d)
            }
        }
        // Aniyomi has no "recommendations" API, so derive a "similar" row from a
        // title search (same fallback CloudStream uses).
        val related = JSONArray()
        try {
            val q = title.replace(Regex("\\(.*?\\)"), "").trim()
            if (q.length >= 2) {
                val results = runBlocking { src.getSearchAnime(1, q, AnimeFilterList()) }
                for (a in results.animes) {
                    if (a.url == url) continue
                    related.put(cardJson(a, id))
                    if (related.length() >= 20) break
                }
            }
        } catch (t: Throwable) { Log.e(TAG, "related $id: ${t.message}") }
        return JSONObject().apply {
            put("provider", "an:$id")
            put("contentId", url); put("contentUrl", url)
            put("title", title)
            put("description", desc)
            put("thumbnail", details.thumbnail_url)
            put("banner", details.thumbnail_url)
            put("year", JSONObject.NULL)
            if (!author.isNullOrBlank()) put("director", author)
            put("genres", JSONArray(details.getGenres() ?: emptyList<String>()))
            put("type", if (isMovie) "Movie" else "Anime")
            put("isSerial", !isMovie)
            put("cast", JSONArray())
            put("related", related)
            put("episodes", episodes)
        }.toString()
    }

    fun loadLinksJson(id: String, data: String): String {
        val meta = sources[id]
        val src = sourceFor(id)
        val videoSources = JSONArray()
        val subs = JSONArray()
        val seen = HashSet<String>()
        val seenSub = HashSet<String>()
        if (src != null && meta != null) {
            val episode = SEpisodeImpl().apply { url = data; name = "" }
            // Default request headers the source applies to every request (often carry the
            // User-Agent / Referer the CDN gates on). Many extensions don't repeat these on
            // each Video, so without this fallback the player's own client gets a 403.
            val baseHeaders = (src as? AnimeHttpSource)?.headers
            val videos = try { runBlocking { src.getVideoList(episode) } }
            catch (t: Throwable) { Log.e(TAG, "videos $id: ${t.message}"); emptyList() }
            for (v in videos) {
                var vu = v.videoUrl
                if (vu.isNullOrEmpty()) {
                    vu = try {
                        runBlocking { (src as? AnimeHttpSource)?.getVideoUrl(v) }
                    } catch (t: Throwable) {
                        Log.e(TAG, "getVideoUrl $id: ${t.message}"); null
                    }
                }
                if (vu.isNullOrEmpty() || !seen.add(vu)) continue
                val headers = JSONObject()
                baseHeaders?.forEach { (k, value) -> headers.put(k, value) }
                v.headers?.forEach { (k, value) -> headers.put(k, value) }
                val isHls = vu.contains(".m3u8")
                videoSources.put(JSONObject().apply {
                    put("quality", v.quality.ifEmpty { "Source" })
                    put("videoUrl", vu)
                    put("type", if (isHls) "hls" else "http")
                    put("host", meta.name)
                    put("isDefault", videoSources.length() == 0)
                    put("accessible", true)
                    put("headers", headers)
                })
                for (t in v.subtitleTracks) {
                    if (t.url.isNotEmpty() && seenSub.add(t.url)) subs.put(JSONObject().apply {
                        put("label", t.lang); put("file", t.url); put("default", false)
                    })
                }
            }
        }
        val first = if (videoSources.length() > 0) videoSources.getJSONObject(0) else null
        return JSONObject().apply {
            put("videoUrl", first?.optString("videoUrl"))
            put("type", first?.optString("type"))
            put("headers", first?.optJSONObject("headers") ?: JSONObject())
            put("videoSources", videoSources)
            put("subtitles", subs)
        }.toString()
    }
}

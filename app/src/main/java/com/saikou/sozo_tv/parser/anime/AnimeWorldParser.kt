package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class AnimeWorldParser : BaseParser() {

    override val name: String = "AnimeWorld"
    override val saveName: String = "animeworld"
    override val hostUrl: String = "https://www.animeworld.ac"
    override val language: String = "it"

    companion object {
        private const val TAG = "AnimeWorldParser"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        private val episodeCache = mutableMapOf<String, List<Data>>()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    }

    private fun getDefaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "it-IT,it;q=0.8,en-US;q=0.5,en;q=0.3",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1"
        )
    }


    private suspend fun fetchDocument(url: String, useUnsafe: Boolean = false): Document? {
        return withContext(Dispatchers.IO) {
            try {
                val client = httpClient

                val request = Request.Builder().url(url).apply {
                    getDefaultHeaders().forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }.build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body.string().let { html ->
                        Jsoup.parse(html)
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                if (!useUnsafe && (e.message?.contains("SSL") == true || e.message?.contains("certificate") == true)) {
                    fetchDocument(url, true)
                } else {
                    null
                }
            }
        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for: $query")

                val encodedQuery = query.replace(" ", "+").takeIf { it.length > 6 }?.substring(0, 6)
                    ?: query.replace(" ", "+")
                val url = "$hostUrl/search?keyword=$encodedQuery"

                val doc = fetchDocument(url) ?: return@withContext emptyList()
                val results = doc.select("div.film-list .item").mapNotNull { item ->
                    try {
                        val linkElement = item.selectFirst("a")
                        val titleElement = item.selectFirst("a.name")
                        val imageElement = item.selectFirst("img")

                        if (linkElement != null && titleElement != null) {
                            val href = linkElement.attr("href")
                            val id = href.substringAfterLast("/")
                            val title = titleElement.text().trim()
                            var poster = imageElement?.attr("src") ?: ""

                            if (poster.isNotEmpty() && !poster.startsWith("http")) {
                                poster = if (poster.startsWith("//")) {
                                    "https:$poster"
                                } else if (poster.startsWith("/")) {
                                    "$hostUrl$poster"
                                } else {
                                    "$hostUrl/$poster"
                                }
                            }

                            val isMovie =
                                item.selectFirst("div.status > div.movie")?.text() == "Movie"

                            Log.d(TAG, "Found: $title (ID: $id, Movie: $isMovie)")

                            ShowResponse(
                                name = title,
                                link = id,
                                coverUrl = poster,
                                otherNames = emptyList(),
                                extra = mapOf("type" to if (isMovie) "movie" else "tv")
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing item: ${e.message}")
                        null
                    }
                }.distinctBy { it.name }

                return@withContext results

            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(
        id: String, page: Int, showResponse: ShowResponse
    ): EpisodeData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading episodes for: $id, page: $page")

                val url = "$hostUrl/play/$id"

                val doc = fetchDocument(url) ?: return@withContext null

                val isMovie = doc.selectFirst("div.status > div.movie")?.text() == "Movie"

                if (isMovie) {
                    Log.d(TAG, "Detected as movie")
                    return@withContext EpisodeData(
                        current_page = 1,
                        data = listOf(
                            Data(
                                id = id.hashCode(),
                                episode = 1,
                                session = id,
                                title = showResponse.name,
                                snapshot = showResponse.coverUrl,
                                season = 1
                            )
                        ),
                        from = 1,
                        last_page = 1,
                        next_page_url = null,
                        per_page = 1,
                        prev_page_url = null,
                        to = 1,
                        total = 1
                    )
                }
                val cachedEpisodes = episodeCache[id]
                val allEpisodes = if (cachedEpisodes != null) {
                    Log.d(TAG, "Using cached episodes")
                    cachedEpisodes
                } else {
                    Log.d(TAG, "Parsing fresh episodes")
                    parseAllEpisodes(doc, showResponse).also {
                        episodeCache[id] = it
                    }
                }

                if (allEpisodes.isEmpty()) {
                    Log.w(TAG, "No episodes found")
                    return@withContext null
                }

                Log.d(TAG, "Total episodes: ${allEpisodes.size}")
                val perPage = 50
                val totalEpisodes = allEpisodes.size
                val totalPages = (totalEpisodes + perPage - 1) / perPage
                val currentPage = page.coerceAtMost(totalPages).coerceAtLeast(1)

                val startIndex = (currentPage - 1) * perPage
                val endIndex = minOf(startIndex + perPage, totalEpisodes)

                val paginatedEpisodes = allEpisodes.subList(startIndex, endIndex)

                EpisodeData(
                    current_page = currentPage,
                    data = paginatedEpisodes,
                    from = startIndex + 1,
                    last_page = totalPages,
                    next_page_url = if (currentPage < totalPages) "/episodes/$id?page=${currentPage + 1}" else null,
                    per_page = perPage,
                    prev_page_url = if (currentPage > 1) "/episodes/$id?page=${currentPage - 1}" else null,
                    to = endIndex,
                    total = totalEpisodes
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes: ${e.message}")
                null
            }
        }
    }

    private fun parseAllEpisodes(doc: Document, showResponse: ShowResponse): List<Data> {
        val allEpisodes = mutableListOf<Data>()

        var cssQuery = "#animeId > div.widget-body > div[data-id=\"9\"]"
        val ranges = doc.select("#animeId div.widget-body div.server[data-id=\"9\"] div.range span")

        if (ranges.isNotEmpty()) {
            ranges.forEach { range ->
                val rangeId = range.attr("data-range-id")
                val rangeEpisodes =
                    doc.select("#animeId > div.widget-body > div[data-id=\"9\"] ul.episodes.range[data-range-id=\"$rangeId\"] a")

                rangeEpisodes.forEachIndexed { index, episode ->
                    try {
                        val episodeId = episode.attr("data-id")
                        val title = episode.text().trim()
                        val episodeNum = extractEpisodeNumber(title)

                        if (episodeId.isNotBlank()) {
                            allEpisodes.add(
                                Data(
                                    id = episodeId.hashCode(),
                                    episode = episodeNum,
                                    session = episodeId,
                                    title = title,
                                    snapshot = showResponse.coverUrl,
                                    season = 1
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing episode: ${e.message}")
                    }
                }
            }
        } else {
            cssQuery += " a"
            val episodes = doc.select(cssQuery)

            episodes.forEachIndexed { index, episode ->
                try {
                    val episodeId = episode.attr("data-id")
                    val title = episode.text().trim()
                    val episodeNum = extractEpisodeNumber(title)

                    if (episodeId.isNotBlank()) {
                        allEpisodes.add(
                            Data(
                                id = episodeId.hashCode(),
                                episode = episodeNum,
                                session = episodeId,
                                title = title,
                                snapshot = showResponse.coverUrl,
                                season = 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing episode: ${e.message}")
                }
            }
        }

        if (allEpisodes.isEmpty()) {
            Log.d(TAG, "No episodes in AnimeWorld server, trying Streamtape...")
            cssQuery = "#animeId > div.widget-body > div[data-id=\"8\"] a"
            val streamtapeEpisodes = doc.select(cssQuery)

            streamtapeEpisodes.forEachIndexed { index, episode ->
                try {
                    val episodeId = episode.attr("data-id")
                    val title = episode.text().trim()
                    val episodeNum = extractEpisodeNumber(title)

                    if (episodeId.isNotBlank()) {
                        allEpisodes.add(
                            Data(
                                id = episodeId.hashCode(),
                                episode = episodeNum,
                                session = episodeId,
                                title = title,
                                snapshot = showResponse.coverUrl,
                                season = 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Streamtape episode: ${e.message}")
                }
            }
        }

        return allEpisodes.sortedBy { it.episode }
    }

    private fun extractEpisodeNumber(title: String): Int {
        val patterns = listOf(
            "Episodio\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Episode\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Ep\\.\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "(\\d+)\\s*[-:]".toRegex(),
            "\\b(\\d+)\\b".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

    override suspend fun getEpisodeVideo(id: String, epId: String, epNum: Int): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting video for episode: $epId of show: $id")
                val url = "$hostUrl/api/episode/serverPlayerAnimeWorld?id=$epId"
                val document = fetchDocument(url) ?: return@withContext emptyList()

                val videoSource = document.select("video source").firstOrNull()
                val videoUrl = videoSource?.attr("src") ?: return@withContext emptyList()

                Log.d(TAG, "Found video URL: $videoUrl")

                val audioType = if (videoUrl.contains("ITA", ignoreCase = true)) {
                    com.saikou.sozo_tv.parser.models.AudioType.DUB
                } else {
                    com.saikou.sozo_tv.parser.models.AudioType.SUB
                }

                val mimeType = getMimeType(videoUrl)

                val videoOption = VideoOption(
                    videoUrl = videoUrl,
                    fansub = "AnimeWorld",
                    resolution = "720p",
                    audioType = audioType,
                    quality = "Adaptive",
                    isActive = true,
                    mimeTypes = mimeType,
                    fullText = "AnimeWorld Server",
                    headers = getDefaultHeaders()
                )

                listOf(videoOption)

            } catch (e: Exception) {
                Log.e(TAG, "Error getting episode video: ${e.message}")
                emptyList()
            }
        }
    }


    private fun getMimeType(url: String): String {
        return when {
            url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            url.contains(".mp4") -> MimeTypes.APPLICATION_MP4
            url.contains(".webm") -> MimeTypes.VIDEO_WEBM
            else -> MimeTypes.APPLICATION_M3U8
        }
    }


}
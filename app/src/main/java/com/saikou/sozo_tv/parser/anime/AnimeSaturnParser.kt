package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class AnimeSaturnParser : BaseParser() {

    override val name = "AnimeSaturn"
    override val saveName = "animesaturn"
    override val hostUrl = "https://www.animesaturn.cx"
    override val language = "it"

    companion object {
        private const val TAG = "AnimeSaturnParser"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private fun getDefaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "it-IT,it;q=0.8,en-US;q=0.5,en;q=0.3",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Referer" to "$hostUrl/"
        )
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for: $query")

                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$hostUrl/animelist?search=$encodedQuery"
                Log.d(TAG, "search: $url")

                val html = Utils.get(url, getDefaultHeaders())
                val doc = Jsoup.parse(html)
                val results = doc.select(".list-group-item.bg-dark-as-box-shadow")
                Log.d(TAG, "search: $results")
                if (results.isEmpty()) {
                    doc.select("div.row.pt-4.justify-content-center .anime-card-newanime")
                        .mapNotNull { item ->
                            try {
                                val linkElement =
                                    item.selectFirst("a[href]") ?: return@mapNotNull null
                                val href = linkElement.attr("href")
                                val title = linkElement.attr("title").trim()
                                val poster = item.selectFirst("img.new-anime")?.attr("src") ?: ""
                                val animeId = href.substringAfterLast("/")

                                ShowResponse(
                                    name = title,
                                    link = animeId,
                                    coverUrl = if (poster.isNotEmpty() && !poster.startsWith("http")) {
                                        "$hostUrl$poster"
                                    } else {
                                        poster
                                    },
                                    otherNames = emptyList(),
                                    extra = mapOf("type" to "tv")
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing search item: ${e.message}")
                                null
                            }
                        }
                } else {
                    results.mapNotNull { item ->
                        try {
                            val linkElement = item.selectFirst(".info-archivio h3 a.badge-archivio")
                                ?: return@mapNotNull null
                            val href = linkElement.attr("href")
                            val title = linkElement.text().trim()
                            val animeId = href.substringAfterLast("/")
                            val poster =
                                item.selectFirst("img.locandina-archivio")?.attr("src") ?: ""

                            ShowResponse(
                                name = title,
                                link = animeId,
                                coverUrl = if (poster.isNotEmpty() && !poster.startsWith("http")) {
                                    "$hostUrl$poster"
                                } else {
                                    poster
                                },
                                otherNames = emptyList(),
                                extra = mapOf("type" to "tv")
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing search result: ${e.message}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(
        id: String,
        page: Int,
        showResponse: ShowResponse
    ): EpisodeData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading episodes for: $id")

                val url = "$hostUrl/anime/$id"
                val html = Utils.get(url, getDefaultHeaders())
                val doc = Jsoup.parse(html)

                val allEpisodes = mutableListOf<Data>()

                val episodeRanges = doc.select(".episode-range")

                if (episodeRanges.isNotEmpty()) {
                    // Process each range/tab
                    episodeRanges.forEachIndexed { rangeIndex, rangeElement ->
                        val tabId = rangeElement.attr("href").substringAfter("#")
                        val tabContent = doc.selectFirst("#$tabId")

                        tabContent?.select(".episodi-link-button a")
                            ?.forEachIndexed { index, button ->
                                try {
                                    val episodeUrl = button.attr("href")
                                    val episodeTitle = button.text().trim()
                                    val episodeNumber =
                                        extractEpisodeNumber(episodeTitle, index + 1)

                                    allEpisodes.add(
                                        Data(
                                            id = episodeUrl.hashCode(),
                                            episode = episodeNumber,
                                            session = episodeUrl,
                                            title = episodeTitle,
                                            snapshot = showResponse.coverUrl,
                                            season = rangeIndex + 1
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing episode in range: ${e.message}")
                                }
                            }
                    }
                } else {
                    // No ranges, single list of episodes
                    doc.select(".episodi-link-button a").forEachIndexed { index, button ->
                        try {
                            val episodeUrl = button.attr("href")
                            val episodeTitle = button.text().trim()
                            val episodeNumber = extractEpisodeNumber(episodeTitle, index + 1)

                            allEpisodes.add(
                                Data(
                                    id = episodeUrl.hashCode(),
                                    episode = episodeNumber,
                                    session = episodeUrl,
                                    title = episodeTitle,
                                    snapshot = showResponse.coverUrl,
                                    season = 1
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing episode: ${e.message}")
                        }
                    }
                }

                if (allEpisodes.isEmpty()) {
                    Log.w(TAG, "No episodes found")
                    return@withContext null
                }

                // Sort by episode number
                val sortedEpisodes = allEpisodes.sortedBy { it.episode }

                // AnimeSaturn doesn't seem to have pagination for episodes, return all at once
                EpisodeData(
                    current_page = 1,
                    data = sortedEpisodes,
                    from = 1,
                    last_page = 1,
                    next_page_url = null,
                    per_page = sortedEpisodes.size,
                    prev_page_url = null,
                    to = sortedEpisodes.size,
                    total = sortedEpisodes.size
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes: ${e.message}")
                null
            }
        }
    }

    private fun extractEpisodeNumber(title: String, default: Int): Int {
        try {
            // Try to extract episode number from various patterns
            val patterns = listOf(
                "Episodio\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
                "Episode\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
                "Ep\\.\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE),
                "(\\d+)(?:\\s+episode|ep)".toRegex(RegexOption.IGNORE_CASE),
                "#(\\d+)".toRegex(),
                "\\b(\\d+)\\b".toRegex()
            )

            for (pattern in patterns) {
                val match = pattern.find(title)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull() ?: default
                }
            }

            // Try to extract from URL pattern (like -ep-123)
            val urlPattern = ".*[_-]ep[_-](\\d+).*".toRegex(RegexOption.IGNORE_CASE)
            val urlMatch = urlPattern.find(title)
            if (urlMatch != null) {
                return urlMatch.groupValues[1].toIntOrNull() ?: default
            }

            return default
        } catch (e: Exception) {
            return default
        }
    }

    override suspend fun getEpisodeVideo(id: String, epId: String, epNum: Int): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting video for episode: $epId")
                val episodeUrl = if (epId.startsWith("http")) epId else "$hostUrl$epId"

                val episodeHtml = Utils.get(episodeUrl, getDefaultHeaders())
                val episodeDoc = Jsoup.parse(episodeHtml)

                val streamingLink = episodeDoc.selectFirst("a[href*='watch?file=']")
                if (streamingLink == null) {
                    Log.e(TAG, "No streaming link found on episode page")
                    return@withContext emptyList()
                }

                val watchPath = streamingLink.attr("href")
                val watchUrl = if (watchPath.startsWith("http")) watchPath else "$hostUrl$watchPath"

                Log.d(TAG, "Found watch URL: $watchUrl")

                val videoHtml = Utils.get(watchUrl, getDefaultHeaders())
                val videoDoc = Jsoup.parse(videoHtml)

                val videoUrl = extractVideoFromPage(videoDoc)

                if (videoUrl.isBlank()) {
                    Log.e(TAG, "Could not extract video URL from watch page")
                    return@withContext emptyList()
                }

                Log.d(TAG, "Extracted video URL: $videoUrl")


                listOf(
                    VideoOption(
                        videoUrl = videoUrl,
                        fansub = "AnimeSaturn",
                        resolution = "720p",
                        audioType = AudioType.SUB,
                        quality = "Adaptive",
                        isActive = true,
                        mimeTypes = getMimeType(videoUrl),
                        fullText = "AnimeSaturn",
                        headers = getDefaultHeaders()
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error getting episode video: ${e.message}")
                emptyList()
            }
        }
    }

    private fun extractVideoFromPage(doc: Document): String {
        val videoSource = doc.selectFirst("video source[src]")
        videoSource?.attr("src")?.let {
            if (it.isNotBlank()) return it
        }

        // Method 2: Look for iframe with video
        val iframe = doc.selectFirst("iframe[src]")
        iframe?.attr("src")?.let { iframeSrc ->
            if (iframeSrc.contains("video") || iframeSrc.contains("stream")) {
                return iframeSrc
            }
        }

        // Method 3: Look for JWPlayer configuration in scripts
        val scripts = doc.select("script")
        for (script in scripts) {
            val scriptContent = script.html()
            if (scriptContent.contains("jwplayer") || scriptContent.contains("videojs")) {
                // Try to extract file URL from player config
                val patterns = listOf(
                    "\"file\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                    "\"src\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                    "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                    "file:\\s*[\"']([^\"']+)[\"']".toRegex(),
                    "src:\\s*[\"']([^\"']+)[\"']".toRegex()
                )

                for (pattern in patterns) {
                    val match = pattern.find(scriptContent)
                    if (match != null) {
                        val url = match.groupValues[1]
                        if (url.isNotBlank() && (url.contains(".mp4") || url.contains(".m3u8"))) {
                            return url
                        }
                    }
                }
            }
        }

        // Method 4: Look for video in data attributes
        val videoElement = doc.selectFirst("[data-video-src], [data-src]")
        videoElement?.attr("data-video-src")?.let {
            if (it.isNotBlank()) return it
        }
        videoElement?.attr("data-src")?.let {
            if (it.isNotBlank() && (it.contains(".mp4") || it.contains(".m3u8"))) {
                return it
            }
        }

        return ""
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
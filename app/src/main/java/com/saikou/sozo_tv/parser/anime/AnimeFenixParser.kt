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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AnimeFenixParser : BaseParser() {

    override val name = "AnimeFenix"
    override val saveName = "animefenix"
    override val hostUrl = "https://animefenix2.tv"
    override val language = "es"

    companion object {
        private const val TAG = "AnimeFenixProvider"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        private val genres = listOf(
            "1" to "Acción", "23" to "Aventuras", "20" to "Ciencia Ficción",
            "5" to "Comedia", "8" to "Deportes", "38" to "Demonios",
            "6" to "Drama", "11" to "Ecchi", "2" to "Escolares",
            "13" to "Fantasía", "28" to "Harem", "24" to "Histórico",
            "47" to "Horror", "25" to "Infantil", "51" to "Isekai",
            "29" to "Josei", "14" to "Magia", "26" to "Artes Marciales",
            "21" to "Mecha", "22" to "Militar", "17" to "Misterio",
            "36" to "Música", "30" to "Parodia", "31" to "Policía",
            "18" to "Psicológico", "10" to "Recuentos de la vida", "3" to "Romance",
            "34" to "Samurai", "7" to "Seinen", "4" to "Shoujo",
            "9" to "Shounen", "12" to "Sobrenatural", "15" to "Superpoderes",
            "19" to "Suspenso", "27" to "Terror", "39" to "Vampiros",
            "40" to "Yaoi", "37" to "Yuri"
        )
    }

    private fun getDefaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "es-ES,es;q=0.8,en-US;q=0.5,en;q=0.3",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Referer" to "$hostUrl/"
        )
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for: $query")

                if (query.isBlank()) {
                    // Return genres as ShowResponse for empty query
                    return@withContext genres.map { (id, name) ->
                        ShowResponse(
                            name = name,
                            link = id,
                            coverUrl = "",
                            otherNames = emptyList(),
                            extra = mapOf("type" to "genre")
                        )
                    }
                }

                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$hostUrl/directorio/anime?q=$encodedQuery"

                val doc = Utils.getJsoup(url, getDefaultHeaders())
                parseShows(doc.select(".grid-animes li article")).distinctBy { it.link }
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseShows(elements: List<Element>): List<ShowResponse> {
        return elements.mapNotNull { item ->
            try {
                val a = item.selectFirst("a") ?: item
                val titleElement = item.selectFirst("h3, p:not(.gray)")
                val imageElement = item.selectFirst("img")

                val href = a.attr("href")
                val id = if (href.contains("/")) href.substringAfterLast("/") else href
                val title = titleElement?.text()?.trim() ?: ""
                val poster = imageElement?.let { img ->
                    img.attr("data-src").ifBlank { img.attr("src") }
                } ?: ""

                val isMovie = item.selectFirst(".main-img") != null

                ShowResponse(
                    name = title,
                    link = id,
                    coverUrl = if (poster.isNotEmpty() && !poster.startsWith("http")) {
                        if (poster.startsWith("//")) {
                            "https:$poster"
                        } else if (poster.startsWith("/")) {
                            "$hostUrl$poster"
                        } else {
                            poster
                        }
                    } else {
                        poster
                    },
                    otherNames = emptyList(),
                    extra = mapOf(
                        "type" to if (isMovie) "movie" else "tv",
                        "full_url" to href
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing show: ${e.message}")
                null
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
                Log.d(TAG, "Loading episodes for: $id, page: $page")

                if (showResponse.extra?.get("type") == "genre") {
                    return@withContext null
                }

                val fullUrl = showResponse.extra?.get("full_url") ?: "$hostUrl/anime/$id"
                val doc = Utils.getJsoup(fullUrl, getDefaultHeaders())

                val isMovie = doc.selectFirst("p.text-gray-300")?.text()?.contains("Película", true) == true ||
                        showResponse.extra?.get("type") == "movie"

                if (isMovie) {
                    Log.d(TAG, "Detected as movie")
                    return@withContext EpisodeData(
                        current_page = 1,
                        data = listOf(
                            Data(
                                id = id.hashCode(),
                                episode = 1,
                                session = fullUrl,
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

                val allEpisodes = parseEpisodesFromDocument(doc, showResponse)

                if (allEpisodes.isEmpty()) {
                    Log.w(TAG, "No episodes found")
                    return@withContext null
                }

                Log.d(TAG, "Total episodes: ${allEpisodes.size}")

                val reversedEpisodes = allEpisodes.sortedByDescending { it.episode }

                EpisodeData(
                    current_page = 1,
                    data = reversedEpisodes,
                    from = 1,
                    last_page = 1,
                    next_page_url = null,
                    per_page = reversedEpisodes.size,
                    prev_page_url = null,
                    to = reversedEpisodes.size,
                    total = reversedEpisodes.size
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes: ${e.message}")
                null
            }
        }
    }

    private fun parseEpisodesFromDocument(doc: Document, showResponse: ShowResponse): List<Data> {
        val allEpisodes = mutableListOf<Data>()

        doc.select(".divide-y li > a").forEachIndexed { index, element ->
            try {
                val episodeUrl = element.attr("href")
                val titleElement = element.selectFirst(".font-semibold")
                val title = titleElement?.text()?.trim() ?: "Episodio ${index + 1}"
                val episodeNum = extractEpisodeNumber(title, index + 1)

                allEpisodes.add(
                    Data(
                        id = episodeUrl.hashCode(),
                        episode = episodeNum,
                        session = episodeUrl,
                        title = title,
                        snapshot = showResponse.coverUrl,
                        season = 1
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing episode: ${e.message}")
            }
        }

        return allEpisodes
    }

    private fun extractEpisodeNumber(title: String, default: Int): Int {
        val patterns = listOf(
            "Episodio\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Episode\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Ep\\.\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Capítulo\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "Cap\\.\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "#(\\d+)".toRegex(),
            "\\b(\\d+)\\b".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: default
            }
        }
        return default
    }

    override suspend fun getEpisodeVideo(id: String, epId: String): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting video for episode: $epId")

                val episodeUrl = if (epId.startsWith("http")) epId else "$hostUrl$epId"

                val doc = Utils.getJsoup(episodeUrl, getDefaultHeaders())

                val serverNames = doc.select(".episode-page__servers-list li a").map { a ->
                    a.select("span").last()?.text()?.trim() ?: ""
                }.filter { it.isNotBlank() }

                Log.d(TAG, "Found ${serverNames.size} server names")

                val script = doc.selectFirst("script:containsData(var tabsArray)")
                if (script == null) {
                    Log.e(TAG, "No tabsArray script found")
                    return@withContext emptyList()
                }

                val scriptContent = script.data()
                Log.d(TAG, "Script content length: ${scriptContent.length}")
                val videoUrls = extractVideoUrlsFromScript(scriptContent)

                if (videoUrls.isEmpty()) {
                    Log.e(TAG, "No video URLs found in script")
                    return@withContext emptyList()
                }

                Log.d(TAG, "Found ${videoUrls.size} video URLs")

                val videoOptions = mutableListOf<VideoOption>()

                for (i in videoUrls.indices) {
                    val videoUrl = videoUrls[i]
                    val serverName = if (i < serverNames.size) serverNames[i] else "Server ${i + 1}"

                    if (videoUrl.isBlank()) continue

                    val resolution = determineResolution(serverName)
                    val audioType = determineAudioType(serverName)

                    Log.d(TAG, "Processing server: $serverName, URL: ${videoUrl.take(100)}...")

                    videoOptions.add(
                        VideoOption(
                            videoUrl = videoUrl,
                            fansub = "AnimeFenix",
                            resolution = resolution,
                            audioType = audioType,
                            quality = "Adaptive",
                            isActive = true,
                            mimeTypes = "video/mp4",
                            fullText = serverName,
                            headers = getDefaultHeaders()
                        )
                    )
                }

                return@withContext videoOptions

            } catch (e: Exception) {
                Log.e(TAG, "Error getting episode video: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun extractVideoUrlsFromScript(scriptContent: String): List<String> {
        val urls = mutableListOf<String>()

        val srcPattern = "src='([^']+)'".toRegex()
        val srcMatches = srcPattern.findAll(scriptContent)

        srcMatches.forEach { match ->
            val url = match.groupValues[1]
            if (url.contains("redirect.php")) {
                val fullUrl = if (url.startsWith("http")) url else "$hostUrl/$url"
                urls.add(fullUrl)
            }
        }

        // Method 2: Look for var tabsArray = [...]
        if (urls.isEmpty()) {
            val tabsArrayPattern = "var tabsArray\\s*=\\s*\\[([^\\]]+)\\]".toRegex()
            val tabsArrayMatch = tabsArrayPattern.find(scriptContent)

            if (tabsArrayMatch != null) {
                val arrayContent = tabsArrayMatch.groupValues[1]
                val urlPattern = "\"([^\"]+)\"".toRegex()
                val urlMatches = urlPattern.findAll(arrayContent)

                urlMatches.forEach { match ->
                    val url = match.groupValues[1]
                    if (url.contains("redirect.php")) {
                        val fullUrl = if (url.startsWith("http")) url else "$hostUrl/$url"
                        urls.add(fullUrl)
                    }
                }
            }
        }

        return urls
    }

    private fun determineResolution(serverName: String): String {
        return when {
            serverName.contains("1080") -> "1080p"
            serverName.contains("720") -> "720p"
            serverName.contains("480") -> "480p"
            serverName.contains("360") -> "360p"
            else -> "720p" // Default
        }
    }

    private fun determineAudioType(serverName: String): AudioType {
        return if (serverName.contains("Latino", ignoreCase = true) ||
            serverName.contains("Español", ignoreCase = true) ||
            serverName.contains("Castellano", ignoreCase = true) ||
            serverName.contains("DUB", ignoreCase = true)) {
            AudioType.DUB
        } else {
            AudioType.SUB
        }
    }

    override suspend fun extractVideo(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extracting video from: ${url.take(100)}...")
                if (url.endsWith(".mp4") || url.endsWith(".m3u8") ||
                    url.endsWith(".webm") || url.endsWith(".mpd")) {
                    return@withContext url
                }
                if (url.contains("redirect.php")) {
                    val redirectUrl = url
                    val doc = Utils.getJsoup(redirectUrl, getDefaultHeaders())
                    val iframeSrc = doc.selectFirst("iframe")?.attr("src")
                    if (!iframeSrc.isNullOrBlank()) {
                        Log.d(TAG, "Found iframe src: $iframeSrc")
                        return@withContext extractVideo(iframeSrc)
                    }
                    val videoSource = doc.selectFirst("video source")
                    val videoUrl = videoSource?.attr("src")
                    if (!videoUrl.isNullOrBlank()) {
                        Log.d(TAG, "Found direct video source: $videoUrl")
                        return@withContext videoUrl
                    }
                    val scripts = doc.select("script")
                    for (script in scripts) {
                        val scriptContent = script.html()
                        if (scriptContent.contains("file") || scriptContent.contains("sources")) {
                            val patterns = listOf(
                                "\"file\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                                "\"src\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                                "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                                "file:\\s*[\"']([^\"']+)[\"']".toRegex(),
                                "sources\\s*:\\s*\\[\\s*\\{[^}]*src\\s*:\\s*\"([^\"]+)\"".toRegex()
                            )

                            for (pattern in patterns) {
                                val match = pattern.find(scriptContent)
                                if (match != null) {
                                    val foundUrl = match.groupValues[1]
                                    if (foundUrl.isNotBlank()) {
                                        Log.d(TAG, "Found video URL in script: ${foundUrl.take(100)}...")
                                        return@withContext foundUrl
                                    }
                                }
                            }
                        }
                    }
                    val finalUrl = doc.location()
                    if (finalUrl != redirectUrl && !finalUrl.contains("redirect.php")) {
                        Log.d(TAG, "Final URL after redirect: $finalUrl")
                        return@withContext finalUrl
                    }
                }

                Log.w(TAG, "Could not extract video from URL")
                return@withContext url
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video: ${e.message}")
                url
            }
        }
    }

}
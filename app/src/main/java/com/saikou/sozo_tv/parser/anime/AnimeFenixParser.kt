package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.extractor.Extractor
import com.saikou.sozo_tv.parser.models.*
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils.getJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeFenixParser : BaseParser() {

    override val name = "AnimeFenix"
    override val saveName = "animefenix"
    override val hostUrl = "https://animefenix2.tv"
    override val language = "es"

    companion object {
        private const val TAG = "AnimeFenixProvider"

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
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
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

                val encodedQuery = query.replace(" ", "%20")
                val url = "$hostUrl/directorio/anime?q=$encodedQuery"
                Log.d(TAG, "Searching url: $url")

                val doc = getJsoup(url, getDefaultHeaders())
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
                val fullUrl = if (href.startsWith("http")) href else "$hostUrl$href"
                val title = titleElement?.text()?.trim() ?: ""

                val poster = imageElement?.let { img ->
                    val src = img.attr("data-src").ifBlank { img.attr("src") }
                    when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> "$hostUrl$src"
                        src.startsWith("http") -> src
                        else -> ""
                    }
                } ?: ""

                val isMovie = item.selectFirst(".main-img") != null

                ShowResponse(
                    name = title,
                    link = fullUrl,
                    coverUrl = poster,
                    otherNames = emptyList(),
                    extra = mapOf(
                        "type" to if (isMovie) "movie" else "tv",
                        "full_url" to fullUrl,
                        "is_movie" to isMovie.toString()
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

                val doc = getJsoup(id, getDefaultHeaders())
                val episodes = parseEpisodes(doc)

                // EpisodeData ga o'rab qaytarish
                EpisodeData(
                    current_page = 1,
                    data = episodes,
                    from = 1,
                    last_page = 1,
                    next_page_url = null,
                    per_page = episodes.size,
                    prev_page_url = null,
                    to = episodes.size,
                    total = episodes.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes: ${e.message}")
                null
            }
        }
    }

    private fun parseEpisodes(doc: Document): List<Data> {
        val episodes = mutableListOf<Data>()

        val episodeElements = doc.select(".divide-y li > a")
        episodeElements.forEachIndexed { index, element ->
            try {
                val titleEp =
                    element.selectFirst(".font-semibold")?.text() ?: "Episode ${index + 1}"
                val href = element.attr("href")
                val fullUrl = if (href.startsWith("http")) href else "$hostUrl$href"

                val episodeNumber = try {
                    titleEp.substringAfter("Episodio ").toIntOrNull() ?: (index + 1)
                } catch (e: Exception) {
                    index + 1
                }

                episodes.add(
                    Data(
                        id = episodes.size + 1,
                        anime_id = episodes.size + 1000,
                        title = titleEp,
                        episode = episodeNumber,
                        episode2 = episodeNumber,
                        session = fullUrl,
                        season = 1,
                        serverId = fullUrl
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing episode: ${e.message}")
            }
        }

        return episodes.sortedByDescending { it.episode ?: 0 }
    }

    override suspend fun getEpisodeVideo(id: String, epId: String): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting video options for episode: $epId")

                val doc = getJsoup(epId, getDefaultHeaders())
                val servers = parseServers(doc)
                Log.d(TAG, "getEpisodeVideo: ${servers}")
                servers.map { server ->
                    VideoOption(
                        videoUrl = server.src,
                        fansub = server.name,
                        resolution = "HD",
                        audioType = AudioType.SUB,
                        quality = "",
                        isActive = true,
                        mimeTypes = MimeTypes.APPLICATION_M3U8,
                        fullText = server.name,
                        tracks = emptyList(),
                        headers = emptyMap()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video options: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseServers(doc: Document): List<Video.Server> {
        val servers = mutableListOf<Video.Server>()

        try {
            val serverElements = doc.select(".episode-page__servers-list li a")
            val script = doc.selectFirst("script:containsData(var tabsArray)")

            if (script != null && serverElements.isNotEmpty()) {
                val names = serverElements.map { a ->
                    a.select("span").last()?.text()?.trim() ?: "Server ${servers.size + 1}"
                }
                val scriptData = script.data()
                val urls = if (scriptData.contains("src='")) {
                    scriptData
                        .substringAfter("<iframe").split("src='")
                        .drop(1)
                        .map { it.substringBefore("'").substringAfter("redirect.php?id=").trim() }
                } else {
                    // Alternative pattern
                    scriptData.split("src=\"").drop(1).map {
                        it.substringBefore("\"").substringAfter("redirect.php?id=").trim()
                    }
                }

                val count = minOf(urls.size, names.size)
                for (i in 0 until count) {
                    val name = names[i].ifBlank { "Server ${i + 1}" }
                    val urlValue = urls[i]
                    if (urlValue.isBlank()) {
                        continue
                    }
                    servers.add(Video.Server(name = name, src = urlValue.toString(), id = urlValue.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing servers: ${e.message}")
        }

        return servers
    }

    override suspend fun extractVideo(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extracting video from: $url")
                val video = Extractor.extract(url)
                val videoUrl = when {
                    video.source.isNotEmpty() -> video.source
                    else -> throw Exception("No video URL found")
                }
                Log.d(TAG, "Extracted video URL: $videoUrl")
                videoUrl
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video: ${e.message}")
                throw e
            }
        }
    }

    }

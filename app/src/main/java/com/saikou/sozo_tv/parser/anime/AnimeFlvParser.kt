package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.extractor.Extractor
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.Utils.getJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeFlvParser : BaseParser() {

    override val name = "AnimeFLV"
    override val saveName = "AnimeFlv"
    override val hostUrl = "https://www3.animeflv.net"
    override val language = "es"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val TAG = "AnimeFlvParser"

        private val genres = listOf(
            "accion" to "Acción",
            "artes-marciales" to "Artes Marciales",
            "aventura" to "Aventuras",
            "carreras" to "Carreras",
            "ciencia-ficcion" to "Ciencia Ficción",
            "comedia" to "Comedia",
            "demencia" to "Demencia",
            "demonios" to "Demonios",
            "deportes" to "Deportes",
            "drama" to "Drama",
            "ecchi" to "Ecchi",
            "escolares" to "Escolares",
            "espacial" to "Espacial",
            "fantasia" to "Fantasía",
            "harem" to "Harem",
            "historico" to "Histórico",
            "infantil" to "Infantil",
            "josei" to "Josei",
            "juegos" to "Juegos",
            "magia" to "Magia",
            "mecha" to "Mecha",
            "militar" to "Militar",
            "misterio" to "Misterio",
            "musica" to "Música",
            "parodia" to "Parodia",
            "policia" to "Policía",
            "psicologico" to "Psicológico",
            "recuentos-de-la-vida" to "Recuentos de la vida",
            "romance" to "Romance",
            "samurai" to "Samurai",
            "seinen" to "Seinen",
            "shoujo" to "Shojo",
            "shounen" to "Shounen",
            "sobrenatural" to "Sobrenatural",
            "superpoderes" to "Superpoderes",
            "suspenso" to "Suspenso",
            "terror" to "Terror",
            "vampiros" to "Vampiros",
            "yaoi" to "Yaoi",
            "yuri" to "Yuri"
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

    private fun getEpisodePoster(animeId: String, episodeNum: String): String {
        return "https://cdn.animeflv.net/screenshots/$animeId/$episodeNum/th_3.jpg"
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

                val url = "$hostUrl/browse?q=${query.replace(" ", "%20")}"
                Log.d(TAG, "Searching url: $url")

                val doc = getJsoup(url, getDefaultHeaders())
                parseShows(doc.select("ul.ListAnimes li article"))
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseShows(elements: List<Element>): List<ShowResponse> {
        return elements.mapNotNull { element ->
            try {
                val url = element.selectFirst("div.Description a.Button")?.attr("href")
                    ?: return@mapNotNull null
                val id = url.substringAfterLast("/")
                val title = element.selectFirst("a h3")?.text() ?: ""
                val posterUrl = element.selectFirst("a div.Image figure img")?.attr("src")
                val type = element.selectFirst("span.Type")?.text()

                val finalPoster = if (posterUrl?.startsWith("http") == true) {
                    posterUrl
                } else {
                    posterUrl?.let { "$hostUrl$it" }
                }

                ShowResponse(
                    name = title,
                    link = id,
                    coverUrl = finalPoster ?: "",
                    otherNames = emptyList(),
                    extra = mapOf(
                        "type" to if (type == "Película") "movie" else "tv",
                        "is_movie" to (type == "Película").toString(),
                        "full_url" to "$hostUrl/anime/$id"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing show: ${e.message}")
                null
            }
        }
    }

    override suspend fun loadEpisodes(
        id: String, page: Int, showResponse: ShowResponse
    ): EpisodeData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading episodes for: $id, page: $page")
                Log.d(TAG, "loadEpisodes: ${hostUrl}/anime/$id")

                val doc = getJsoup("$hostUrl/anime/$id", getDefaultHeaders())
                val episodes = parseEpisodes(doc)
                Log.d(TAG, "loadEpisodes: ${episodes}")
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
        try {
            val script =
                doc.select("script").firstOrNull { it.data().contains("var episodes =") }?.data()
                    ?: return emptyList()

            val episodesData = script.substringAfter("var episodes = [").substringBefore("];")
            val animeInfoJson =
                Regex("""var\s+anime_info\s*=\s*(\[[^\]]+])""").find(script)?.groupValues?.get(1)
            val animeInfo = animeInfoJson?.let { json.decodeFromString<List<String>>(it) }
            val animeId = animeInfo?.getOrNull(0) ?: ""
            val animeUri = animeInfo?.getOrNull(2) ?: ""

            val episodeList = episodesData.split("],[").mapNotNull { episodeStr ->
                val parts = episodeStr.replace("[", "").replace("]", "").split(",")
                if (parts.size >= 2) {
                    val episodeNum = parts[0].trim()
                    val episodeId = parts[1].trim()
                    Pair(episodeNum, episodeId)
                } else null
            }

            episodeList.forEachIndexed { index, (episodeNum, episodeId) ->
                episodes.add(
                    Data(
                        id = episodeId.toIntOrNull() ?: (index + 1),
                        anime_id = animeId.toIntOrNull() ?: 0,
                        title = "Episodio $episodeNum",
                        episode = episodeNum.toIntOrNull(),
                        episode2 = episodeNum.toIntOrNull(),
                        session = "$hostUrl/ver/$animeUri-$episodeNum",
                        season = 1,
                        serverId = "$hostUrl/ver/$animeUri-$episodeNum",
                        snapshot = getEpisodePoster(animeId, episodeNum)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing episodes: ${e.message}")
        }

        return episodes.sortedByDescending { it.episode ?: 0 }
    }

    override suspend fun getEpisodeVideo(id: String, epId: String, epNum: Int): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting video options for episode: $id, epId: $epId")

                val episodeUrl = if (epId.startsWith("http")) epId else "$hostUrl/ver/$id"
                val doc = getJsoup(episodeUrl, getDefaultHeaders())
                val servers = parseServers(doc)
                Log.d(TAG, "getEpisodeVideo: ${servers}")

                servers.mapNotNull { server ->
                    if (server.name.equals("Sw", ignoreCase = true)) {
                        VideoOption(
                            videoUrl = server.src,
                            fansub = server.name,
                            resolution = "HD",
                            audioType = if (server.name.contains(
                                    "(Latino)", ignoreCase = true
                                ) || server.name.contains("(Español)", ignoreCase = true)
                            ) AudioType.DUB else AudioType.SUB,
                            quality = "",
                            isActive = true,
                            mimeTypes = MimeTypes.APPLICATION_M3U8,
                            fullText = server.name,
                            tracks = emptyList(),
                            headers = emptyMap()
                        )
                    } else {
                        null
                    }
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
            val scriptElements = doc.select("script")

            for (script in scriptElements) {
                val scriptData = script.data()
                if (scriptData.contains("var videos =")) {
                    val regex =
                        Regex("var videos\\s*=\\s*(\\{.*?\\});", RegexOption.DOT_MATCHES_ALL)
                    val matchResult = regex.find(scriptData)

                    if (matchResult != null) {
                        val jsonString = matchResult.groupValues[1]
                        Log.d(TAG, "Parsed JSON string: $jsonString")

                        try {
                            val jsonObject = org.json.JSONObject(jsonString)
                            if (jsonObject.has("SUB")) {
                                val subArray = jsonObject.getJSONArray("SUB")
                                for (i in 0 until subArray.length()) {
                                    val item = subArray.getJSONObject(i)
                                    val code = item.optString("code", "")
                                    if (code.isNotBlank()) {
                                        val title = item.optString("title", "")
                                        val serverName = item.optString("server", "")

                                        val name = when {
                                            title.isNotBlank() -> title
                                            serverName.isNotBlank() -> serverName
                                            else -> "Server ${i + 1}"
                                        }

                                        servers.add(
                                            Video.Server(
                                                id = code,
                                                name = name,
                                                src = code,
                                                fileName = "",
                                                fileSize = ""
                                            )
                                        )
                                    }
                                }
                            }

                            if (jsonObject.has("DUB")) {
                                val dubArray = jsonObject.getJSONArray("DUB")
                                for (i in 0 until dubArray.length()) {
                                    val item = dubArray.getJSONObject(i)
                                    val code = item.optString("code", "")
                                    if (code.isNotBlank()) {
                                        val title = item.optString("title", "")
                                        val serverName = item.optString("server", "")

                                        val name = when {
                                            title.isNotBlank() -> "DUB - $title"
                                            serverName.isNotBlank() -> "DUB - $serverName"
                                            else -> "DUB Server ${i + 1}"
                                        }

                                        servers.add(
                                            Video.Server(
                                                id = code,
                                                name = name,
                                                src = code,
                                                fileName = "",
                                                fileSize = ""
                                            )
                                        )
                                    }
                                }
                            }

                            if (jsonObject.has("LAT")) {
                                val latArray = jsonObject.getJSONArray("LAT")
                                for (i in 0 until latArray.length()) {
                                    val item = latArray.getJSONObject(i)
                                    val code = item.optString("code", "")
                                    if (code.isNotBlank()) {
                                        val title = item.optString("title", "")
                                        val serverName = item.optString("server", "")

                                        val name = when {
                                            title.isNotBlank() -> "LAT - $title"
                                            serverName.isNotBlank() -> "LAT - $serverName"
                                            else -> "LAT Server ${i + 1}"
                                        }

                                        servers.add(
                                            Video.Server(
                                                id = code,
                                                name = name,
                                                src = code,
                                                fileName = "",
                                                fileSize = ""
                                            )
                                        )
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing JSON: ${e.message}")
                            continue
                        }

                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing servers: ${e.message}", e)
        }

        return servers
    }

    override suspend fun extractVideo(url: String): Video {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extracting video from: $url")
                val video = Extractor.extract(url)
                video
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video: ${e.message}")
                throw e
            }
        }
    }

}

package com.saikou.sozo_tv.parser.extractor.videasy

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saikou.sozo_tv.parser.extractor.Extractor
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class VideasyExtractor : Extractor() {
    override val name = "VidEasy"
    override val mainUrl = "https://api.videasy.net"

    data class ServerConfig(
        val name: String,
        val endpoint: String,
        val movieOnly: Boolean = false
    )

    private val englishServers = listOf(
        ServerConfig("Neon", "myflixerzupcloud"),
        ServerConfig("Cypher", "moviebox"),
        ServerConfig("Sage", "1movies"),
        ServerConfig("Jett", "primesrcme"),
        ServerConfig("Reyna", "primewire"),
        ServerConfig("Breach", "m4uhd"),
        ServerConfig("Vyse", "hdmovie")
    )

    fun servers(videoType: Video.Type, language: String = "en"): List<Video.Server> {
        return when (language) {
            "en" -> {
                englishServers.mapNotNull { config ->
                    if (config.movieOnly && videoType !is Video.Type.Movie) return@mapNotNull null

                    val url = when (videoType) {
                        is Video.Type.Movie -> {
                            "$mainUrl/${config.endpoint}/sources-with-title?mediaType=movie&tmdbId=${videoType.id}"
                        }

                        is Video.Type.Episode -> {
                            "$mainUrl/${config.endpoint}/sources-with-title?mediaType=tv&tmdbId=${videoType.tvShow.id}&episodeId=${videoType.number}&seasonId=${videoType.season.number}"
                        }
                    }

                    Video.Server(
                        id = "${config.name} (Videasy)",
                        name = "${config.name} (Videasy)",
                        src = url
                    )
                }
            }

            else -> {
                val serverName = when (language) {
                    "de" -> "Killjoy (Videasy)"
                    "it" -> "Harbor (Videasy)"
                    "fr" -> if (videoType is Video.Type.Movie) "Chamber (Videasy)" else return emptyList()
                    "es" -> "Kayo (Videasy)"
                    else -> return emptyList()
                }

                val videasyLang = when (language) {
                    "de" -> "german"
                    "it" -> "italian"
                    "fr" -> "french"
                    "es" -> "spanish"
                    else -> return emptyList()
                }

                val endpoint = when (language) {
                    "es" -> "cuevana-spanish"
                    else -> "meine"
                }

                val url = when (videoType) {
                    is Video.Type.Movie -> {
                        "$mainUrl/$endpoint/sources-with-title?mediaType=movie&tmdbId=${videoType.id}&language=$videasyLang"
                    }

                    is Video.Type.Episode -> {
                        "$mainUrl/$endpoint/sources-with-title?mediaType=tv&tmdbId=${videoType.tvShow.id}&episodeId=${videoType.number}&seasonId=${videoType.season.number}&language=$videasyLang"
                    }
                }

                listOf(
                    Video.Server(
                        id = serverName,
                        name = serverName,
                        src = url
                    )
                )
            }
        }
    }

    override fun server(videoType: Video.Type): Video.Server {
        return servers(videoType).firstOrNull()!!
    }

    override suspend fun extract(link: String): Video {
        val encData = Utils.get(
            url = link,
            mapOfHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
            )
        )

        val tmdbId = link.split("tmdbId=").getOrNull(1)?.split("&")?.getOrNull(0) ?: ""

        val json = JsonObject()
        json.addProperty("text", encData)
        json.addProperty("id", tmdbId)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val decRequest = Request.Builder()
            .url("https://enc-dec.app/api/dec-videasy")
            .post(body)
            .build()

        val decResponse = Utils.httpClient.newCall(decRequest).execute()
        val decBody = decResponse.body.string() ?: "{}"
        val decJson = JsonParser.parseString(decBody).asJsonObject
        val resultJson = decJson.getAsJsonObject("result")
        val sources = resultJson.getAsJsonArray("sources")

        val subtitles = mutableListOf<Video.Subtitle>()
        val tracks = resultJson.getAsJsonArray("subtitles")
        if (tracks != null) {
            for (i in 0 until tracks.size()) {
                val track = tracks[i].asJsonObject
                val label = track.get("lang")?.asString ?: "Unknown"
                val url = track.get("url")?.asString ?: ""
                if (url.isNotEmpty()) {
                    subtitles.add(Video.Subtitle(label = label, url = url))
                }
            }
        }

        if (sources != null && sources.size() > 0) {
            val bestSource = sources.map { it.asJsonObject }
                .maxByOrNull {
                    it.get("quality")?.asString?.replace("p", "")?.toIntOrNull() ?: 0
                } ?: sources[0].asJsonObject

            val url = bestSource.get("url")?.asString ?: ""
            val headers = if (url.contains("paradise")) {
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Referer" to "https://www.vidking.net/",
                    "Origin" to "https://www.vidking.net/",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                )
            } else {
                mapOf("Referer" to "https://videasy.net")
            }

            Log.d("GGG", "extract:${headers} || $url")
            return Video(
                source = url,
                subtitles = subtitles,
                headers = headers
            )
        }

        throw Exception("Video manba topilmadi")

    }
}

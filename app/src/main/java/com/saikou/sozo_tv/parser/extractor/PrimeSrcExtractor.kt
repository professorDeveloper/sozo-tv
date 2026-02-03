package com.saikou.sozo_tv.parser.extractor

import android.annotation.SuppressLint
import android.util.Log
import com.google.gson.Gson
import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils

class PrimeSrcExtractor : Extractor() {

    override val name = "PrimeSrc"
    override val mainUrl = "https://primesrc.me"
    private val gson = Gson()

    @SuppressLint("NewApi")
    fun servers(videoType: Video.Type): List<Video.Server> {
        val apiUrl = when (videoType) {
            is Video.Type.Episode -> "$mainUrl/api/v1/s?tmdb=${videoType.tvShow.id}&season=${videoType.season.number}&episode=${videoType.number}&type=tv"
            is Video.Type.Movie -> "$mainUrl/api/v1/s?tmdb=${videoType.id}&type=movie"
        }

        return try {
            val headers = mapOf(
                "User-Agent" to USER_AGENT, "Accept" to "application/json", "Referer" to mainUrl
            )

            val response = Utils.get(apiUrl, headers)
            println(apiUrl)
            println(response)
            val serversResponse = gson.fromJson(response, ServersResponse::class.java)

            val nameCount = mutableMapOf<String, Int>()

            serversResponse.servers.map { server ->
                val count = nameCount.getOrDefault(server.name, 0) + 1
                nameCount[server.name] = count

                val suffix = if (count > 1) " $count" else ""
                val displayName = "${server.name}$suffix (PrimeSrc)"

                Video.Server(
                    id = "${server.name}-${server.key} (PrimeSrc)",
                    name = displayName,
                    src = "$mainUrl/api/v1/l?key=${server.key}"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }


    override fun server(videoType: Video.Type): Video.Server {
        val servers = servers(videoType)
        Log.d("GGG", "servers:${servers} ")
        val firstOrNull = servers.filter { it.name.contains("Filemoon") }.lastOrNull(   )
        Log.d("GGG", "server:${firstOrNull} ")
        return firstOrNull ?: throw Exception(
            "No servers found"
        )
    }

    override suspend fun extract(link: String): Video {
        val headers = mapOf(
            "User-Agent" to USER_AGENT, "Accept" to "application/json", "Referer" to mainUrl
        )

        println(link)
        val response = Utils.get(link, headers)
        println(response)
        val linkResponse = gson.fromJson(response, LinkResponse::class.java)

        val videoLink = linkResponse.link ?: throw Exception("No video link found in response")
        return Extractor.extract(videoLink)
    }

    data class ServersResponse(
        val servers: List<Server>
    )

    data class Server(
        val name: String, val key: String
    )

    data class LinkResponse(
        val link: String?
    )
}
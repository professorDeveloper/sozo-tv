package com.saikou.sozo_tv.parser.extractor

import android.annotation.SuppressLint
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import kotlin.math.log

class PrimeSrcExtractor : Extractor() {

    override val name = "PrimeSrc"
    override val mainUrl = "https://primesrc.me"
    private val gson = Gson()

    @SuppressLint("NewApi")
    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val apiUrl = when (videoType) {
            is Video.Type.Episode -> "$mainUrl/api/v1/s?tmdb=${videoType.tvShow.id}&season=${videoType.season.number}&episode=${videoType.number}&type=tv"
            is Video.Type.Movie -> "$mainUrl/api/v1/s?tmdb=${videoType.id}&type=movie"
            else -> throw IllegalArgumentException("Unknown video type")
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
                    src = "$mainUrl/api/v1/l?key=${server.key}",
                    fileName = server.file_name ?: "",
                    fileSize = server.file_size ?: ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        val servers = servers(videoType)
        Log.d("GGG", "PRIMESRC servers:$servers ")
        val pickBestServer = pickBestServer(servers)
        Log.d("GGG", "pickBestServer: ${pickBestServer}")
        return pickBestServer ?: throw Exception("No servers found")
    }

    private fun pickBestServer(servers: List<Video.Server>): Video.Server? {
        val preferredOrder = listOf(
            "Streamtape",
            "Voe",
            "Mixdrop",
            "Filemoon",
            "VidNest",
            "Filelions",
            "PrimeVid",
            "Luluvdoo",
            "UpZur",
            "Streamwish",
            "Streamplay",
            "Savefiles"
        )
        val mp4Servers = servers.filter { isMp4(it.fileName) }
        if (mp4Servers.isNotEmpty()) {
            for (host in preferredOrder) {
                mp4Servers.firstOrNull { it.id.startsWith("$host-") }?.let { return it }
            }
            return mp4Servers.firstOrNull()
        }
        val sizedServers = servers.filter { hasValidSize(it.fileSize) }
        if (sizedServers.isNotEmpty()) {
            for (host in preferredOrder) {
                sizedServers.firstOrNull { it.id.startsWith("$host-") }?.let { return it }
            }
            return sizedServers.firstOrNull()
        }
        return null
    }

    private fun isMp4(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.trim().lowercase()
        return n.endsWith(".mp4") || n.endsWith("mp4")
    }

    private fun hasValidSize(size: String?): Boolean {
        return !size.isNullOrBlank()
    }

    override suspend fun extract(link: String): Video {
        val headers = mapOf(
            "User-Agent" to USER_AGENT, "Accept" to "application/json", "Referer" to mainUrl
        )

        Log.d("ggg", link)
        val response = Utils.get(link, headers)
        val linkResponse = gson.fromJson(response, LinkResponse::class.java)

        val videoLink = linkResponse.link ?: throw Exception("No video link found in response")
        return Extractor.extract(videoLink)
    }

    data class ServersResponse(
        val servers: List<Server>
    )

    data class Server(
        val name: String,
        val key: String,
        @SerializedName("file_name") val file_name: String?,
        @SerializedName("file_size") val file_size: String?
    )

    data class LinkResponse(
        val link: String?
    )
}
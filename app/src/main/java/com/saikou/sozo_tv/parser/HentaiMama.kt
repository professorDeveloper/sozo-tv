package com.saikou.sozo_tv.parser

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.Kiwi
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ResponseElement(
    val type: String,
    val file: String
)

// Additional data classes you'll need:
data class VideoContainer(
    val videos: List<Video>
)

data class Video(
    val id: String?,
    val type: VideoType,
    val url: String,
    val size: Long?
)

data class VideoServer(
    val url: String,
    val index: String,
)

enum class VideoType {
    M3U8,
    CONTAINER
}

class HentaiMama : BaseParser() {
    override val name: String = "hentaimama"
    override val saveName: String = "hentai_mama"
    override val hostUrl = "https://hentaimama.io"
    override val isNSFW: Boolean = true
    override val language: String = "jp"

    var httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)

        .callTimeout(2, TimeUnit.MINUTES)
        .build()
    val client = Requests(httpClient, responseParser = parser)

    suspend fun search(query: String): List<ShowResponse>  {
        val updatedQuery = if (query.length > 7) query.substring(0, 7) else query
        val url = "$hostUrl/?s=${updatedQuery.replace(" ", "+")}"
        val document = Utils.getJsoup(url)

        Log.d("GGG", "search:$document ")

        return document.select("div.result-item article").map {
            val link = it.select("div.details div.title a").attr("href")
            val title = it.select("div.details div.title a").text()
            val cover = it.select("div.image div a img").attr("src")
            ShowResponse(title, link, cover)
        }
    }


    suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Data> {
        val pageBody = Utils.getJsoup(animeLink)

        val episodes =
            pageBody.select("div#episodes.sbox.fixidtab div.module.series div.content.series div.items article")
                .reversed()
                .map { article ->
                    // Extract episode number from the h3 text
                    val epNum = article.select("div.data h3").text().replace("Episode", "").trim()

                    // Extract episode URL from the season_m div (remove .animation-3 class)
                    val url = article.select("div.poster div.season_m a").attr("href")

                    // Extract thumbnail from img data-src attribute
                    val thumb = article.select("div.poster img").attr("data-src")
                    Data(episode = epNum.toInt(), session = url ?: "", snapshot = thumb ?: "")
                }

        return episodes
    }

    suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): Kiwi {
        val client = Requests(Utils.httpClient, responseParser = parser)
        val animeId = client.get(episodeLink).document
            .select("#post_report > input:nth-child(5)")
            .attr("value")

        val response = client.post(
            "https://hentaimama.io/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "get_player_contents",
                "a" to animeId
            )
        ).text

        val gson = Gson()
        val listType = object : TypeToken<List<String>>() {}.type
        val videoUrls: List<String> = gson.fromJson(response, listType)

        // Convert to VideoServer objects
        val videoServers = videoUrls.mapIndexed { index, url ->
            println(url.extractIframeSrc())
            Kiwi(url.extractIframeSrc() ?: "", "Mirror $index", "")
        }

        return videoServers.first()
    }

    private fun String.extractIframeSrc(): String? {
        val srcPattern = """src="([^"]+)"""".toRegex()
        return srcPattern.find(this)?.groupValues?.get(1)
    }

    suspend fun extract(server: Kiwi): Video {
        val client = Requests(Utils.httpClient, responseParser = parser)
        val doc = client.get(server.session)

        doc.document.selectFirst("video>source")?.attr("src")?.let { directSrc ->

            return Video(null, VideoType.CONTAINER, directSrc, getSize(directSrc))
        }

        // Extract sources from JavaScript
        val unSanitized =
            doc.text.findBetween("sources: [", "],") ?: return Video(
                null,
                VideoType.CONTAINER,
                "",
                null
            )

        // Sanitize the JSON string
        val sanitizedJson = "[${
            unSanitized
                .replace("type:", "\"type\":")
                .replace("file:", "\"file\":")
        }]"

        // Parse JSON using Gson
        val gson = Gson()
        val listType = object : TypeToken<List<ResponseElement>>() {}.type
        val json: List<ResponseElement> = gson.fromJson(sanitizedJson, listType)

        // Convert to Video objects
        val videos = json.map { element ->
            if (element.type == "hls")
                Video(null, VideoType.M3U8, element.file, null)
            else
                Video(null, VideoType.CONTAINER, element.file, getSize(element.file))
        }

        return videos.first()
    }

    fun String.findBetween(start: String, end: String): String? {
        val startIndex = this.indexOf(start)
        if (startIndex == -1) return null

        val actualStart = startIndex + start.length
        val endIndex = this.indexOf(end, actualStart)
        if (endIndex == -1) return null

        return this.substring(actualStart, endIndex)
    }

    fun getSize(url: String): Long? {
        return null
    }
}
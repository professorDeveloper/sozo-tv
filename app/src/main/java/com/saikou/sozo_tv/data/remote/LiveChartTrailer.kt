package com.saikou.sozo_tv.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.data.model.TrailerModel
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.delay
import okhttp3.Request
import org.jsoup.Jsoup

class LiveChartTrailer() {
    private val BASE_URL = "https://www.livechart.me"
    suspend fun searchAndGetTrailer(animeTitle: String): TrailerModel {
        val niceHttp = Requests(baseClient = Utils.httpClient, responseParser = parser)
        var detailsUrl = ""
        niceHttp.get("$BASE_URL/search?q=$animeTitle").document?.let {
            val firstItem = it.selectFirst("li.grouped-list-item.anime-item")!!
            detailsUrl =
                "https://www.livechart.me" + firstItem.selectFirst("a[data-anime-item-target=mainTitle]")
                    ?.attr("href").orEmpty()
            println(detailsUrl)
        }
        return TrailerModel("$detailsUrl/videos")
    }

    suspend fun getTrailerByDetail(url: String): ArrayList<TrailerModel> {
        val trailers = ArrayList<TrailerModel>()
        val niceHttp = Requests(baseClient = Utils.httpClient, responseParser = parser)

        val doc = niceHttp.get(url).document
        val videoElements = doc.select("div.lc-video a[href^=https://www.youtube.com/watch]")

        for (element in videoElements) {
            val link = element.attr("href") // YouTube link

            trailers.add(
                TrailerModel(
                    link
                )
            )
        }

        return trailers
    }
}

class DubsMp4Parser {

    private val BASE_URL = "https://dubs.io/wp-json/tools/v1"
    private val gson = Gson()

    suspend fun parseYt(link: String): String {
        val videoId = extractYoutubeId(link)
        println(videoId)
        val url = "$BASE_URL/download-video?id=$videoId&format=720"
        val response = Requests(baseClient = Utils.httpClient, responseParser = parser).get(url)

        if (!response.isSuccessful) {
            throw Exception("Request failed: ${response.code}")
        }

        val body = response.body.string() ?: throw Exception("Empty response")
        println("init: $body")
        val json = gson.fromJson(body, JsonObject::class.java)


        if (!json["success"].asBoolean) {
            throw Exception("Download init failed: $body")
        }
        val progressId = json["progressId"].asString

        var downloadUrl: String? = null
        repeat(20) { // maksimal 20 marta tekshiradi (~60 sekund)
            val statusUrl = "$BASE_URL/status-video?id=$progressId"
            val statusResp =
                Utils.httpClient.newCall(Request.Builder().url(statusUrl).build()).execute()
            val statusBody = statusResp.body?.string() ?: ""
            val statusJson = gson.fromJson(statusBody, JsonObject::class.java)

            if (statusJson["finished"].asBoolean) {
                downloadUrl = statusJson["downloadUrl"].asString
                return@repeat
            }

            delay(2000) // 3 sekund kutadi
        }

        return downloadUrl ?: throw Exception("Download URL not ready")
    }

    private fun extractYoutubeId(url: String): String? {
        val regex = Regex("v=([A-Za-z0-9_-]{11})")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)
    }

}


package com.saikou.sozo_tv.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.data.model.TrailerModel
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
        val videoId = extractYoutubeId(link) ?: throw Exception("Invalid YouTube link")
        Log.d("GGG", "VideoId: $videoId")

        var progressId: String? = null

        repeat(40) { attempt -> // ~120s kutish (40*3s)
            // Agar progressId bo‘lmasa yangidan olish
            if (progressId == null) {
                val initUrl = "$BASE_URL/download-video?id=$videoId&format=720"
                val initResp = Requests(baseClient = Utils.httpClient, responseParser = parser).get(initUrl)

                if (!initResp.isSuccessful) throw Exception("Request failed: ${initResp.code}")

                val initBody = initResp.body.string() ?: throw Exception("Empty response")
                val initJson = gson.fromJson(initBody, JsonObject::class.java)

                if (!initJson["success"].asBoolean) throw Exception("Download init failed: $initBody")

                progressId = initJson["progressId"].asString
                Log.d("GGG", "New progressId: $progressId")
            }

            // Statusni tekshirish
            val statusUrl = "$BASE_URL/status-video?id=$progressId"
            val statusResp = withContext(Dispatchers.IO) {
                Utils.httpClient.newCall(Request.Builder().url(statusUrl).build()).execute()
            }
            val statusBody = statusResp.body?.string() ?: ""
            val statusJson = gson.fromJson(statusBody, JsonObject::class.java)

            Log.d("GGG", "Status [$attempt]: $statusBody")

            if (statusJson["finished"]?.asBoolean == true) {
                val downloadUrl = statusJson["downloadUrl"].asString
                Log.d("GGG", "Download ready: $downloadUrl")
                return downloadUrl // 🔥 finished true bo‘lsa loopni to‘xtatamiz
            }

            // progressId yangilansa — update
            if (statusJson.has("progressId") && statusJson["progressId"].asString != progressId) {
                progressId = statusJson["progressId"].asString
                Log.d("GGG", "ProgressId updated: $progressId")
            }

            delay(3000) // 3 sekund kutish
        }

        return ""
    }

    private fun extractYoutubeId(url: String): String? {
        val regex = Regex("v=([A-Za-z0-9_-]{11})")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)
    }

}


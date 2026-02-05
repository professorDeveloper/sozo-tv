package com.saikou.sozo_tv.data.remote

import android.util.Log
import com.bugsnag.android.Bugsnag
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
import java.util.concurrent.CancellationException

class LiveChartTrailer {
    private val BASE_URL = "https://www.livechart.me"
    suspend fun searchAndGetTrailer(animeTitle: String): TrailerModel {
        val niceHttp = Requests(baseClient = Utils.httpClient, responseParser = parser)
        var detailsUrl = ""
        niceHttp.get("$BASE_URL/search?q=$animeTitle").document.let {
            val firstItem = it.selectFirst("li.grouped-list-item.anime-item") ?: return@let
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
        if (url.startsWith("http")) {
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
            //

            return trailers
        } else {
            return trailers
        }
    }
}

class DubsMp4Parser {

    private val BASE_URL = "https://dubs.io/wp-json/tools/v1"
    private val gson = Gson()

    suspend fun parseYt(link: String): String {
        val videoId = extractYoutubeId(link) ?: throw Exception("Invalid YouTube link")
        Log.d("GGG", "VideoId: $videoId")

        var progressId: String? = null

        repeat(40) { attempt ->
            if (progressId == null) {
                val initUrl = "$BASE_URL/download-video?id=$videoId&format=720"
                val initResp =
                    Requests(baseClient = Utils.httpClient, responseParser = parser).get(initUrl)

                if (!initResp.isSuccessful) throw Exception("Request failed: ${initResp.code}")

                val initBody = initResp.body.string()
                val initJson = gson.fromJson(initBody, JsonObject::class.java)

                Log.d("GGG", "Init response: $initBody")

                // 🔥 Agar message bo‘lsa va "Something went wrong" bo‘lsa — ishni to‘xtatamiz
                if (initJson.has("message") && initJson["message"].asString.contains(
                        "Something went wrong",
                        true
                    )
                ) {
                    throw CancellationException("Download cancelled: ${initJson["message"].asString}")
                }

                if (!initJson.has("success") || !initJson["success"].asBoolean) {
                    Bugsnag.notify(Exception("Download failed: $initBody"))
                }

                progressId = initJson["progressId"].asString
                Log.d("GGG", "New progressId: $progressId")
            }

            // Statusni tekshirish
            val statusUrl = "$BASE_URL/status-video?id=$progressId"
            val statusResp = withContext(Dispatchers.IO) {
                Utils.httpClient.newCall(Request.Builder().url(statusUrl).build()).execute()
            }
            val statusBody = statusResp.body.string()
            val statusJson = gson.fromJson(statusBody, JsonObject::class.java)

            Log.d("GGG", "Status [$attempt]: $statusBody")

            if (statusJson.has("message") && statusJson["message"].asString.contains(
                    "Something went wrong",
                    true
                )
            ) {
                throw CancellationException("Status cancelled: ${statusJson["message"].asString}")
            }

            if (statusJson["finished"]?.asBoolean == true) {
                val downloadUrl = statusJson["downloadUrl"].asString
                Log.d("GGG", "Download ready: $downloadUrl")
                return downloadUrl
            }

            if (statusJson.has("progressId") && statusJson["progressId"].asString != progressId) {
                progressId = statusJson["progressId"].asString
                Log.d("GGG", "ProgressId updated: $progressId")
            }

            delay(3000)
        }

        return ""
    }

    private fun extractYoutubeId(url: String): String? {
        val regex = Regex("v=([A-Za-z0-9_-]{11})")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)
    }

}


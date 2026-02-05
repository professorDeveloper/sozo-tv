package com.saikou.sozo_tv.aniskip

import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AniSkip {

    private val gson = Gson()

    data class AniSkipResponse(
        val found: Boolean,
        val results: List<Stamp>?,
        val message: String?,
        val statusCode: Int?
    )

    data class Stamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )

    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )

    fun String.getType(): String {
        return when (this) {
            "op" -> "Opening"
            "ed" -> "Ending"
            "recap" -> "Recap"
            "mixed-ed" -> "Mixed Ending"
            "mixed-op" -> "Mixed Opening"
            else -> this
        }
    }

    fun getResult(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long,
        useProxyForTimeStamps: Boolean = false
    ): List<Stamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?" +
                    "types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"

        return try {
            val responseText = if (useProxyForTimeStamps) {
                val encoded = URLEncoder.encode(url, "utf-8").replace("+", "%20")
                fetchUrl("https://corsproxy.io/?$encoded")
            } else {
                fetchUrl(url)
            }

            val parsed = gson.fromJson(responseText, AniSkipResponse::class.java)
            val stamps = if (parsed.found) parsed.results else null
            stamps
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

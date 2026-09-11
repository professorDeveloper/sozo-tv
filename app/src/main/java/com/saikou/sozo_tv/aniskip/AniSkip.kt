package com.saikou.sozo_tv.aniskip

import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

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

    /**
     * Skip times for one episode, straight from api.aniskip.com. Requests used to go through
     * corsproxy.io — a browser workaround this native client never needed, which handed a third
     * party every title and episode watched and failed whenever that proxy did.
     */
    fun getResult(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long,
    ): List<Stamp>? {
        if (malId <= 0 || episodeNumber <= 0) return null
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?" +
                    "types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"

        return try {
            val responseText = fetchUrl(url)

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

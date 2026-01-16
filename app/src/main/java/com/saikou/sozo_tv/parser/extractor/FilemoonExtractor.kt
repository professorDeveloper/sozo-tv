package com.saikou.sozo_tv.parser.extractor

import android.util.Base64
import com.google.gson.Gson
import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

open class FilemoonExtractor : Extractor() {

    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.site"
    override val aliasUrls = listOf("https://bf0skv.org", "https://bysejikuar.com")
    private val gson = Gson()

    override suspend fun extract(link: String): Video {
        val matcher = Regex("""/(e|d)/([a-zA-Z0-9]+)""").find(link)
            ?: throw Exception("Could not extract video ID or type")

        val linkType = matcher.groupValues[1]
        val videoId = matcher.groupValues[2]

        val currentDomain = Regex("""(https?://[^/]+)""").find(link)?.groupValues?.get(1)
            ?: throw Exception("Could not extract Base URL")

        val detailsUrl = "$currentDomain/api/videos/$videoId/embed/details"
        val details = getDetails(detailsUrl)
        val embedFrameUrl = details.embed_frame_url
            ?: throw Exception("embed_frame_url not found")

        var playbackDomain = ""
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = USER_AGENT
        headers["Accept"] = "application/json"

        if (linkType == "d") {
            playbackDomain = currentDomain
            headers["Referer"] = link
        } else {
            playbackDomain = Regex("""(https?://[^/]+)""").find(embedFrameUrl)?.groupValues?.get(1)
                ?: throw Exception("Could not extract domain from embed_frame_url")
            headers["Referer"] = embedFrameUrl
            headers["X-Embed-Parent"] = link
        }

        val playbackUrl = "$playbackDomain/api/videos/$videoId/embed/playback"
        val playbackResponse = getPlayback(playbackUrl, headers)
        val playbackData = playbackResponse.playback
            ?: throw Exception("No playback data")

        val decryptedJson = decryptPlayback(playbackData)

        val jsonObject = JSONObject(decryptedJson)
        val sources = jsonObject.optJSONArray("sources")
            ?: throw Exception("No sources found in decrypted data")

        if (sources.length() == 0) throw Exception("Empty sources list")

        val sourceUrl = sources.getJSONObject(0).getString("url")

        return Video(
            source = sourceUrl,
            headers = mapOf(
                "Referer" to "$playbackDomain/",
                "User-Agent" to USER_AGENT,
                "Origin" to playbackDomain
            )
        )
    }

    private suspend fun getDetails(url: String): DetailsResponse {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json"
        )
        val response = Utils.get(url, headers)
        return gson.fromJson(response, DetailsResponse::class.java)
    }

    private suspend fun getPlayback(url: String, headers: Map<String, String>): PlaybackResponse {
        val response = Utils.get(url, headers)
        return gson.fromJson(response, PlaybackResponse::class.java)
    }

    private fun decryptPlayback(data: PlaybackData): String {
        val iv = Base64.decode(data.iv, Base64.URL_SAFE)
        val payload = Base64.decode(data.payload, Base64.URL_SAFE)
        val p1 = Base64.decode(data.key_parts[0], Base64.URL_SAFE)
        val p2 = Base64.decode(data.key_parts[1], Base64.URL_SAFE)

        val key = ByteArray(p1.size + p2.size)
        System.arraycopy(p1, 0, key, 0, p1.size)
        System.arraycopy(p2, 0, key, p1.size, p2.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        val secretKey = SecretKeySpec(key, "AES")

        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(payload)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    class Any(hostUrl: String) : FilemoonExtractor() {
        override val mainUrl = hostUrl
    }

    data class DetailsResponse(val embed_frame_url: String?)
    data class PlaybackResponse(val playback: PlaybackData?)
    data class PlaybackData(
        val iv: String,
        val payload: String,
        val key_parts: List<String>
    )
}
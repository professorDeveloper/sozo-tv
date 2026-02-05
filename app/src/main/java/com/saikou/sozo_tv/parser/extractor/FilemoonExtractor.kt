package com.saikou.sozo_tv.parser.extractor

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import java.util.Base64
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
        val embedFrameUrl = details.embed_frame_url ?: throw Exception("embed_frame_url not found")

        val headers = mutableMapOf<String, String>(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json"
        )

        val playbackDomain: String = if (linkType == "d") {
            headers["Referer"] = link
            currentDomain
        } else {
            val domain = Regex("""(https?://[^/]+)""").find(embedFrameUrl)?.groupValues?.get(1)
                ?: throw Exception("Could not extract domain from embed_frame_url")
            headers["Referer"] = embedFrameUrl
            headers["X-Embed-Parent"] = link
            domain
        }

        val playbackUrl = "$playbackDomain/api/videos/$videoId/embed/playback"
        val playbackResponse = getPlayback(playbackUrl, headers)
        val playbackData = playbackResponse.playback ?: throw Exception("No playback data")

        val decryptedJson = decryptPlayback(playbackData)

        val decrypted = gson.fromJson(decryptedJson, DecryptedPlayback::class.java)
            ?: throw Exception("Failed to parse decrypted JSON")

        val sources = decrypted.sources ?: throw Exception("No sources found in decrypted data")
        if (sources.isEmpty()) throw Exception("Empty sources list")

        val sourceUrl = sources[0].url ?: throw Exception("Source url missing")

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

    private fun padBase64(s: String): String {
        val m = s.length % 4
        return if (m == 0) s else s + "=".repeat(4 - m)
    }

    @SuppressLint("NewApi")
    private fun decryptPlayback(data: PlaybackData): String {
        val decoder = Base64.getUrlDecoder()

        val iv = decoder.decode(padBase64(data.iv))
        val payload = decoder.decode(padBase64(data.payload))
        val p1 = decoder.decode(padBase64(data.key_parts[0]))
        val p2 = decoder.decode(padBase64(data.key_parts[1]))

        val key = ByteArray(p1.size + p2.size)
        System.arraycopy(p1, 0, key, 0, p1.size)
        System.arraycopy(p2, 0, key, p1.size, p2.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        val secretKey = SecretKeySpec(key, "AES")

        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(payload)
        return decryptedBytes.toString(Charsets.UTF_8)
    }

    class Any(hostUrl: String) : FilemoonExtractor() {
        override val mainUrl = hostUrl
    }

    // API models
    data class DetailsResponse(val embed_frame_url: String?)
    data class PlaybackResponse(val playback: PlaybackData?)
    data class PlaybackData(
        val iv: String,
        val payload: String,
        val key_parts: List<String>
    )

    // Decrypted JSON model
    data class DecryptedPlayback(
        val sources: List<DecryptedSource>?,
        val tracks: List<JsonElement>? = null, // IMPORTANT: not FilemoonExtractor.Any !
        @SerializedName("poster_url") val posterUrl: String? = null,
        @SerializedName("generated_at") val generatedAt: String? = null,
        @SerializedName("expires_at") val expiresAt: String? = null
    )

    data class DecryptedSource(
        val quality: String? = null,
        val label: String? = null,
        @SerializedName("mime_type") val mimeType: String? = null,
        val url: String? = null,
        @SerializedName("bitrate_kbps") val bitrateKbps: Int? = null,
        val height: Int? = null,
        @SerializedName("size_bytes") val sizeBytes: Long? = null
    )
}

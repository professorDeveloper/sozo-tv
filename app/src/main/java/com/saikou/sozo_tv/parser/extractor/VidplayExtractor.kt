package com.saikou.sozo_tv.parser.extractor

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import java.net.URLDecoder
import kotlin.experimental.xor

open class VidplayExtractor : Extractor() {

    override val name = "Vidplay"
    override val mainUrl = "https://vidplay.site"
    private val keyUrl = "https://raw.githubusercontent.com/Ciarands/vidsrc-keys/main/keys.json"
    private val gson = Gson()

    override suspend fun extract(link: String): Video {
        val keysJson = Utils.get(keyUrl)
        val keys = gson.fromJson(keysJson, Keys::class.java)
        val cleanLink = link.substringBefore("?")
        val id = if (cleanLink.endsWith("/")) {
            cleanLink.substringBeforeLast("/").substringAfterLast("/")
        } else {
            cleanLink.substringAfterLast("/")
        }
        val encId = encode(keys.encrypt[1], id)
        val h = encode(keys.encrypt[2], id)
        
        val queryParams = if (link.contains("?")) "&${link.substringAfter("?")}" else ""
        val mediaUrl = "${mainUrl}/mediainfo/${encId}?autostart=true${queryParams}&h=${h}"
        val headers = mapOf(
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to link,
            "User-Agent" to USER_AGENT
        )

        val responseJson = Utils.get(mediaUrl, headers)
        val jsonObject = gson.fromJson(responseJson, JsonObject::class.java)
        
        val result = if (jsonObject.has("result") && jsonObject.get("result").isJsonPrimitive) {
            val encryptedResult = jsonObject.get("result").asString
            decryptResult(keys.decrypt[1], encryptedResult)
        } else {
            gson.fromJson(jsonObject.get("result"), Result::class.java)
        }

        val video = Video(
            source = result.sources?.first()?.file
                ?: throw Exception("Can't retrieve source"),
            subtitles = result.tracks
                ?.filter { it.kind == "captions" }
                ?.mapNotNull {
                    Video.Subtitle(
                        it.label ?: "Unknown",
                        it.file ?: return@mapNotNull null,
                    )
                }
                ?: listOf(),
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to USER_AGENT
            )
        )

        return video
    }

    private fun decryptResult(key: String, encryptedResult: String): Result {
        fun decodeBase64UrlSafe(url: String): ByteArray {
            val standardizedInput = url
                .replace('_', '/')
                .replace('-', '+')
            return Base64.decode(standardizedInput, Base64.NO_WRAP)
        }

        fun decodeData(key: String, data: ByteArray): ByteArray {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val s = ByteArray(256) { it.toByte() }
            var j = 0

            for (i in 0 until 256) {
                j = (j + s[i].toInt() + keyBytes[i % keyBytes.size].toInt()) and 0xff
                s[i] = s[j].also { s[j] = s[i] }
            }

            val decoded = ByteArray(data.size)
            var i = 0
            var k = 0

            for (index in decoded.indices) {
                i = (i + 1) and 0xff
                k = (k + s[i].toInt()) and 0xff
                s[i] = s[k].also { s[k] = s[i] }
                val t = (s[i].toInt() + s[k].toInt()) and 0xff
                decoded[index] = (data[index] xor s[t])
            }

            return decoded
        }

        val encoded = decodeBase64UrlSafe(encryptedResult)
        val decoded = decodeData(key, encoded)
        val decodedText = decoded.toString(Charsets.UTF_8)
        val resultJson = URLDecoder.decode(decodedText, "utf-8")
        
        return gson.fromJson(resultJson, Result::class.java)
    }

    companion object {
        private fun encode(key: String, vId: String): String {
            val decodedId = decodeData(key, vId)

            val encodedBase64 = Base64.encode(decodedId, Base64.NO_WRAP).toString(Charsets.UTF_8)

            return encodedBase64
                .replace("/", "_")
                .replace("+", "-")
        }

        private fun decodeData(key: String, data: String): ByteArray {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val s = ByteArray(256) { it.toByte() }
            var j = 0

            for (i in 0 until 256) {
                j = (j + s[i].toInt() + keyBytes[i % keyBytes.size].toInt()) and 0xff
                s[i] = s[j].also { s[j] = s[i] }
            }

            val decoded = ByteArray(data.length)
            var i = 0
            var k = 0

            for (index in decoded.indices) {
                i = (i + 1) and 0xff
                k = (k + s[i].toInt()) and 0xff
                s[i] = s[k].also { s[k] = s[i] }
                val t = (s[i].toInt() + s[k].toInt()) and 0xff

                decoded[index] = (data[index].code xor s[t].toInt()).toByte()
            }

            return decoded
        }
    }

    class Any(hostUrl: String) : VidplayExtractor() {
        override val mainUrl = hostUrl
    }

    class MyCloud : VidplayExtractor() {
        override val name = "MyCloud"
        override val mainUrl = "https://mcloud.bz"
    }

    class VidplayOnline : VidplayExtractor() {
        override val mainUrl = "https://vidplay.online"
    }

    data class Result(
        val sources: List<Source>? = listOf(),
        val tracks: List<Track>? = listOf(),
    ) {
        data class Track(
            val file: String? = null,
            val label: String? = null,
            val kind: String? = null,
        )

        data class Source(
            val file: String? = null,
        )
    }

    data class Keys(
        val encrypt: List<String>,
        val decrypt: List<String>,
    )
}
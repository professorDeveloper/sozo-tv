package com.saikou.sozo_tv.parser.extractor

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.MimeTypes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import okhttp3.Request
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.coroutineScope

class VidzeeExtractor : Extractor() {
    override val name = "Vidzee"
    override val mainUrl = "https://player.vidzee.wtf"
    private val coreApi = "https://core.vidzee.wtf"
    private val staticPass = "b3f2a9d4c6e1f8a7b"

    data class ServerConfig(val name: String, val index: Int)

    private val servers = listOf(
        ServerConfig("Nflix", 0),
        ServerConfig("Glory", 1),
        ServerConfig("Nazy", 2),
        ServerConfig("Atlas", 3),
        ServerConfig("Drag", 4),
        ServerConfig("Achilles", 5),
        ServerConfig("Viet", 6),
        ServerConfig("Hindi", 7),
        ServerConfig("Bengali", 8),
        ServerConfig("Tamil", 9),
        ServerConfig("Tamil", 10),
        ServerConfig("Telugu", 11),
        ServerConfig("Malayalam", 12)
    )

    fun servers(videoType: Video.Type): List<Video.Server> {
        val baseUrl = when (videoType) {
            is Video.Type.Movie -> "$mainUrl/api/server?id=${videoType.id}"
            is Video.Type.Episode -> "$mainUrl/api/server?id=${videoType.tvShow.id}&ss=${videoType.season.number}&ep=${videoType.number}"
        }

        return servers.map { config ->
            Video.Server(
                id = "${config.name} (Vidzee)",
                name = "${config.name} (Vidzee)",
                src = "$baseUrl&sr=${config.index}"
            )
        }
    }

    override fun server(videoType: Video.Type): Video.Server = servers(videoType).first()

    override suspend fun extract(link: String): Video = coroutineScope {
        val masterKey = getMasterKey() ?: throw Exception("Failed to get Vidzee master key")

        try {
            Log.d(
                "GGG", "extract:${
                    link
                } "
            )
            val request = Request.Builder()
                .url(link)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                )
                .header("Origin", mainUrl)
                .header("Referer", "$mainUrl/")
                .build()

            val response = Utils.httpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Network error: HTTP ${response.code}")

            val body = response.body?.string() ?: throw Exception("Empty body")

            val root = JsonParser.parseString(body).asJsonObject

            val urlArray: JsonArray =
                root.getAsJsonArray("url") ?: throw Exception("No URLs found (url array missing)")
            if (urlArray.size() == 0) throw Exception("Empty URL array")

            val content: JsonObject = urlArray[0].asJsonObject
            val encryptedLink = content.get("link")?.asString.orEmpty()
            if (encryptedLink.isEmpty()) throw Exception("Empty encrypted link")

            val decryptedUrl = decryptLink(encryptedLink, masterKey)
                ?: throw Exception("Failed to decrypt link")

            val subtitles = mutableListOf<Video.Subtitle>()
            val tracksArray = root.getAsJsonArray("tracks")
            if (tracksArray != null) {
                for (i in 0 until tracksArray.size()) {
                    val t = tracksArray[i].asJsonObject
                    val subUrl = t.get("url")?.asString.orEmpty()
                    if (subUrl.isNotEmpty()) {
                        subtitles.add(
                            Video.Subtitle(
                                label = t.get("lang")?.asString ?: "Unknown",
                                url = subUrl
                            )
                        )
                    }
                }
            }

            return@coroutineScope Video(
                source = decryptedUrl,

                subtitles = subtitles,
                headers = mapOf(
                    "Referer" to mainUrl,
                    "Origin" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                ),
                type = MimeTypes.APPLICATION_M3U8
            )
        } catch (e: Exception) {
            println(e.stackTraceToString())
            throw Exception("Failed to extract video: ${e.message}")
        }
    }

    @SuppressLint("NewApi")
    private fun b64Decode(s: String): ByteArray = Base64.getDecoder().decode(s.trim())
    private fun b64DecodeToString(s: String): String = String(b64Decode(s), Charsets.UTF_8)

    private fun getMasterKey(): String? {
        return try {
            val request = Request.Builder()
                .url("$coreApi/api-key")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .header("Origin", mainUrl)
                .header("Referer", "$mainUrl/")
                .build()

            val response = Utils.httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val b64Data = response.body?.string() ?: return null
            val data = b64Decode(b64Data)

            if (data.size < 12 + 16 + 1) return null

            val iv = data.copyOfRange(0, 12)

            // Server format is unclear; try both:
            // A) iv + tag + ciphertext
            // B) iv + ciphertext + tag
            val key = MessageDigest.getInstance("SHA-256")
                .digest(staticPass.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(key, "AES")

            fun tryDecrypt(tagFirst: Boolean): String? {
                return try {
                    val (ciphertext, tag) = if (tagFirst) {
                        val tagBytes = data.copyOfRange(12, 28)
                        val ct = data.copyOfRange(28, data.size)
                        ct to tagBytes
                    } else {
                        val ct = data.copyOfRange(12, data.size - 16)
                        val tagBytes = data.copyOfRange(data.size - 16, data.size)
                        ct to tagBytes
                    }

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                    val combined = ciphertext + tag
                    val decrypted = cipher.doFinal(combined)
                    String(decrypted, Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }

            tryDecrypt(tagFirst = true) ?: tryDecrypt(tagFirst = false)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptLink(encLink: String, masterKey: String): String? {
        return try {
            val decodedRaw = b64DecodeToString(encLink)
            val parts = decodedRaw.split(":")
            if (parts.size < 2) return null

            val iv = b64Decode(parts[0])
            val ciphertext = b64Decode(parts[1])

            val keyBytes = masterKey.toByteArray(Charsets.UTF_8)
            val paddedKey = ByteArray(32) { i -> if (i < keyBytes.size) keyBytes[i] else 0 }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(paddedKey, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(ciphertext)

            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

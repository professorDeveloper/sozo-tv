package com.saikou.sozo_tv.parser.extractor

import com.google.gson.Gson
import com.saikou.sozo_tv.data.model.hianime.MegaResponse
import com.saikou.sozo_tv.data.model.hianime.MegaTrack
import com.saikou.sozo_tv.utils.Utils
import java.net.URLEncoder

class MegacloudExtractor {

    private val gson = Gson()
    private val mainUrl = "https://megacloud.blog"

    fun extractVideoUrl(url: String): Pair<String, List<MegaTrack>> {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to "$mainUrl/"
        )

        val id = url.substringAfterLast("/").substringBefore("?")

        val html = Utils.get(url, headers)
        val nonce = extractNonce(html)

        val apiUrl = "$mainUrl/embed-2/v3/e-1/getSources?id=$id&_k=$nonce"
        val json = Utils.get(apiUrl, headers)

        val resp = gson.fromJson(json, MegaResponse::class.java)
        val encoded = resp.sources.firstOrNull()?.file
            ?: error("No sources found")

        val videoUrl = if (encoded.contains(".m3u8")) {
            encoded
        } else decryptVideo(encoded, nonce)

        return Pair(videoUrl, resp.tracks)
    }

    private fun extractNonce(html: String): String {
        val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(html)
        if (match1 != null) return match1.value

        val match2 = Regex("""([a-zA-Z0-9]{16}).*?([a-zA-Z0-9]{16}).*?([a-zA-Z0-9]{16})""")
            .find(html)

        return match2?.groupValues?.drop(1)?.joinToString("")
            ?: error("Nonce not found")
    }

    private fun decryptVideo(encrypted: String, nonce: String): String {
        val keyJson = Utils.get(
            "https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json"
        )

        val key = gson.fromJson(keyJson, Map::class.java)["mega"] as? String
            ?: error("Mega key missing")

        val googleDecrypt =
            "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"

        val url = googleDecrypt +
                "?encrypted_data=${URLEncoder.encode(encrypted, "UTF-8")}" +
                "&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
                "&secret=${URLEncoder.encode(key, "UTF-8")}"

        val decrypted = Utils.get(url)
        return Regex("\"file\":\"(.*?)\"")
            .find(decrypted)?.groupValues?.get(1)
            ?: error("Decrypted link not found")
    }
}

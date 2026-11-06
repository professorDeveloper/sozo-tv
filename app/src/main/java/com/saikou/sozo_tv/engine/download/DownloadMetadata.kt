package com.saikou.sozo_tv.engine.download

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

internal data class DownloadMetadata(
    val title: String,
    val owner: String,
    val url: String,
    val mime: String,
    val headers: Map<String, String>,
    val localProxy: String? = null,
    val transform: String? = null,
) {
    fun encode(): ByteArray = JSONObject().apply {
        put("title", title); put("owner", owner); put("url", url); put("mime", mime)
        put("headers", JSONObject(headers)); put("localProxy", localProxy); put("transform", transform)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(data: ByteArray): DownloadMetadata {
            val json = JSONObject(String(data, Charsets.UTF_8))
            val headers = json.optJSONObject("headers") ?: JSONObject()
            return DownloadMetadata(json.getString("title"), json.getString("owner"),
                json.getString("url"), json.getString("mime"),
                headers.keys().asSequence().associateWith { headers.getString(it) },
                json.optString("localProxy").takeIf { it.isNotEmpty() && it != "null" },
                json.optString("transform").takeIf { it.isNotEmpty() && it != "null" })
        }
    }
}

internal object DownloadRules {
    fun id(owner: String, url: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$owner\n$url".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun rejectReason(url: String, drm: String?, separateAudio: Boolean): String? {
        if (!drm.isNullOrBlank()) return "This source requires an offline DRM license. Choose an unprotected source."
        if (separateAudio) return "This source has separate audio. Choose a source with audio included for offline playback."
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            return "Only direct video, HLS and DASH sources can be downloaded."
        }
        if (uri!!.host.lowercase() in setOf("localhost", "127.0.0.1", "::1", "[::1]")) {
            return "This source uses a temporary player session. Choose another source for offline playback."
        }
        return null
    }
}

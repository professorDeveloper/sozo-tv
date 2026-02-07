package com.saikou.sozo_tv.parser.extractor

import android.annotation.SuppressLint
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.parser.models.Video
import org.jsoup.Jsoup
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class YourUploadExtractor : Extractor() {

    override val name = "YourUpload"
    override val mainUrl = "https://www.yourupload.com"
    override val aliasUrls =
        listOf("https://www.yourupload.com", "https://vidcache.net", "https://www.yucache.net")

    override suspend fun extract(link: String): Video {
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                    arrayOf()
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val doc = Jsoup.connect(link)
            .sslSocketFactory(sslContext.socketFactory)
            .timeout(30000)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .get()

        val videoUrl = extractVideoUrl(doc)

        if (videoUrl.isEmpty()) {
            throw Exception("Video manbasi topilmadi")
        }

        return Video(
            source = videoUrl,
            subtitles = listOf(),
            type = if (videoUrl.endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/116.0.0.0 Safari/537.36",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive"
            )
        )
    }

    private fun extractVideoUrl(doc: org.jsoup.nodes.Document): String {
        doc.select("meta[property=og:video], meta[name=og:video]")
            .firstOrNull()
            ?.attr("content")
            ?.let { if (it.isNotEmpty()) return it }
        val scripts = doc.select("script")
        for (script in scripts) {
            val scriptContent = script.html()
            if (scriptContent.contains("jwplayerOptions") || scriptContent.contains("file:")) {
                val pattern1 = Regex("""file:\s*['"]([^'"]+\.(?:mp4|m3u8))['"]""")
                pattern1.find(scriptContent)?.let {
                    return it.groupValues[1]
                }
                val pattern2 = Regex("""https?://[^"' ]+video\.(?:mp4|m3u8)""")
                pattern2.find(scriptContent)?.let {
                    return it.value
                }
            }
        }

        doc.select("video source")
            .firstOrNull()
            ?.attr("src")
            ?.let { if (it.isNotEmpty()) return it }

        return ""
    }
}
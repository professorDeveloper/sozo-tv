package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.parser.anime.AnimePahe.Companion.USER_AGENT
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import java.net.URI

open class DoodLaExtractor : Extractor() {

    override val name = "DoodStream"
    override val mainUrl = "https://dood.la"
    override val aliasUrls = listOf(
        "https://dsvplay.com",
        "https://mikaylaarealike.com",
        "https://myvidplay.com"
    )

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun extract(link: String): Video {
        val embedUrl = link.replace("/d/", "/e/")

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to link,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5"
        )

        val documentHtml = Utils.get(embedUrl, headers)

        val md5Regex = Regex("/pass_md5/[^']*")
        val md5Path = md5Regex.find(documentHtml)?.value
            ?: throw Exception("Can't find md5")

        val baseUrl = getBaseUrl(embedUrl)
        val md5Url = baseUrl + md5Path

        val response = Utils.get(md5Url, headers)

        val hashTable = createHashTable()
        val videoUrl = response.trim() + hashTable + "?token=${md5Path.substringAfterLast("/")}"

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to mainUrl
            )
        )
    }

    private fun createHashTable(): String {
        return buildString {
            repeat(10) {
                append(alphabet.random())
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            if (url.startsWith("http://")) {
                url.substringBefore("/", "")
            } else if (url.startsWith("https://")) {
                val afterProtocol = url.substringAfter("https://")
                "https://" + afterProtocol.substringBefore("/")
            } else {
                "https://" + url.substringBefore("/")
            }
        }
    }

    class DoodLiExtractor : DoodLaExtractor() {
        override var mainUrl = "https://dood.li"
    }

    class DoodExtractor : DoodLaExtractor() {
        override val mainUrl = "https://vide0.net"
    }
}
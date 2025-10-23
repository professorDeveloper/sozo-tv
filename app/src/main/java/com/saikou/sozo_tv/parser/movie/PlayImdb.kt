package com.saikou.sozo_tv.parser.movie

import com.saikou.sozo_tv.data.model.Backdrop
import com.saikou.sozo_tv.data.remote.ImageUrlFormatter
import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.Utils.getJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


class PlayImdb : BaseParser() {
    override val name: String = "StreamIMDB"
    override val saveName: String = "streamimdb"
    override val hostUrl: String = "https://streamimdb.me/"
    override val language: String = "en"
    override val isNSFW: Boolean = false

    suspend fun getEpisodes(imdbId: String): Pair<List<Episode>, List<Backdrop>> =
        withContext(Dispatchers.IO) {
            val doc: Document = getJsoup("$hostUrl/embed/$imdbId")
            val episodes = mutableListOf<Episode>()
            val epsDiv = doc.selectFirst("#eps")
            if (epsDiv != null && epsDiv.select(".ep").isNotEmpty()) {
                epsDiv.select(".ep").forEach { el ->
                    val url = el.attr("data-iframe").trim()
                    val titleText = el.text().ifBlank {
                        val s = el.attr("data-s")
                        val e = el.attr("data-e")
                        "S${s.padStart(2, '0')}E${e.padStart(2, '0')}"
                    }

                    episodes.add(
                        Episode(
                            season = el.attr("data-s").toIntOrNull() ?: 0,
                            episode = el.attr("data-e").toIntOrNull() ?: 0,
                            title = titleText,
                            iframeUrl = if (url.startsWith("http")) url else "$hostUrl$url"
                        )
                    )
                }
            } else {
                val iframe = doc.selectFirst("iframe")
                    ?: throw Exception("Iframe not found")

                val iframeSrc = iframe.attr("src").trim()
                val fixedUrl = when {
                    iframeSrc.startsWith("http") -> iframeSrc
                    iframeSrc.startsWith("//") -> "https:$iframeSrc"
                    iframeSrc.startsWith("/") -> "$hostUrl$iframeSrc"
                    else -> "$hostUrl/$iframeSrc"
                }

                val pageTitle = doc.selectFirst("title")?.text()?.trim() ?: "Movie"

                episodes.add(
                    Episode(
                        season = 0,
                        episode = 1,
                        title = pageTitle,
                        iframeUrl = fixedUrl
                    )
                )
            }

            return@withContext Pair(
                episodes.sortedWith(compareBy({ it.season }, { it.episode })),
                getDetails(imdbId)
            )
        }

    fun extractSeriesIframe(link: String): String? {
        val doc: Document = getJsoup(link)
//        println(doc)
        val iframeSrc = doc.selectFirst("iframe#player_iframe")?.attr("src")

        if (iframeSrc != null) {
            val finalUrl = if (iframeSrc.startsWith("//")) {
                "https:$iframeSrc"
            } else iframeSrc

            return finalUrl
        }

        println("⚠️ iframe#player_iframe not found")
        return null
    }

    suspend fun getDetails(imdbId: String): ArrayList<Backdrop> {
        val backdrops = ArrayList<Backdrop>()
        println()
        val doc = getJsoup(
            "https://imdb.com/title/${imdbId}/mediaindex/?ref_=mv_sm",
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",

                )
        )
        println("Details url :https://imdb.com/title/${imdbId}/mediaindex/?ref_=mv_sm")
        println("Details url :${doc}")

        val images = doc.select("img.ipc-image")

        for (image in images) {
            val srcSet = image.attr("srcSet")
            val links = srcSet.split(",")

            val link1230 = links.firstOrNull { it.contains("UX1230") }?.trim()?.split(" ")?.first()

            val finalLink = link1230 ?: image.attr("src")
            backdrops.add(Backdrop(ImageUrlFormatter.formatImageUrl(finalLink)))
        }

        return backdrops
    }

    suspend fun convertRcptProctor(iframeUrl: String): String = withContext(Dispatchers.IO) {

        val response = Jsoup.connect(iframeUrl)
            .method(Connection.Method.GET)
            .header(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            .header("accept-language", "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7")
            .header("cache-control", "max-age=0")
            .header("dnt", "1")
            .header("priority", "u=0, i")
            .header(
                "sec-ch-ua",
                "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\""
            )
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("sec-fetch-dest", "document")
            .header("sec-fetch-mode", "navigate")
            .header("sec-fetch-site", "none")
            .header("sec-fetch-user", "?1")
            .header("upgrade-insecure-requests", "1")
            .header(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .execute()
        val document = Jsoup.parse(response.body())
        val scripts = document.select("script")

        var prorcpUrl: String? = null
        for (script in scripts) {
            val content = script.data()
            val regex = Regex("""src:\s*['"](/prorcp/[^'"]+)['"]""")
            val match = regex.find(content)
            if (match != null) {
                prorcpUrl = match.groupValues[1]
                break
            }
        }
        return@withContext "https://cloudnestra.com/$prorcpUrl" ?: ""

    }

    suspend fun extractDirectM3u8(iframeUrl: String): String {
        val response = Jsoup.connect(iframeUrl)
            .method(Connection.Method.GET)
            .header(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            .header("accept-language", "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7")
            .header("cache-control", "max-age=0")
            .header("dnt", "1")
            .header(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari"
            )
            .ignoreContentType(true)
            .timeout(15000)
            .execute()

        val html = response.body()

        val regex = Regex("""https?://[^\s'"]+\.m3u8""")
        val match = regex.find(html)

        return if (match != null) {
            val m3u8Url = match.value
            m3u8Url
        } else {
            "empty"
        }

    }

}
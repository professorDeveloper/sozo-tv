package com.saikou.sozo_tv.parser.movie

import android.util.Log
import com.bugsnag.android.Bugsnag
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.data.model.Backdrop
import com.saikou.sozo_tv.data.model.SeasonResponse
import com.saikou.sozo_tv.data.model.SubtitleItem
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.movie.helper.SourceHelper.decryptMethods
import com.saikou.sozo_tv.parser.movie.helper.SourceHelper.extractIframeUrl
import com.saikou.sozo_tv.parser.movie.helper.SourceHelper.normalizeStreamUrl
import com.saikou.sozo_tv.utils.Utils.getJsoup
import com.saikou.sozo_tv.utils.Utils.httpClient
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.SocketTimeoutException
import java.net.URI


class PlayImdb : BaseParser() {
    override val name: String = "VIDSRC"
    override val saveName: String = "vidsrc"
    override val hostUrl: String = "https://vidsrc-embed.su"
    override val language: String = "en"
    override val isNSFW: Boolean = false

    suspend fun getEpisodes(imdbId: String): List<Episode> {
        return try {
            withContext(Dispatchers.IO) {
                val doc: Document = getJsoup("$hostUrl/embed/$imdbId")
                Log.d("GGG", "getEpisodes: DDD")
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
                Log.d("GGG", "getEpisodes: ${episodes}")

                return@withContext episodes.sortedWith(compareBy({ it.season }, { it.episode }))

            }

        } catch (e: Exception) {
            Bugsnag.notify(e)
            println(e)
            return arrayListOf()
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }


    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        Log.d("GGG", "extractProrcpUrl: ${iframeUrl}")
        val doc = Requests(baseClient = httpClient, responseParser = parser).get(
            iframeUrl, referer = iframeUrl, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )
        ).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        return host + matchedSrc
    }

    private suspend fun extractAndDecryptSource(
        prorcpUrl: String,
        referer: String
    ): List<Pair<String, String>>? {
        val responseText = Requests(baseClient = httpClient, responseParser = parser).get(
            prorcpUrl,
            referer = referer,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )
        ).text
        val playerJsRegex = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""")
        val temp = playerJsRegex.find(responseText)?.groupValues?.get(1)

        val encryptedURLNode = if (!temp.isNullOrEmpty()) {
            mapOf("id" to "playerjs", "content" to temp)
        } else {
            val document = Jsoup.parse(responseText)
            val reporting = document.selectFirst("#reporting_content") ?: return null
            val node = reporting.nextElementSibling() ?: return null
            mapOf("id" to node.attr("id"), "content" to node.text())
        }

        val id = encryptedURLNode["id"] ?: return null
        val content = encryptedURLNode["content"] ?: return null

        val decrypted = decryptMethods[id]?.invoke(content) ?: return null
        val vSubs = mapOf(
            "v1" to "shadowlandschronicles.com",
            "v2" to "cloudnestra.com",
            "v3" to "thepixelpioneer.com",
            "v4" to "putgate.org",
        )
        val placeholderRegex = "\\{(v\\d+)\\}".toRegex()
        val mirrors: List<Pair<String, String>> = decrypted
            .split(" or ")
            .map { it.trim() }
            .filter { it.startsWith("http") }
            .map { rawUrl ->
                val match = placeholderRegex.find(rawUrl)
                val version = match?.groupValues?.get(1) ?: ""
                val domain = vSubs[version] ?: ""
                val finalUrl = if (domain.isNotEmpty()) {
                    placeholderRegex.replace(rawUrl) { domain }
                } else {
                    rawUrl
                }

                version to finalUrl
            }

        return mirrors.ifEmpty { null }
    }

    suspend fun getSubtitleListForMovie(tmdbId: Int): ArrayList<SubtitleItem> {
        val niceHttp = Requests(baseClient = httpClient, responseParser = parser)

        val request = niceHttp.get(
            "https://sub.wyzie.ru/search?id=${tmdbId}",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )
        )

        if (request.isSuccessful) {
            val body = request.body.string()
            try {
                val listType = object : TypeToken<List<SubtitleItem>>() {}.type
                val data: List<SubtitleItem> = Gson().fromJson(body, listType)

                if (data.isNotEmpty()) {
                    return data as ArrayList<SubtitleItem>
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return arrayListOf()
    }

    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val url = if (season == null) {
            "$hostUrl/embed/movie?imdb=$id"
        } else {
            "$hostUrl/embed/tv?imdb=$id&season=$season&episode=$episode"
        }
        val iframeUrl = extractIframeUrl(url) ?: return ""
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: "Not Found 2"
        val decryptedSource = extractAndDecryptSource(prorcpUrl, iframeUrl) ?: return ""
        return decryptedSource.get(0).second.normalizeStreamUrl()

    }

    suspend fun getSubTitleList(
        tmdbId: Int,
        season: Int = -1,
        episode: Int = -1
    ): ArrayList<SubtitleItem> {
        val niceHttp = Requests(baseClient = httpClient, responseParser = parser)

        val request = niceHttp.get(
            "https://sub.wyzie.ru/search?id=${tmdbId}&season=${season}&episode=${episode}",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )
        )

        if (request.isSuccessful) {
            val body = request.body.string()
            try {
                val listType = object : TypeToken<List<SubtitleItem>>() {}.type
                val data: List<SubtitleItem> = Gson().fromJson(body, listType)

                if (data.isNotEmpty()) {
                    return data as ArrayList<SubtitleItem>
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return arrayListOf()
    }


    suspend fun getDetails(season: Int, tmdbId: Int): ArrayList<Backdrop> {
        return withContext(Dispatchers.IO) {
            val niceHttp = Requests(baseClient = httpClient, responseParser = parser)

            val url = "https://jumpfreedom.com/3/tv/$tmdbId/season/$season?language=en-US"
            Log.d("GGG", "URL: $url")

            val response = niceHttp.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
                    "Content-Type" to "application/json;charset=UTF-8",
                    "Accept" to "application/json"
                )
            )

            // Body ni faqat 1 marta o'qing
            val bodyString = response.body?.string().orEmpty()
            Log.d("GGG", "isSuccessful=${response.isSuccessful}")
            Log.d("GGG", "BODY: $bodyString")

            if (!response.isSuccessful || bodyString.isBlank()) {
                return@withContext arrayListOf<Backdrop>()
            }

            val data = Gson().fromJson(bodyString, SeasonResponse::class.java)
            val stillPaths = data.episodes.mapNotNull { it.stillPath }

            ArrayList(stillPaths.map { Backdrop("https://image.tmdb.org/t/p/w500/$it") })
        }
    }


}
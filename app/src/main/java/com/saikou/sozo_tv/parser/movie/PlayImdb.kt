package com.saikou.sozo_tv.parser.movie

import android.util.Log
import com.bugsnag.android.Bugsnag
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.data.model.Backdrop
import com.saikou.sozo_tv.data.model.SeasonResponse
import com.saikou.sozo_tv.data.model.SubtitleItem
import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
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


class PlayImdb : BaseParser() {
    override val name: String = "StreamIMDB"
    override val saveName: String = "streamimdb"
    override val hostUrl: String = "https://streamimdb.me/"
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

    override suspend fun loadEpisodes(id: String, page: Int,showResponse: ShowResponse): EpisodeData? {
        TODO("Not yet implemented")
    }

    override suspend fun search(query: String): List<ShowResponse> {
        TODO("Not yet implemented")
    }

    fun extractSeriesIframe(link: String): String? {
        val doc: Document = getJsoup(link)
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


    suspend fun getSubTitleList(tmdbId: Int, season: Int, episode: Int): ArrayList<SubtitleItem> {
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
        val niceHttp = Requests(baseClient = httpClient, responseParser = parser)
        val request = niceHttp.get(
            "https://jumpfreedom.com/3/tv/${tmdbId}/season/${season}?language=en-US",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
            )
        )
        if (request.isSuccessful) {

            val body = request.body.string()
            val data = Gson().fromJson(body, SeasonResponse::class.java)
            val stillPaths = data.episodes.mapNotNull { it.stillPath }


            return stillPaths.map { Backdrop("https://image.tmdb.org/t/p/w500/${it.toString()}") } as ArrayList<Backdrop>
        } else {
            Log.d("GGG", "getDetails:fuck  life ")
            return arrayListOf()
        }
        return ArrayList()
    }

    suspend fun convertRcptProctor(iframeUrl: String): String = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var attempt = 0

        while (attempt < maxRetries) {
            try {
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
                        "User-agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                    )
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .timeout(10_000)
                    .execute()

                delay(800)

                val document = Jsoup.parse(response.body())
                val scripts = document.select("script")

                for (script in scripts) {
                    val content = script.data()
                    val regex = Regex("""src:\s*['"](/prorcp/[^'"]+)['"]""")
                    val match = regex.find(content)
                    if (match != null) {
                        val prorcpUrl = match.groupValues[1]
                        return@withContext "https://cloudnestra.com$prorcpUrl"
                    }
                }

            } catch (e: SocketTimeoutException) {
                attempt++
                if (attempt >= maxRetries) {
                    Bugsnag.notify(e)
                    return@withContext ""
                } else {
                    delay(1000)
                }
            } catch (e: Exception) {
                Bugsnag.notify(e)
                return@withContext ""
            }
        }

        return@withContext ""
    }

    fun extractDirectM3u8(iframeUrl: String): String {
        try {
            val response = Jsoup.connect(iframeUrl)
                .method(Connection.Method.GET)
                .header(
                    "accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                .header("Sec-Fetch-Dest", "iframe")
                .header("Refer", iframeUrl)
                .header("accept-language", "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7")
                .header("cache-control", "max-age=0")
                .header("dnt", "1")
                .header(
                    "User-agent",
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

        } catch (e: Exception) {
            Bugsnag.notify(e)
            return ""
        }
    }
//    @SuppressLint("SetJavaScriptEnabled")
//    suspend fun extractDirectM3u8(
//        context: Context,
//        iframeUrl: String
//    ): String = withContext(Dispatchers.Main) {
//        suspendCancellableCoroutine { cont ->
//            val webView = WebView(context).apply {
//                settings.javaScriptEnabled = true
//                settings.domStorageEnabled = true
//                settings.userAgentString =
//                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
//                visibility = View.GONE
//            }
//
//            webView.webViewClient = object : WebViewClient() {
//                override fun onPageFinished(view: WebView?, url: String?) {
//                    webView.evaluateJavascript(
//                        "(function(){return document.documentElement.outerHTML;})();"
//                    ) { html ->
//                        try {
//                            val regex = Regex("""https?://[^\s'"]+\.m3u8""")
//                            val match = regex.find(html)
//                            val finalM3u8 = match?.value ?: "empty"
//                            cont.resume(finalM3u8, null)
//                        } catch (e: Exception) {
//                            Bugsnag.notify(e)
//                            cont.resume("", null)
//                        } finally {
//                            webView.destroy()
//                        }
//                    }
//                }
//            }
//
//            webView.loadUrl(iframeUrl)
//        }
//    }

}
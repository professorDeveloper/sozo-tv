package com.saikou.sozo_tv.parser

import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.models.AnimePaheData
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.Kiwi
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Utils.getJsoup
import com.saikou.sozo_tv.utils.Utils.httpClient
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Elements
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.cert.X509Certificate

class AnimePahe : BaseParser() {
    override val name: String = "AniPahe"
    override val saveName: String = "anipahe"
    override val hostUrl: String = "https://animepahe.ru/"
    override val language: String = "en"

    // DDoS-Guard va Cloudflare bypass uchun umumiy headerlar
    private suspend fun getDefaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to hostUrl,
            "DNT" to "1",
            "Cookie" to getFreshCookies()
        )
    }

    suspend fun search(query: String): List<ShowResponse> {
        val formattedQuery = query.replace(" ", "%20")
        val requests = Requests(httpClient, responseParser = parser)
        val headers = getDefaultHeaders()

        val list = mutableListOf<ShowResponse>()
        try {
            val res = requests.get("${hostUrl}api?m=search&q=$formattedQuery", headers)
                .parsed<AnimePaheData>()

            res.data.forEach {
                list.add(ShowResponse(it.title.toString(), it.session ?: "", it.poster ?: ""))
            }
        } catch (e: Exception) {
            Bugsnag.notify(e)
            println("Search failed: ${e.message}")
        }
        return list
    }

    suspend fun loadEpisodes(id: String, curPage: Int): EpisodeData? {
        val requests = Requests(httpClient, responseParser = parser)
        return try {
            requests.get(
                "${hostUrl}api?m=release&id=$id&sort=episode_asc&page=$curPage",
                getDefaultHeaders()
            ).parsed()
        } catch (e: Exception) {
            Bugsnag.notify(e)
            println("Episode load error: ${e.message}")
            null
        }
    }

    fun getEpisodeVideo(epId: String, id: String): Kiwi {
        val doc =
            getJsoup("https://animepahe.ru/play/${id}/${epId}", mapOf("User-Agent" to USER_AGENT))

        val scriptContent = doc.select("script")
            .map { it.html() }
            .firstOrNull { it.contains("session") && it.contains("provider") && it.contains("url") }
            ?: ""

        val sessionRegex = Pattern.compile("""let\s+session\s*=\s*"([^"]+)"""")
        val providerRegex = Pattern.compile("""let\s+provider\s*=\s*"([^"]+)"""")
        val urlRegex = Pattern.compile("""let\s+url\s*=\s*"([^"]+)"""")

        val session =
            sessionRegex.matcher(scriptContent).let { if (it.find()) it.group(1) else null }
        val provider =
            providerRegex.matcher(scriptContent).let { if (it.find()) it.group(1) else null }
        val url = urlRegex.matcher(scriptContent).let { if (it.find()) it.group(1) else null }

        println("Session: $session | Provider: $provider | URL: $url")

        return Kiwi(session ?: "empty", provider ?: "empty", url ?: "empty")
    }

    fun extractVideo(url: String): String {
        val doc = getJsoup(url, mapOf("User-Agent" to USER_AGENT, "Referer" to hostUrl))
        val scripts: Elements = doc.getElementsByTag("script")
        var evalContent: String? = null

        for (script in scripts) {
            val scriptContent = script.html()
            if (scriptContent.contains("eval(function(p,a,c,k,e,d){")) {
                evalContent = scriptContent
                break
            }
        }

        return extractFileUrl(getAndUnpack(evalContent ?: "")) ?: ""
    }

    private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
    private fun getPacked(string: String): String? = packedRegex.find(string)?.value

    private fun getAndUnpack(string: String): String {
        val packedText = getPacked(string)
        return JsUnpacker(packedText).unpack() ?: string
    }

    private fun extractFileUrl(input: String): String? {
        val regex = Regex("https?://\\S+\\.m3u8")
        return regex.find(input)?.value
    }

    suspend fun getFreshCookies(): String = withContext(Dispatchers.IO) {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    p0: Array<out java.security.cert.X509Certificate>?,
                    p1: String?
                ) {

                }

                override fun checkServerTrusted(
                    p0: Array<out java.security.cert.X509Certificate>?,
                    p1: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<out java.security.cert.X509Certificate> =
                    arrayOf()
            }
        )

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true).build()
        val request = Request.Builder()
            .url(hostUrl)
            .header("User-Agent", USER_AGENT)
            .header("DNT", "1")
            .build()

        client.newCall(request).execute().use { response: Response ->
            val cookies = response.headers("Set-Cookie")
            return@withContext cookies.joinToString("; ") { it.substringBefore(";") }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }
}

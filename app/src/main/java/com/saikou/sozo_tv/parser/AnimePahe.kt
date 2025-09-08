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
import org.jsoup.select.Elements
import java.util.regex.Pattern

class AnimePahe : BaseParser() {
    override val name: String = "AniPahe"
    override val saveName: String = "anipahe"
    override val hostUrl: String = "https://animepahe.ru/"
    override val language: String = "en"

    suspend fun search(query: String): List<ShowResponse> {
        val firstSpaceIndex = query.indexOf(" ")
        val formattedQuery = if (firstSpaceIndex != -1) {
            query.substring(0, firstSpaceIndex).replace(" ", "%")
        } else {
            query
        }
        val requests = Requests(httpClient, responseParser = parser)

        val list = mutableListOf<ShowResponse>()
        println("${hostUrl}api?m=search&q=$formattedQuery")
        requests.get(
            "${hostUrl}api?m=search&q=$query", mapOf(
                "dnt" to "1",
                "Cookie" to "\n" +
                        "__ddg1_=Yhqnq62nxbM5uT9LNXBU; SERVERID=janna; latest=5633; ann-fakesite=0; res=720; aud=jpn; av1=0; dom3ic8zudi28v8lr6fgphwffqoz0j6c=33161aa3-e5ac-4f93-b315-e3165fddb0bf%3A3%3A1; sb_page_8966b6c0380845137e2f0bc664baf7be=3; sb_count_8966b6c0380845137e2f0bc664baf7be=3; sb_onpage_8966b6c0380845137e2f0bc664baf7be=1; XSRF-TOKEN=eyJpdiI6InV2RGVHeUhMNkxFelAzOG16TnRXa2c9PSIsInZhbHVlIjoiWkQyWTJaODErMnNVREhRdnZ5L0pycG1Sd2hWZkRhcjB6alN6MDZwb3ppOEpTNFpscWljYmRkVHI0RDNDN0ZxYkZIZE5jSTF2SWpjckZSaHhYWkVRZmdHMGgreE1LMlNLZXpPUnREQ3hjQ0NiZ1RZNUEwQ1hXNkxjaEdKdVc3YnAiLCJtYWMiOiJhMDRkOWU3ZjkzZWNjZmMxYTUxNTI0YWIwOTE2NTcxYTUyYWI3NTM4YTgyMzJhYmYyZDc3YjA2NWVlMjBmMDNhIiwidGFnIjoiIn0%3D; laravel_session=eyJpdiI6IlVtQnJPL3habzNObUJmWVpkTEZTTEE9PSIsInZhbHVlIjoiR2ZMditvM0ZvYnVLajArWnVYZllFcEpOUGVXYk95bWRkdXdGcUVMZE9mT0ZvYmpPSEpoMDdNeC9MWjlxMnluVHd4djZ1TGcyOHJxbEdxd013K09wemJiZlcrZHhUZUN5YkJma3pkZXN4ZVZyU0RQY0pvSnc1WHpHTHlDUWpvTE0iLCJtYWMiOiIzZGVjYTM3N2ZiYzc3ODAyOWMyNjAwODU4NWU4YTY0NTgwNjVhNTVjZGM0NjZjM2QxOTM5MzJlZTcwNTEyYzM3IiwidGFnIjoiIn0%3D; __ddgid_=QTDZaHo3uDoGqGuR; __ddgmark_=a9WzMcAyP2KIzfHF; __ddg2_=nslKhTTMfCM10kKQ",
                "Referer" to "https://animepahe.ru//api?m=search&q=$formattedQuery",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
            )
        )
            .parsed<AnimePaheData>()
            .apply {
                data.onEach {
                    val link = it.session
                    val title = it.title.toString()
                    val cover = it.poster

                    list.add(ShowResponse(title, link ?: "", cover ?: ""))
                }
            }
        return list
    }

    //api?m=release&id=d58fc9f8-582e-fdf0-3618-112cd54ed5ab&sort=episode_asc&page=1
    suspend fun loadEpisodes(id: String, curPage: Int): EpisodeData? {
        val request = Requests(httpClient, responseParser = parser)
        try {
            return request.get(
                "${hostUrl}api?m=release&id=$id&sort=episode_asc&page=$curPage",
            ).parsed<EpisodeData>()

        } catch (e: Exception) {
            Bugsnag.notify(e)
            return null
        }
    }

    fun getEpisodeVideo(epId: String, id: String): Kiwi {
        //https://animepahe.ru/play/${id}/${epId}
        val doc = getJsoup("https://animepahe.ru/play/${id}/${epId}")

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

        println("Session: $session")
        println("Provider: $provider")
        println("URL: $url")
        return Kiwi(
            session = session ?: "empty",
            provider = provider ?: "empty",
            url = url ?: "empty"
        )
    }


     fun extractVideo(url: String): String {
        val doc = getJsoup(
            url, mapOf(
                "Referer" to "https://animepahe.ru/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                "Alt-Used" to "kwik.si",
                "Host" to "kwik.si",
                "Sec-Fetch-User" to "?1"
            )
        )
        val scripts: Elements = doc.getElementsByTag("script")
        var evalContent: String? = null
        for (script in scripts) {
            val scriptContent = script.html()

            if (scriptContent.contains("eval(function(p,a,c,k,e,d){")) {
                println("Found eval function: \n$scriptContent")
                evalContent = scriptContent
                break
            }
        }

        val urlM3u8 = extractFileUrl(getAndUnpack(evalContent.toString())) ?: ""

        return urlM3u8
    }


    private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
    private fun getPacked(string: String): String? {
        return packedRegex.find(string)?.value
    }

    private   fun getAndUnpack(string: String): String {
        val packedText = getPacked(string)
        return JsUnpacker(packedText).unpack() ?: string
    }

    private  fun extractFileUrl(input: String): String? {
        val regex = Regex("https?://\\S+\\.m3u8")
        val matchResult = regex.find(input)
        return matchResult?.value
    }

}
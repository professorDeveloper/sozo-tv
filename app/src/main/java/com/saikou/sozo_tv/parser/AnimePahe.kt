package com.saikou.sozo_tv.parser

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.bugsnag.android.Bugsnag
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.di.BASE_URL
import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.models.AnimePaheData
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.Kiwi
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Utils.getJsoup
import com.saikou.sozo_tv.utils.Utils.httpClient
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AnimePahe : BaseParser() {
    override val name: String = "AniPahe"
    override val saveName: String = "anipahe"
    override val hostUrl: String = "https://animepahe.si/"
    override val language: String = "en"

    private suspend fun getDefaultHeaders(): Map<String, String> {
        val cookie = try {
            getFreshCookiesFromFirebase("cookies")
        } catch (e: Exception) {
            Bugsnag.notify(e)
            println("Failed to get cookies from Firebase: ${e.message}")
            ""
        }

        val map = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.9",
            "DNT" to "1",
            "Referer" to "https://animepahe.si/"
        )

        if (cookie.isNotBlank()) {
            map["Cookie"] = cookie
        }

        return map
    }




    suspend fun search(query: String): List<ShowResponse> {
        val headers = getDefaultHeaders()
        val formattedQuery = query.replace(" ", "%20")
        val requests = Requests(httpClient, defaultHeaders = headers, responseParser = parser)

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
            return list
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

    private fun m3u8ToMp4(m3u8Url: String, fileName: String): String {
        val uri = java.net.URI(m3u8Url)
        val cleanPath = uri.path
            .replaceFirst("/stream", "/mp4")
            .substringBeforeLast("/")

        return java.net.URI(
            uri.scheme,
            uri.authority,
            cleanPath,
            "file=$fileName.mp4",
            null
        ).toString()
    }

    fun getEpisodeVideo(epId: String, id: String): Kiwi {
        val doc =
            getJsoup("https://animepahe.si/play/${id}/${epId}", mapOf("User-Agent" to USER_AGENT))

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

//        println("Session: $session | Provider: $provider | URL: $url")

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

        return m3u8ToMp4(extractFileUrl(getAndUnpack(evalContent ?: "")) ?: "", fileName = "file")
    }

    private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
    private fun getPacked(string: String): String? = packedRegex.find(string)?.value

    private fun getAndUnpack(string: String): String {
        val packedText = getPacked(string)
        return JsUnpacker(packedText).unpack() ?: string
    }

    private fun extractFileUrl(input: String): String? {
        val regex = Regex("https?://\\S+\\.m3u8")
        println("Input: ${regex.find(input)?.value}")
        return regex.find(input)?.value
    }

    suspend fun getFreshCookiesFromFirebase(path: String = "cookies"): String =
        suspendCancellableCoroutine { cont ->
            val dbRef: DatabaseReference = FirebaseDatabase.getInstance().getReference(path)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val cookieString = snapshot.getValue(String::class.java) ?: ""
                        cont.resume(cookieString)
                        println("Cookies: $cookieString")
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    cont.resumeWithException(error.toException())
                }
            }

            dbRef.addListenerForSingleValueEvent(listener)

            cont.invokeOnCancellation {
                try {
                    dbRef.removeEventListener(listener)
                } catch (_: Exception) { /* ignore */ }
            }
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }
}

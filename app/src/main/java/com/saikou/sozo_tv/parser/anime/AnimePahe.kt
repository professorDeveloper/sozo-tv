package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.bugsnag.android.Bugsnag
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.AnimePaheData
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.Utils.getJsoup
import com.saikou.sozo_tv.utils.Utils.httpClient
import com.saikou.sozo_tv.utils.parser
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
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
        )

        if (cookie.isNotBlank()) {
            map["Cookie"] = cookie
        }

        return map
    }


    override suspend fun search(query: String): List<ShowResponse> {
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

    override suspend fun loadEpisodes(
        id: String,
        page: Int,
        showResponse: ShowResponse
    ): EpisodeData? {
        val headers = getDefaultHeaders()
        val requests = Requests(httpClient, responseParser = parser, defaultHeaders = headers)
        return try {
            requests.get(
                "${hostUrl}api?m=release&id=$id&sort=episode_asc&page=$page", headers = headers
            ).parsed()
        } catch (e: Exception) {
            Bugsnag.notify(e)
            println("Episode load error: ${e.message}")
            null
        }
    }


    private fun getVideoOptions(doc: Document): List<VideoOption> {
        val videoOptions = mutableListOf<VideoOption>()

        val videoButtons = doc.select("div#resolutionMenu button.dropdown-item")
        videoButtons.forEach { button ->
            val kwikUrl = button.attr("data-src")
            val fansub = button.attr("data-fansub")
            val resolution = button.attr("data-resolution")
            val audio = button.attr("data-audio")
            val isActive = button.hasClass("active")

            val audioType = when (audio) {
                "jpn" -> AudioType.SUB
                "eng" -> AudioType.DUB
                else -> AudioType.SUB
            }

            val badges = button.select("span.badge").map { it.text() }
            val quality = badges.find { it.contains("BD") } ?: ""

            videoOptions.add(
                VideoOption(
                    videoUrl = kwikUrl,
                    fansub = fansub,
                    resolution = resolution,
                    audioType = audioType,
                    mimeTypes = MimeTypes.APPLICATION_M3U8,
                    quality = quality,
                    isActive = isActive,
                    fullText = button.text()
                )
            )
        }
        return videoOptions
    }


    override suspend fun getEpisodeVideo(id: String, epId: String): List<VideoOption> {
        val headers = getDefaultHeaders()
        val doc = getJsoup("https://animepahe.si/play/${id}/${epId}", headers)
        val videoOptions = getVideoOptions(doc)
        Log.d("GGG", "getEpisodeVideo:https://animepahe.si/play/${id}/${epId} ")
        return videoOptions
    }

    override suspend fun extractVideo(url: String): Video {
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

        return Video(
            source = extractFileUrl(getAndUnpack(evalContent ?: "")) ?: "",
            subtitles = listOf(),
            type = MimeTypes.APPLICATION_M3U8,
            headers = mapOf(
                "Referer" to hostUrl,
                "User-Agent" to USER_AGENT,
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive"
            )
        )
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
                } catch (_: Exception) { /* ignore */
                }
            }
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }
}

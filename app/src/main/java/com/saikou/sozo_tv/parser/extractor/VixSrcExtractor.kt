package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

class VixSrcExtractor : Extractor() {

    override val name = "VixSrc"
    override val mainUrl = "https://vixsrc.to"

    fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
                is Video.Type.Movie -> "$mainUrl/movie/${videoType.id}"
                else -> throw IllegalArgumentException("Unknown video type")
            },
        )
    }

    override suspend fun extract(link: String): Video {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to mainUrl
        )

        val html = Utils.get(link, headers)

        val scriptRegex =
            Regex("<script>([\\s\\S]*?)window\\.video\\s*=\\s*\\{([\\s\\S]*?)\\};([\\s\\S]*?)</script>")
        val scriptMatch = scriptRegex.find(html)

        val scriptText = scriptMatch?.value ?: ""

        if (scriptText.isEmpty()) {
            val allScriptsRegex = Regex("<script>([\\s\\S]*?)</script>")
            val allScripts = allScriptsRegex.findAll(html).toList()

            for (script in allScripts) {
                val scriptContent = script.groupValues[1]
                if (scriptContent.contains("window.video")) {
                    return extractFromScript(scriptContent, link)
                }
            }

            throw Exception("window.video script topilmadi")
        }

        return extractFromScript(scriptText, link)
    }

    private fun extractFromScript(scriptText: String, link: String): Video {
        val videoIdRegex = Regex("""id\s*:\s*'([^']+)'""")
        val videoIdMatch = videoIdRegex.find(scriptText)
        val videoId = videoIdMatch?.groupValues?.get(1) ?: throw Exception("Video ID topilmadi")

        println("DEBUG - Video ID: $videoId")
        val tokenRegex = Regex("""'token'\s*:\s*'([^']+)'""")
        val tokenMatch = tokenRegex.find(scriptText)
        val token = tokenMatch?.groupValues?.get(1) ?: throw Exception("Token topilmadi")

        println("DEBUG - Token: $token")

        val expiresRegex = Regex("""'expires'\s*:\s*'([^']+)'""")
        val expiresMatch = expiresRegex.find(scriptText)
        val expires = expiresMatch?.groupValues?.get(1) ?: throw Exception("Expires topilmadi")

        val hasBParam = scriptText.contains("""url:\s*'[^']*b=1[^']*'""")
        val canPlayFHD = scriptText.contains("window.canPlayFHD = true")

        println("DEBUG - hasBParam: $hasBParam, canPlayFHD: $canPlayFHD")

        val masterParams = mutableMapOf<String, String>()
        masterParams["token"] = token
        masterParams["expires"] = expires
        val url = URL(link)
        val query = url.query
        if (!query.isNullOrEmpty()) {
            query.split("&").map { param -> param.split("=") }.filter { it.size == 2 }
                .forEach { masterParams[it[0]] = it[1] }
        }

        if (hasBParam) masterParams["b"] = "1"
        if (canPlayFHD) masterParams["h"] = "1"
        masterParams["lang"] = "en"

        val baseUrl = "https://vixsrc.to/playlist/$videoId"
        val httpUrlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid base URL")

        masterParams.forEach { (key, value) ->
            httpUrlBuilder.addQueryParameter(key, value)
        }

        val finalUrl = httpUrlBuilder.build().toString()
        println(finalUrl)
        println(
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
        )
        return Video(
            source = finalUrl, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            ), subtitles = emptyList()
        )
    }
}
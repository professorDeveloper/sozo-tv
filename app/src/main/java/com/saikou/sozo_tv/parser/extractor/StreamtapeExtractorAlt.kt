package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import okhttp3.Request

class StreamtapeExtractorAlt : Extractor() {

    override val name = "Streamtape"
    override val mainUrl = "https://streamtape.com"
    override val aliasUrls = listOf("https://streamta.site")

    override suspend fun extract(link: String): Video {
        val linkJustParameter = if (link.startsWith(mainUrl)) {
            link.replace(mainUrl, "")
        } else {
            aliasUrls.firstOrNull { link.startsWith(it) }?.let { link.replace(it, "") } 
                ?: link.substringAfter("com")
        }

        // Headers for initial request
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to mainUrl
        )

        // Get initial page
        val sourceUrl = mainUrl + linkJustParameter
        val sourceHtml = Utils.get(sourceUrl, headers)
        
        // Extract JavaScript parameters
        val scriptRegex = Regex("document\\.getElementById\\('botlink'\\)\\.innerHTML\\s*=\\s*'([^']+)'\\s*\\+\\s*\\('([^']+)'\\)\\.substring\\(([0-9]+)\\)")
        val scriptMatch = scriptRegex.find(sourceHtml) 
            ?: throw Exception("botlink JavaScript not found")
        
        val baseUrl = scriptMatch.groupValues[1]
        val paramString = scriptMatch.groupValues[2]
        val substringIndex = scriptMatch.groupValues[3].toInt()
        
        // Apply substring
        val cleanParams = paramString.substring(substringIndex)
        
        // Extract parameters
        val idRegex = Regex("id=([^&]+)")
        val expiresRegex = Regex("expires=([^&]+)")
        val ipRegex = Regex("ip=([^&]+)")
        val tokenRegex = Regex("token=([^&]+)")
        
        val videoId = idRegex.find(cleanParams)?.groupValues?.get(1) ?: throw Exception("video id not found")
        val expires = expiresRegex.find(cleanParams)?.groupValues?.get(1) ?: throw Exception("expires not found")
        val ip = ipRegex.find(cleanParams)?.groupValues?.get(1) ?: throw Exception("ip not found")
        val token = tokenRegex.find(cleanParams)?.groupValues?.get(1) ?: throw Exception("token not found")
        
        val finalVideoUrl = "$mainUrl/get_video?id=$videoId&expires=$expires&ip=$ip&token=$token&stream=1"
        val request = Request.Builder()
            .url(finalVideoUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", sourceUrl)
            .build()
        
        val response = Utils.httpClient.newCall(request).execute()
        val redirectedUrl = response.request.url.toString()
        response.close()

        return Video(
            source = redirectedUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to mainUrl
            ),
            subtitles = emptyList()
        )
    }
}
package com.saikou.sozo_tv.parser.anime

import android.util.Log
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.data.model.hianime.MegaTrack
import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AnimeLokParser : BaseParser() {

    override val name = "AnimeLok"
    override val saveName = "anime_lok"
    override val hostUrl = "https://animelok.site"
    override val language = "hindi"

    companion object {
        private const val TAG = "AnimeLok"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$hostUrl/search?keyword=$encodedQuery"

                val headers = mapOf("User-Agent" to USER_AGENT)
                val doc = Utils.getJsoup(url, headers)

                val results = doc.select("a[href^=/anime/]").mapNotNull { element ->
                    try {
                        val href = element.attr("href")
                        val name =
                            element.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
                        val img = element.selectFirst("img")
                        val coverUrl = img?.attr("src") ?: ""

                        val epsText =
                            element.select("span.text-gray-300.bg-gray-800").firstOrNull()?.text()
                                ?: ""
                        val total =
                            Regex("(\\d+)\\s*EPS").find(epsText)?.groupValues?.get(1)?.toIntOrNull()
                        val type =
                            element.select("span.uppercase.tracking-wider").firstOrNull()?.text()
                                ?.trim() ?: ""
                        val year =
                            element.select("span.opacity-70").firstOrNull()?.text()?.trim() ?: ""

                        val link = "$hostUrl${href}?ep=1"

                        ShowResponse(
                            name = name,
                            link = link,
                            coverUrl = coverUrl,
                            total = total,
                            extra = mapOf(
                                "type" to type,
                                "year" to year
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing search item: ${e.message}")
                        null
                    }
                }.distinctBy { it.link }

                return@withContext results
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(
        id: String,
        page: Int,
        showResponse: ShowResponse
    ): EpisodeData? {
        return withContext(Dispatchers.IO) {
            try {
                val slug = if (id.contains("/anime/")) {
                    id.substringAfter("/anime/").substringBefore("?")
                } else {
                    id.substringBefore("?")
                }
                val apiPage = if (page > 0) page - 1 else 0

                val apiUrl =
                    "$hostUrl/api/anime/$slug/episodes-range?page=$apiPage&lang=${language.uppercase()}&pageSize=25"

                val headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Referer" to "$hostUrl/watch/$slug?ep=1",
                    "Alt-Used" to "animelok.site",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin"
                )

                val response = Utils.get(apiUrl, headers)
                val json = JSONObject(response)

                val episodesArray = json.getJSONArray("episodes")
                val totalPages = json.optInt("totalPages", 1)

                val episodes = mutableListOf<Data>()
                for (i in 0 until episodesArray.length()) {
                    val ep = episodesArray.getJSONObject(i)
                    episodes.add(
                        Data(
                            id = ep.optInt("number", i + 1),
                            episode = ep.optInt("number", i + 1),
                            title = ep.optString("name", "Episode ${ep.optInt("number", i + 1)}"),
                            snapshot = ep.optString("img", showResponse.coverUrl),
                            session = slug,
                            filler = if (ep.optBoolean("isFiller", false)) 1 else 0
                        )
                    )
                }

                val currentPage = apiPage + 1
                val perPage = 25

                EpisodeData(
                    current_page = currentPage,
                    data = episodes,
                    from = (apiPage * perPage) + 1,
                    last_page = totalPages,
                    next_page_url = if (currentPage < totalPages) "$apiUrl&page=${apiPage + 1}" else null,
                    per_page = perPage,
                    prev_page_url = if (apiPage > 0) "$apiUrl&page=${apiPage - 1}" else null,
                    to = (apiPage * perPage) + episodes.size,
                    total = totalPages * perPage
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes: ${e.message}")
                null
            }
        }
    }

    override suspend fun getEpisodeVideo(id: String, epId: String, epNum: Int): List<VideoOption> {
        return withContext(Dispatchers.IO) {
            try {
                val slug = if (id.contains("/anime/")) {
                    id.substringAfter("/anime/").substringBefore("?")
                } else {
                    id.substringBefore("?")
                }

                Log.d(TAG, "getEpisodeVideo: slug=$slug, epId=$epNum")

                val apiUrl = "$hostUrl/api/anime/$slug/episodes/$epNum"
                Log.d(TAG, "API URL: $apiUrl")

                val headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Referer" to "$hostUrl/watch/$slug?ep=$epId",
                    "Alt-Used" to "animelok.site",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin"
                )

                val allTracks = mutableListOf<MegaTrack>()
                var asCdnUrl: String? = null

                // 1-request
                val response1 = Utils.get(apiUrl, headers)
                Log.d(TAG, "Response1 length: ${response1.length}")
                Log.d(TAG, "Response1 first 500: ${response1.take(500)}")

                val json1 = JSONObject(response1)
                Log.d(TAG, "JSON keys: ${json1.keys().asSequence().toList()}")

                // episode yoki to'g'ridan-to'g'ri tekshirish
                val episode1 = if (json1.has("episode")) {
                    json1.getJSONObject("episode")
                } else {
                    Log.w(TAG, "No 'episode' key, using root JSON")
                    json1
                }

                val servers1 = if (episode1.has("servers")) {
                    episode1.getJSONArray("servers")
                } else {
                    Log.w(TAG, "No 'servers' key in episode")
                    JSONArray()
                }
                Log.d(TAG, "Servers count: ${servers1.length()}")

                // API dagi subtitlelar
                val subtitlesArray = episode1.optJSONArray("subtitles")
                if (subtitlesArray != null) {
                    Log.d(TAG, "Subtitles count: ${subtitlesArray.length()}")
                    val addedUrls = mutableSetOf<String>()
                    for (i in 0 until subtitlesArray.length()) {
                        val sub = subtitlesArray.getJSONObject(i)
                        val subUrl = sub.optString("url", "")
                        val subName = sub.optString("name", "Unknown")
                        if (subUrl.isNotEmpty() && addedUrls.add(subUrl)) {
                            allTracks.add(
                                MegaTrack(file = subUrl, label = subName, kind = "captions")
                            )
                        }
                    }
                }

                // 1-responseda as-cdn izlash
                asCdnUrl = findAsCdnUrl(servers1)
                Log.d(TAG, "1st request as-cdn: $asCdnUrl")

                // Agar 1-da topilmasa, 2-request
                if (asCdnUrl == null) {
                    Log.d(TAG, "as-cdn not found, waiting 1.5s for 2nd request...")
                    delay(1500)
                    val response2 = Utils.get(apiUrl, headers)
                    val json2 = JSONObject(response2)
                    val episode2 =
                        if (json2.has("episode")) json2.getJSONObject("episode") else json2
                    val servers2 =
                        if (episode2.has("servers")) episode2.getJSONArray("servers") else JSONArray()
                    Log.d(TAG, "2nd request servers count: ${servers2.length()}")
                    asCdnUrl = findAsCdnUrl(servers2)
                    Log.d(TAG, "2nd request as-cdn: $asCdnUrl")
                }

                val videoOptions = mutableListOf<VideoOption>()

                if (asCdnUrl != null) {
                    Log.d(TAG, "Processing as-cdn: $asCdnUrl")
                    val cdnHeaders = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*",
                        "Referer" to asCdnUrl
                    )
                    val pageHtml = Utils.get(asCdnUrl, cdnHeaders)
                    Log.d(TAG, "CDN page length: ${pageHtml.length}")

                    val baseUrl = asCdnUrl.substringBefore("/video/")
                    val hash = asCdnUrl.substringAfter("/video/")
                    Log.d(TAG, "baseUrl=$baseUrl, hash=$hash")

                    // JsUnpacker bilan thumbnail
                    var thumbnailUrl = ""
                    val unpacker = JsUnpacker(pageHtml)
                    if (unpacker.detect()) {
                        Log.d(TAG, "Packed JS detected, unpacking...")
                        val unpacked = unpacker.unpack()
                        if (unpacked != null) {
                            Log.d(TAG, "Unpacked length: ${unpacked.length}")
                            val trackRegex =
                                Regex(""""file"\s*:\s*"(https?://[^"]+\.jpg)"\s*,\s*"kind"\s*:\s*"thumbnails"""")
                            val trackMatch = trackRegex.find(unpacked)
                            if (trackMatch != null) {
                                thumbnailUrl = trackMatch.groupValues[1]
                            }
                            Log.d(TAG, "Unpacked and found:${trackMatch != null}, thumbnailUrl=$thumbnailUrl")
                        }
                    } else {
                        Log.d(TAG, "No packed JS detected")
                    }

                    if (thumbnailUrl.isEmpty()) {
                        val thumbRegex =
                            Regex(""""file"\s*:\s*"(https?://[^"]+\.jpg)"[^}]*"kind"\s*:\s*"thumbnails"""")
                        val thumbMatch = thumbRegex.find(pageHtml)
                        if (thumbMatch != null) {
                            thumbnailUrl = thumbMatch.groupValues[1]
                        }
                    }
                    Log.d(TAG, "Thumbnail: $thumbnailUrl")

                    if (thumbnailUrl.isNotEmpty()) {
                        allTracks.add(
                            MegaTrack(
                                file = thumbnailUrl,
                                label = "thumbnails",
                                kind = "thumbnails"
                            )
                        )
                    }

                    // getVideo POST
                    val postUrl = "$baseUrl/player/index.php?data=$hash&do=getVideo"
                    Log.d(TAG, "POST URL: $postUrl")
                    val postHeaders = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to baseUrl,
                        "Alt-Used" to baseUrl.removePrefix("https://"),
                        "Referer" to asCdnUrl,
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "same-origin"
                    )
                    val postPayload = mapOf("hash" to hash, "r" to "")
                    val postResponse = Utils.post(postUrl, postHeaders, postPayload)
                    Log.d(TAG, "POST response: ${postResponse.take(300)}")

                    val videoJson = JSONObject(postResponse)
                    val masterM3u8 = videoJson.optString(
                        "securedLink",
                        videoJson.optString("videoSource", "")
                    )
                    Log.d(TAG, "Master m3u8: $masterM3u8")

                    if (masterM3u8.isNotEmpty()) {
                        val m3u8Headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Accept" to "*/*",
                            "Referer" to "$baseUrl/",
                            "Origin" to baseUrl,
                            "Sec-Fetch-Dest" to "empty",
                            "Sec-Fetch-Mode" to "cors",
                            "Sec-Fetch-Site" to "same-origin"
                        )

                        videoOptions.add(
                            VideoOption(
                                videoUrl = masterM3u8,
                                fansub = "AnimeLok",
                                resolution = "Auto",
                                audioType = AudioType.DUB,
                                quality = "Multi Audio - Auto",
                                isActive = true,
                                mimeTypes = MimeTypes.APPLICATION_M3U8,
                                fullText = "Multi Audio - Auto",
                                tracks = allTracks,
                                headers = m3u8Headers
                            )
                        )
                        Log.d(TAG, "VideoOption added: tracks=${allTracks.size}")
                    } else {
                        Log.e(TAG, "Empty master m3u8!")
                    }
                } else {
                    Log.e(TAG, "as-cdn URL not found in both requests!")
                }

                Log.d(TAG, "Total video options: ${videoOptions.size}")
                return@withContext videoOptions
            } catch (e: Exception) {
                Log.e(TAG, "Error getting episode video: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                emptyList()
            }
        }
    }

    private fun findAsCdnUrl(servers: JSONArray): String? {
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            val url = server.optString("url", "")
            if (url.contains("as-cdn") && url.contains("/video/")) {
                return url
            }
        }
        return null
    }

    override suspend fun extractVideo(url: String): Video {
        return withContext(Dispatchers.IO) {
            try {
                if (url.endsWith(".m3u8") || url.contains(".m3u8?")) {
                    return@withContext Video(
                        source = url,
                        type = MimeTypes.APPLICATION_M3U8
                    )
                }
                Log.e(TAG, "Error extracting video: $url")
                Video(source = "")
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video: ${e.message}")
                Video(source = "")
            }
        }
    }
}
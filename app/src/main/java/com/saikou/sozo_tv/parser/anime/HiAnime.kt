package com.saikou.sozo_tv.parser.anime

import android.util.Log
import com.saikou.sozo_tv.data.remote.KitsuApi
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.extractor.HiAnimeVideoExtractor
import com.saikou.sozo_tv.parser.extractor.MegacloudExtractor
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.sources.HiAnimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class HiAnime : BaseParser() {
    override val name: String = "HiAnime"
    override val saveName: String = "hianime"
    override val hostUrl: String = "https://hianime.bz/"
    override val language: String = "en"

    private val source = HiAnimeSource()
    private val kitsuApi = KitsuApi()
    private val extractor = HiAnimeVideoExtractor()

    override suspend fun search(query: String): List<ShowResponse> {
        val map = source.searchAnime(query).map {
            ShowResponse(
                name = it.title, link = it.id.toString(), coverUrl = it.imageUrl
            )
        }
        Log.d("GGG", "search:${map} ")
        return map
    }

    override suspend fun loadEpisodes(
        id: String,
        page: Int,
        showResponse: ShowResponse
    ): EpisodeData? {
        val animeId = id.toInt()
        val searchId = kitsuApi.searchId(showResponse.name).toString()
        if (!episodeCache.containsKey(animeId)) {
            val episodes = source.getEpisodeListById(animeId)
            if (episodes.isEmpty()) return null
            episodeCache[animeId] = episodes
        }
        val allEpisodes = episodeCache[animeId] ?: return null

        val perPage = 50
        val offset = (page - 1) * perPage
        if (offset >= allEpisodes.size) return null

        val paginatedEpisodes = allEpisodes.drop(offset).take(perPage)

        val neededNumbers = paginatedEpisodes.map { it.episode }.toSet()

        val cachedThumbnails = thumbnailCache.getOrPut(searchId) { mutableMapOf() }
        val thumbnailMap = mutableMapOf<Int, String?>()
        val missingNumbers = mutableSetOf<Int>()
        neededNumbers.forEach { num ->
            val cached = cachedThumbnails[num]
            if (cached != null) {
                thumbnailMap[num] = cached
            } else {
                missingNumbers.add(num)
            }
        }
        if (missingNumbers.isNotEmpty()) {
            val isFullyFetched = kitsuFullyFetched.getOrDefault(searchId, false)
            if (isFullyFetched) {
                missingNumbers.forEach { thumbnailMap[it] = null }
                missingNumbers.clear()
            } else {
                var currentPage = kitsuNextPage.getOrDefault(searchId, 0)
                while (missingNumbers.isNotEmpty() && currentPage < 1000) {
                    val episodePage = kitsuApi.getEpisodes(searchId, currentPage)
                    if (episodePage.isEmpty()) {
                        kitsuFullyFetched[searchId] = true
                        kitsuNextPage[searchId] = currentPage
                        break
                    }

                    episodePage.forEach { kitsuEp ->
                        val thumb = kitsuEp.thumbnail
                        cachedThumbnails[kitsuEp.number] = thumb
                        if (kitsuEp.number in missingNumbers) {
                            thumbnailMap[kitsuEp.number] = thumb
                            missingNumbers.remove(kitsuEp.number)
                        }
                    }
                    currentPage++
                }
                kitsuNextPage[searchId] = currentPage
                if (missingNumbers.isNotEmpty()) {
                    kitsuFullyFetched[searchId] = true
                }
                Log.d(
                    "GGG",
                    "Fetched up to page $currentPage for ${missingNumbers.size} remaining missing thumbnails"
                )
            }
        }

        val mappedData: List<Data> = paginatedEpisodes.map { ep ->
            Data(
                id = ep.iframeUrl.toInt(),
                episode = ep.episode,
                session = ep.iframeUrl,
                title = ep.title,
                snapshot = thumbnailMap[ep.episode] ?: showResponse.coverUrl,
                season = ep.season,
                episode2 = null,
                audio = null,
                filler = 0,
            )
        }

        val totalEpisodes = allEpisodes.size
        val lastPage = (totalEpisodes + perPage - 1) / perPage

        return EpisodeData(
            current_page = page,
            data = mappedData,
            from = offset + 1,
            last_page = lastPage,
            next_page_url = if (page < lastPage) "/episodes/$id?page=${page + 1}" else null,
            per_page = perPage,
            prev_page_url = if (page > 1) "/episodes/$id?page=${page - 1}" else null,
            to = offset + mappedData.size,
            total = totalEpisodes
        )
    }

    override suspend fun getEpisodeVideo(id: String, epId: String): List<VideoOption> =
        coroutineScope {

            Log.d("GGG", "getEpisodeVideo: $id | epId: $epId")

            val servers = extractor.extractServers(epId.toInt())
            if (servers.isEmpty()) return@coroutineScope emptyList()

            Log.d("GGG", "Servers: $servers")

            val mega = MegacloudExtractor()
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Origin" to "https://megacloud.blog",
                "Referer" to "https://megacloud.blog/"
            )

            val jobs = servers.map { server ->
                async {
                    val embedUrl = extractor.extractVideoFromServer(server.id)
                    val (m3u8, tracks) = mega.extractVideoUrl(embedUrl)

                    VideoOption(
                        kwikUrl = m3u8,
                        fansub = "HiAnime",
                        resolution = "HLS",
                        audioType = if (server.type == "dub") AudioType.DUB else AudioType.SUB,
                        quality = "Adaptive",
                        isActive = true,
                        fullText = "HiAnime Stream",
                        tracks = tracks,
                        headers = headers
                    )
                }
            }

            jobs.awaitAll()
        }


    companion object {

        private val episodeCache = mutableMapOf<Int, List<Episode>>()
        private val thumbnailCache = mutableMapOf<String, MutableMap<Int, String?>>()
        private val kitsuNextPage = mutableMapOf<String, Int>()
        private val kitsuFullyFetched = mutableMapOf<String, Boolean>()
    }
}
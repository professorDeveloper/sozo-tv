package com.saikou.sozo_tv.parser.anime

import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.extractor.HiAnimeVideoExtractor
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeHi
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.sources.HiAnimeSource
import com.saikou.sozo_tv.utils.toDomain


class HiAnime : BaseParser() {

    override val name: String = "HiAnime"
    override val saveName: String = "hianime"
    override val hostUrl: String = "https://hianime.bz/"
    override val language: String = "en"

    private val source = HiAnimeSource()
    private val videoExtractor = HiAnimeVideoExtractor()

    suspend fun search(query: String): List<ShowResponse> {
        return source.searchAnime(query).map {
            ShowResponse(
                name = it.title,
                link = it.link,
                coverUrl = it.imageUrl
            )
        }
    }

    suspend fun loadEpisodes(id: String, page: Int): List<Episode>? {
        val animeId = id.extractAnimeId() ?: return null
        val eps = source.getEpisodeListById(animeId)
        return eps
    }


    private fun String.extractAnimeId(): Int? {
        return Regex("-(\\d+)").find(this)?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }
}

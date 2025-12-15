package com.saikou.sozo_tv.parser.movie

import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse

class VidSrc : BaseParser() {
    override val hostUrl: String = "https://vidsrc.cc/v2/embed"
    override val isNSFW: Boolean = false
    override val language: String = "any"
    override val name: String = "vidSrc"
    override val saveName: String = "vidSrc"
    override suspend fun loadEpisodes(
        id: String,
        page: Int,
        showResponse: ShowResponse
    ): EpisodeData? {
        TODO("FUCK THEN")
    }

    override suspend fun search(query: String): List<ShowResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun extractVideo(url: String): String {
        return "null"
    }
}
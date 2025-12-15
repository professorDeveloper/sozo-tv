package com.saikou.sozo_tv.parser.movie

import android.util.Log
import com.google.gson.Gson
import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.data.model.Backdrop
import com.saikou.sozo_tv.data.model.SeasonResponse
import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.parser

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
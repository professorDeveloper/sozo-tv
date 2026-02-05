package com.saikou.sozo_tv.parser.sources

import android.util.Log
import com.google.gson.Gson
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeResponse
import com.saikou.sozo_tv.parser.models.HiAnime
import com.saikou.sozo_tv.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class HiAnimeSource {

    private val BASE_URL = "https://hianime.bz"

    suspend fun searchAnime(keyword: String): List<HiAnime> = withContext(Dispatchers.IO) {
        try {
            val doc = Utils.getJsoup("$BASE_URL/search?keyword=$keyword")

            val items = doc.select("div.flw-item")
            val result = items.map { item ->
                val poster = item.selectFirst(".film-poster")
                val detail = item.selectFirst(".film-detail")

                val title = detail?.selectFirst(".film-name a")?.attr("title") ?: ""
                val link = BASE_URL + (detail?.selectFirst(".film-name a")?.attr("href") ?: "")
                val imageUrl = poster?.selectFirst("img")?.attr("data-src") ?: ""
                val type = detail?.selectFirst(".fd-infor .fdi-item")?.text() ?: ""
                val duration = detail?.selectFirst(".fdi-duration")?.text() ?: ""

                val subCount =
                    poster?.selectFirst(".tick-item.tick-sub")?.ownText()?.toIntOrNull()
                val dubCount =
                    poster?.selectFirst(".tick-item.tick-dub")?.ownText()?.toIntOrNull()

                HiAnime(
                    id = link.extractAnimeId() ?: 0,
                    title = title,
                    imageUrl = imageUrl,
                    type = type,
                    duration = duration,
                    link = link,
                    subCount = subCount,
                    dubCount = dubCount
                )
            }.toList()

            Log.d("GGG", result.toString())
            result
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("GGG", "searchAnime:${e} ")
            emptyList()
        }
    }

    suspend fun getEpisodeListById(id: Int): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val json = Utils.get("$BASE_URL/ajax/v2/episode/list/$id")
            Log.d("GGG", "getEpisodeListById: $BASE_URL/ajax/v2/episode/list/$id")
            Log.d("GGG", "getEpisodeListById: $json")
            val episodeResponse = Gson().fromJson(json, EpisodeResponse::class.java)

            val doc = Jsoup.parse(episodeResponse.html)
            val items = doc.select("a.ssl-item.ep-item")

            for (item in items) {
                val number = item.attr("data-number").toIntOrNull() ?: continue
                val epId = item.attr("data-id").toIntOrNull() ?: continue
                val title = item.attr("title")

                episodes.add(
                    Episode(
                        iframeUrl = epId.toString(),
                        episode = number,
                        title = title,
                        season = -1
                    )
                )
            }

            episodes
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("GGG", "getEpisodeListById:${e.message} ")
            emptyList()
        }
    }

    private fun String.extractAnimeId(): Int? {
        val cleanString = this.split('?')[0].split('#')[0].substringAfterLast('/')
        return Regex("-(\\d+)$").find(cleanString)?.groupValues?.get(1)?.toIntOrNull()
    }


}
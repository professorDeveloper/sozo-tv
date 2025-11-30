package com.saikou.sozo_tv.data.remote

import com.google.gson.Gson
import com.saikou.sozo_tv.data.model.kitsu.KitsuEpisode
import com.saikou.sozo_tv.data.model.kitsu.KitsuEpisodeResponse
import com.saikou.sozo_tv.data.model.kitsu.KitsuSearchResponse
import com.saikou.sozo_tv.utils.Utils

class KitsuApi {

    private val gson = Gson()
    private val base = "https://kitsu.io/api/edge"

    suspend fun searchId(query: String): String? {
        val encoded = query.replace(" ", "%20")
        val json = Utils.get("$base/anime?filter[text]=$encoded")

        val result = gson.fromJson(json, KitsuSearchResponse::class.java)

        return result.data?.firstOrNull()?.id
    }


    suspend fun getEpisodes(animeId: String, page: Int): List<KitsuEpisode> {
        val limit = 20
        val offset = page * limit

        val url =
            "$base/anime/$animeId/episodes?page[limit]=$limit&page[offset]=$offset"

        val json = Utils.get(url)
        val result = gson.fromJson(json, KitsuEpisodeResponse::class.java)
        val list = result.data ?: return emptyList()

        return list.mapNotNull { ep ->
            val attr = ep.attributes ?: return@mapNotNull null

            KitsuEpisode(
                id = ep.id ?: "",
                number = attr.number ?: 0,
                title = attr.canonicalTitle
                    ?: attr.titles?.en_us
                    ?: attr.titles?.en_jp
                    ?: attr.titles?.ja_jp
                    ?: "Episode ${attr.number}",
                description = attr.description ?: "",
                thumbnail = attr.thumbnail?.original ?: ""
            )
        }
    }
}
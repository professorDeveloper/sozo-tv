package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.model.TrailerModel
import com.saikou.sozo_tv.utils.Utils

class LiveChartTrailer() {
    private val BASE_URL = "https://www.livechart.me"
    suspend fun searchAndGetTrailer(animeTitle: String): TrailerModel {
        val doc = Utils.getJsoup(
            "$BASE_URL/search?q=$animeTitle",
            mapOf(
                "authority" to "www.livechart.me",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
                "Accept-Language" to "en-US,en;q=0.9",
            )
        )
        val firstItem = doc.selectFirst("li.grouped-list-item.anime-item")!!

        val id = firstItem.attr("data-anime-id").toInt()
        val title = firstItem.attr("data-title")
        val imageUrl = firstItem.selectFirst("img")?.attr("src").orEmpty()
        val detailsUrl =
            "https://www.livechart.me" + firstItem.selectFirst("a[data-anime-item-target=mainTitle]")
                ?.attr("href").orEmpty()
        val episodeText = firstItem.selectFirst("span.title-extra")?.text()?.trim() ?: "(TV, 0 eps)"
        val episodeCount = Regex("""\d+""").find(episodeText)?.value?.toIntOrNull() ?: 0
        val premiereDate = firstItem.selectFirst(".info span")?.text().orEmpty()
        return TrailerModel(detailsUrl)
    }
}


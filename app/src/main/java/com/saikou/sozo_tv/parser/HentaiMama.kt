package com.saikou.sozo_tv.parser

import com.lagradost.nicehttp.Requests
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Utils
import com.saikou.sozo_tv.utils.parser

class HentaiMama : BaseParser() {
    override val name: String = "hentaimama"
    override val saveName: String = "hentai_mama"
    override val hostUrl: String = "https://hentaimama.io"
    override val isNSFW: Boolean = true
    override val language: String = "jp"

    suspend fun search(query: String): List<ShowResponse> {
        val client = Requests(Utils.httpClient, responseParser = parser)
        val url = "$hostUrl/?s=${query.replace(" ", "+")}"
        val document = client.get(url).document

        return document.select("div.result-item article").map {
            val link = it.select("div.details div.title a").attr("href")
            val title = it.select("div.details div.title a").text()
            val cover = it.select("div.image div a img").attr("src")
            ShowResponse(title, link, cover)
        }
    }

    suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Data> {
        val pageBody = Utils.getJsoup(animeLink)

        val episodes =
            pageBody.select("div#episodes.sbox.fixidtab div.module.series div.content.series div.items article")
                .reversed()
                .map { article ->
                    // Extract episode number from the h3 text
                    val epNum = article.select("div.data h3").text().replace("Episode", "").trim()

                    // Extract episode URL from the season_m div (remove .animation-3 class)
                    val url = article.select("div.poster div.season_m a").attr("href")

                    // Extract thumbnail from img data-src attribute
                    val thumb = article.select("div.poster img").attr("data-src")
                    Data(episode = epNum.toInt(), session = url ?: "", snapshot = thumb ?: "")
                }

        return episodes
    }

}
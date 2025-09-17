package com.saikou.sozo_tv.parser

import com.lagradost.nicehttp.Requests
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
}
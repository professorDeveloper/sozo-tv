package com.saikou.sozo_tv.parser.extractor

import android.util.Log
import com.google.gson.Gson
import com.saikou.sozo_tv.data.model.hianime.EpisodeServers
import com.saikou.sozo_tv.data.model.hianime.HiServer
import com.saikou.sozo_tv.data.model.hianime.MegaTrack
import com.saikou.sozo_tv.data.model.hianime.ServerResponse
import com.saikou.sozo_tv.utils.Utils
import org.jsoup.Jsoup

class HiAnimeVideoExtractor {

    private val gson = Gson()
    private val base = "https://hianime.bz"

    fun extractServers(episodeId: Int): List<HiServer> {
        val json = Utils.get("$base/ajax/v2/episode/servers?episodeId=$episodeId")
        val resp = gson.fromJson(json, EpisodeServers::class.java)
        val doc = Jsoup.parse(resp.html)

        return doc.select(".server-item[data-id]").mapNotNull { el ->
            val id = el.attr("data-id")
            val label = el.selectFirst("a.btn")?.text().orEmpty()
            val type = el.attr("data-type")

            if (id.isBlank() || type.isBlank()) {
                null
            } else {
                HiServer(
                    id = id,
                    label = label,
                    type = type
                )
            }
        }
    }

    fun extractVideoFromServer(serverId: String): String {
        val json = Utils.get("$base/ajax/v2/episode/sources?id=$serverId")
        val source = gson.fromJson(json, ServerResponse::class.java).link
        return source
    }

    fun extractMegacloudVideo(url: String): Pair<String, List<MegaTrack>> {
        val extractor = MegacloudExtractor()
        val (m3u8, tracks) = extractor.extractVideoUrl(url)
        println("Subtitles: $tracks")
        return Pair(m3u8, tracks)
    }
}

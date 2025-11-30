package com.saikou.sozo_tv.data.model.hianime

data class HiAnimeShow(
    val id: Int,
    val title: String,
    val poster: String,
    val link: String
)

data class HiAnimeEpisode(
    val number: Int,
    val episodeId: Int,
    val title: String,
    val link: String
)

data class HiAnimeServer(
    val serverId: String,
    val label: String
)

data class EpisodeResponse(
    val html: String
)

data class EpisodeServers(
    val html: String
)

data class ServerSourceResponse(
    val link: String
)

data class HiAnimeVideoResult(
    val m3u8: String,
    val subtitles: List<MegaTrack>,
    val headers: Map<String, String>
)

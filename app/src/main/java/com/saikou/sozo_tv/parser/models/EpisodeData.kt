package com.saikou.sozo_tv.parser.models

data class EpisodeData(
    val current_page: Int?,
    val `data`: List<Data>?,
    val from: Int?,
    val last_page: Int?,
    val next_page_url: String?,
    val per_page: Int,
    val prev_page_url: String?,
    val to: Int,
    val total: Int
)


data class AnimePaheData(
    val current_page: Int,
    val `data`: List<DataD>,
    val from: Int,
    val last_page: Int,
    val per_page: Int,
    val to: Int,
    val total: Int
)

data class DataD(
    val episodes: Int,
    val id: Int?,
    val poster: String?,
    val score: Double?,
    val season: String?,
    val session: String?,
    val status: String?,
    val title: String?,
    val type: String?,
    val year: Int?
)

data class Data(
    val anime_id: Int? = null,
    val audio: String? = null,
    val created_at: String? = null,
    val disc: String? = null,
    val duration: String? = null,
    val edition: String? = null,
    val episode: Int? = null,
    val
    episode2: Int? = null,
    val filler: Int? = null,
    val id: Int? = null,
    val session: String? = null,
    val snapshot: String? = null,
    val title: String? = null,
    val season: Int = 0,
)
package com.saikou.sozo_tv.data.model.kitsu
data class KitsuEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val description: String,
    val thumbnail: String
)

data class KitsuEpisodeResponse(
    val data: List<KitsuEpisodeData>?
)

data class KitsuEpisodeData(
    val id: String?,
    val attributes: KitsuEpisodeAttributes?
)

data class KitsuEpisodeAttributes(
    val number: Int?,
    val canonicalTitle: String?,
    val description: String?,
    val titles: KitsuTitles?,
    val thumbnail: KitsuThumbnail?
)
data class KitsuSearchResponse(
    val data: List<KitsuSearchItem>?
)

data class KitsuSearchItem(
    val id: String?
)

data class KitsuTitles(
    val en_jp: String?,
    val en_us: String?,
    val ja_jp: String?
)

data class KitsuThumbnail(
    val original: String?
)

package com.saikou.sozo_tv.data.model.jikan

import com.saikou.sozo_tv.utils.LocalData

data class Data(
    val aired: com.saikou.sozo_tv.data.model.jikan.Aired,
    val airing: Boolean,
    val approved: Boolean,
    val background: String,
    val broadcast: com.saikou.sozo_tv.data.model.jikan.Broadcast,
    val demographics: List<com.saikou.sozo_tv.data.model.jikan.Demographic>,
    val duration: String,
    val episodes: Int,
    val explicit_genres: List<Any?>,
    val favorites: Int,
    val genres: List<com.saikou.sozo_tv.data.model.jikan.Genre>,
    val images: com.saikou.sozo_tv.data.model.jikan.Images,
    val licensors: List<com.saikou.sozo_tv.data.model.jikan.Licensor>,
    val mal_id: Int,
    val members: Int,
    val popularity: Int,
    val producers: List<com.saikou.sozo_tv.data.model.jikan.Producer>,
    val rank: Int,
    val rating: String,
    val score: Double,
    val scored_by: Int,
    val season: String,
    val source: String,
    val status: String,
    val studios: List<com.saikou.sozo_tv.data.model.jikan.Studio>,
    val synopsis: String,
    val themes: List<com.saikou.sozo_tv.data.model.jikan.Theme>,
    val title: String,
    val title_english: String,
    val title_japanese: String,
    val title_synonyms: List<String>,
    val titles: List<com.saikou.sozo_tv.data.model.jikan.Title>,
    val trailer: com.saikou.sozo_tv.data.model.jikan.Trailer,
    val type: String,
    val url: String,
    val year: Int
)

data class BannerHomeData(
    val image: String,
    val title: String,
    val description: String,
    val mal_id: Int = -1,
    val imdb_id: Int = -1,
    val isMovie:Boolean = false
) {
}
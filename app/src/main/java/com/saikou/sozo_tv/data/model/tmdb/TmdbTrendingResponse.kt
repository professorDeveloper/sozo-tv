package com.saikou.sozo_tv.data.model.tmdb

import com.saikou.sozo_tv.utils.LocalData

data class TmdbTrendingResponse(
    val results: List<TmdbTrendingItem>
)

data class TmdbTrendingItem(
    val id: Int?,
    val title: String?,
    val name: String?,
    val backdrop_path: String?,
    val poster_path: String?,
    val media_type: String?
) {
    val imageUrl: String?
        get() = poster_path?.let { LocalData.IMDB_IMAGE_PATH+ it }
}

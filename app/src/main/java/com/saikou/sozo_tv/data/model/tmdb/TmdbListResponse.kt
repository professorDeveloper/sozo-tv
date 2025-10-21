package com.saikou.sozo_tv.data.model.tmdb

import com.saikou.sozo_tv.utils.LocalData

data class TmdbListResponse(
    val page: Int?,
    val total_pages: Int?,
    val total_results: Int?,
    val results: List<TmdbListItem>
)

data class TmdbListItem(
    val id: Int?,
    val name: String?,
    val title: String?,
    val original_name: String?,
    val backdrop_path: String?,
    val poster_path: String?,
    val media_type: String?,
    var overview: String?,
    val genre_ids: List<Int>,
    val release_date: String,

) {
    val titleFormat: String?
        get() = name ?: original_name ?: title
    val imageUrl: String?
        get() = poster_path?.let { LocalData.IMDB_IMAGE_PATH + it }

}

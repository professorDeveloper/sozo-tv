package com.saikou.sozo_tv.data.model.tmdb.cast

import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.utils.LocalData

data class Cast(
    val adult: Boolean,
    val backdrop_path: String,
    val character: String,
    val credit_id: String,
    val genre_ids: List<Int>,
    val id: Int,
    val order: Int,
    val original_language: String,
    @SerializedName("original_title")
    val original_title: String?,
    val overview: String,
    val popularity: Double,
    val poster_path: String?,
    val release_date: String,
    @SerializedName("title")
    val title: String?,
    val video: Boolean,
    val vote_average: Double,
    val vote_count: Int
) {
    val titleFormat get() = title ?: original_title ?: ""
    val imageUrl get() = "${LocalData.IMDB_IMAGE_PATH}${poster_path}"
}
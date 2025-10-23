package com.saikou.sozo_tv.data.model.tmdb.cast

data class MediaCast(
    val adult: Boolean,
    val cast_id: Int,
    val character: String,
    val credit_id: String,
    val gender: Int,
    val id: Int,
    val known_for_department: String,
    val name: String,
    val order: Int,
    val original_name: String,
    val popularity: Double,
    val profile_path: String
) {
    val profileImg get() = "https://image.tmdb.org/t/p/w500/$profile_path"

}
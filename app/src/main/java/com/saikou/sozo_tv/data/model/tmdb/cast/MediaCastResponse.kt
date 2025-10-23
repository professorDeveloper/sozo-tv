package com.saikou.sozo_tv.data.model.tmdb.cast

data class MediaCastResponse(
    val cast: List<MediaCast>,
    val crew: List<Crew>,
    val id: Int
)
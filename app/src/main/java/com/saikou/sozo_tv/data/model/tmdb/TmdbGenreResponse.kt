package com.saikou.sozo_tv.data.model.tmdb

data class TmdbGenreResponse(
    val genres: List<TmdbGenreRaw>
)

data class TmdbGenreRaw(
    val id: Int,
    val name: String
)

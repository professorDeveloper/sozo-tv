package com.saikou.sozo_tv.domain.model

import java.io.Serializable

data class GenreModel(
    val title: String,
    val image: String,
) : Serializable

data class GenreTmdbModel(
    val title: String,
    val image: String,
    val id: Int,
)
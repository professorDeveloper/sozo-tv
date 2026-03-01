package com.saikou.sozo_tv.data.model.anilist

import com.animestudios.animeapp.type.MediaFormat
import com.animestudios.animeapp.type.MediaSource

data class HomeModel(
    val id: Int,
    val idMal: Int,
    val coverImage: CoverImage,
    val format: MediaFormat,
    val source: MediaSource,
    val title: Title,
    val isSeries: Boolean = true,
    val isAnime: Boolean = false
)

data class Title(
    val english: String,
)

data class CoverImage(
    val large: String
)
package com.saikou.sozo_tv.parser.models

data class HiAnime(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val type: String,
    val duration: String,
    val link: String,
    val subCount: Int?,
    val dubCount: Int?
)

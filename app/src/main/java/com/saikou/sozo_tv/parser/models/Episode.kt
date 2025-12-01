package com.saikou.sozo_tv.parser.models

data class Episode(
    val season: Int,
    val episode: Int,
    val title: String,
    val iframeUrl: String,
    var snapshot: String = "",
    var description: String = "",
)

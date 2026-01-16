package com.saikou.sozo_tv.parser.models

import java.io.Serializable

data class Video(
    val source: String,
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap()
) : Serializable {
    data class Subtitle(
        val label: String,
        val url: String
    ) : Serializable

    data class Server(
        val id: String,
        val name: String,
        val src: String
    ) : Serializable

    sealed class Type : Serializable {
        data class Movie(val id: String) : Type()
        data class Episode(
            val tvShow: TvShow,
            val season: Season,
            val number: Int
        ) : Type()

        data class TvShow(val id: String) : Serializable
        data class Season(val number: Int) : Serializable
    }
}
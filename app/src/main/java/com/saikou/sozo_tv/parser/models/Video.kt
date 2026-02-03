package com.saikou.sozo_tv.parser.models

import androidx.media3.common.MimeTypes
import java.io.Serializable

data class Video(
    val source: String,
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val type: String = MimeTypes.APPLICATION_M3U8,
) : Serializable {
    data class Subtitle(
        val label: String,
        val url: String
    ) : Serializable

    data class Server(
        val id: String,
        val name: String,
        val src: String,
        val fileName: String = "",
        val fileSize: String = ""
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
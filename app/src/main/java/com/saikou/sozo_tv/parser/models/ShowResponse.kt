package com.saikou.sozo_tv.parser.models

import java.io.Serializable

data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: String,
    val otherNames: List<String> = listOf(),
    val total: Int? = null,
    val extra : Map<String,String>?=null,
    val seasons: List<Int> = listOf(),
) : Serializable {

}



data class VideoOption(
    val kwikUrl: String,
    val fansub: String,
    val resolution: String,
    val audioType: AudioType,
    val quality: String,
    val isActive: Boolean,
    val fullText: String
)


enum class AudioType {
    SUB, DUB
}

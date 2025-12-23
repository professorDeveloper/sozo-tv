package com.saikou.sozo_tv.parser.models

import com.saikou.sozo_tv.data.model.hianime.MegaTrack
import java.io.Serializable

data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: String,
    val otherNames: List<String> = listOf(),
    val total: Int? = null,
    val extra: Map<String, String>? = null,
    val seasons: List<Int> = listOf(),
) : Serializable {

}


data class VideoOption(
    val videoUrl: String,
    val fansub: String,
    val resolution: String,
    val audioType: AudioType,
    val quality: String,
    val isActive: Boolean,
    val isM3U8: Boolean = true,
    val fullText: String,
    var tracks: List<MegaTrack> = arrayListOf(),
    var headers: Map<String, String> = mapOf()
)


enum class AudioType {
    SUB, DUB
}

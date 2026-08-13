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
) : Serializable


data class VideoOption(
    val videoUrl: String,
    val fansub: String,
    val resolution: String,
    val audioType: AudioType,
    val quality: String,
    val isActive: Boolean,
    val mimeTypes: String = "",
    val fullText: String,
    var tracks: List<MegaTrack> = arrayListOf(),
    var headers: Map<String, String> = mapOf(),
    var thumbnail: String? = null,
    var useLocalProxy: Boolean = false,
    var localProxy: String? = null,
    var requestTransform: String? = null,
    var useWebViewSniff: Boolean = false,
    var sniff: String? = null,
)


enum class AudioType {
    SUB, DUB
}

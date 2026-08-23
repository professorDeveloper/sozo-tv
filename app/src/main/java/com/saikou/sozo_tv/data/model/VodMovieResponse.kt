package com.saikou.sozo_tv.data.model

import androidx.media3.common.MimeTypes
import java.io.Serializable

data class VodMovieResponse(
    val authInfo: String,
    var header: Map<String, String>,
    @Transient
    val subtitleList: List<SubTitle> = arrayListOf(),
    val urlobj: String,
    val type: String = MimeTypes.APPLICATION_M3U8,
    val thumbnail: String = "",
    val language: String = "",
    val useLocalProxy: Boolean = false,
    val localProxyJson: String? = null,
    val requestTransformJson: String? = null,
) : Serializable


data class SubTitle(
    val file: String,
    val label: String,
    val flag: String = "",
    /** Headers for the subtitle request itself; empty for sources that need none. */
    val headers: Map<String, String> = emptyMap(),
)
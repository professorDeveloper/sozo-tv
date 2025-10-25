package com.saikou.sozo_tv.data.model

import com.google.gson.annotations.SerializedName

data class EpiDsode(
    @SerializedName("still_path") val stillPath: String?
)

data class SeasonResponse(
    @SerializedName("episodes") val episodes: List<EpiDsode>
)



data class SubtitleItem(
    @SerializedName("url") val url: String,
    @SerializedName("media") val name: String,
    @SerializedName("display") val lang: String,
    @SerializedName("format") val format: String,
    @SerializedName("flagUrl") val flagUrl: String
)
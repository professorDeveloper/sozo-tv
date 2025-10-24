package com.saikou.sozo_tv.data.model

import com.google.gson.annotations.SerializedName

data class EpiDsode(
    @SerializedName("still_path") val stillPath: String?
)

data class SeasonResponse(
    @SerializedName("episodes") val episodes: List<EpiDsode>
)
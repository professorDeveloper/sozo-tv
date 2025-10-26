package com.saikou.sozo_tv.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

data class EpiDsode(
    @SerializedName("still_path") val stillPath: String?
)

data class SeasonResponse(
    @SerializedName("episodes") val episodes: List<EpiDsode>
)


@kotlinx.parcelize.Parcelize
data class SubtitleItem(
    @SerializedName("url") val url: String,
    @SerializedName("media") val name: String,
    @SerializedName("display") val lang: String,
    @SerializedName("format") val format: String,
    @SerializedName("flagUrl") val flagUrl: String
) : Parcelable
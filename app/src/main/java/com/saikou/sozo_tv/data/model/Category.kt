package com.saikou.sozo_tv.data.model

import com.google.gson.annotations.SerializedName

data class Category(
    val key: String, val name: String
)

data class Country(
    val code: String, val name: String
)

data class Channel(
    @SerializedName("nanoid") val nanoid: String,
    @SerializedName("name") val name: String,
    @SerializedName("iptv_urls") val iptvUrls: List<String>,
    @SerializedName("youtube_urls") val youtubeUrls: List<String>,
    @SerializedName("language") val language: String,
    @SerializedName("country") val country: String,
    @SerializedName("isGeoBlocked") val isGeoBlocked: Boolean
)

data class GitHubFile(
    @SerializedName("name") val name: String, @SerializedName("type") val type: String
)

enum class BookmarkType {
    MEDIA, CHARACTER, TV_CHANNEL
}
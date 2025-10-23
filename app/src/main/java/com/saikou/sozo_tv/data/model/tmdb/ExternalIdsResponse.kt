package com.saikou.sozo_tv.data.model.tmdb

data class ExternalIdsResponse(
    val id: Int,
    val imdb_id: String?,
    val wikidata_id: String?,
    val facebook_id: String?,
    val instagram_id: String?,
    val twitter_id: String?
)

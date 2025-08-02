package com.saikou.sozo_tv.domain.model

data class SearchModel(
    val id: Int?,
    val title: String?,
    val image: String?,
    val studios: List   <String?>?,
    val genres: List    <String?>?,
    val averageScore: Int?,
)
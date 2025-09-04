package com.saikou.sozo_tv.data.model
// Har bir SubSource - individual source
data class SubSource(
    val sourceId: String = "",
    val title: String = "",
    val country: String = ""
)

data class Source(
    val country: String = "",
    val list: Map<String, SubSource> = emptyMap() // <<< MAP
)

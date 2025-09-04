package com.saikou.sozo_tv.data.model
// Har bir SubSource - individual source
data class SubSource(
    val sourceId: String = "", // Firebase uchun default bo'sh qiymat kerak
    val title: String = "",
    val country: String = ""
)

// Source - davlat yoki kategoriya uchun
data class Source(
    val country: String = "",
    val list: ArrayList<SubSource> = arrayListOf()
)

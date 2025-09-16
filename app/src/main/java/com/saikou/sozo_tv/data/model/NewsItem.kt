package com.saikou.sozo_tv.data.model

data class NewsItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val publishedAt: Long = 0L,
    val source: String = "",
    val url: String = "",
    val featured: Boolean = false
)

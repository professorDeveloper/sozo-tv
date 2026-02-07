package com.saikou.sozo_tv.data.model


data class NewsItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0L,
    val action: String = "",
    val isRead: Boolean = false,
    val priority: NewsPriority = NewsPriority.NORMAL
)

enum class NewsPriority {
     NORMAL, HIGH, URGENT
}

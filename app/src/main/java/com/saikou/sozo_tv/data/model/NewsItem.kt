package com.saikou.sozo_tv.data.model


data class NewsItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0L ,
    val action: String = "",
    val isRead: Boolean = false, // Added isRead field for unread/read state
    val priority: NewsPriority = NewsPriority.NORMAL // Added priority for different notification types
)

enum class NewsPriority {
    LOW, NORMAL, HIGH, URGENT
}

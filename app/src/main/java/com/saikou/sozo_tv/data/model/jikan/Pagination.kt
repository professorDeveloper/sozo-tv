package com.saikou.sozo_tv.data.model.jikan

data class Pagination(
    val current_page: Int,
    val has_next_page: Boolean,
    val items: com.saikou.sozo_tv.data.model.jikan.Items,
    val last_visible_page: Int
)
package com.saikou.sozo_tv.data.model.anilist

data class Profile(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val bannerImg: String,
    val unreadNotificationCount: Int = 0
)
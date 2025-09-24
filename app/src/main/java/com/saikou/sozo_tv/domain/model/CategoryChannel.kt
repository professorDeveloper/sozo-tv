package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class CategoryChannel(
    val name: String,
    val list: List<CategoryChannelItem>,
    override val viewType: Int = HomeAdapter.VIEW_CHANNEL
) : HomeAdapter.HomeData

data class CategoryChannelItem(
    override var viewType: Int = HomeAdapter.VIEW_CHANNEL_ITEM,
    val content: ChannelResponseItem
) : HomeAdapter.HomeData


data class ChannelResponseItem(
    val title: String,
    val image: String,
    val playLink: String,
    val country: String,
)

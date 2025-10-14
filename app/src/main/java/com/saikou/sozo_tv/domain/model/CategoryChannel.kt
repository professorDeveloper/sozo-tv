package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
data class ChannelResponseItem(
    val title: String = "",
    val image: String = "",
    val playLink: String = "",
    val country: String = ""
)

data class CategoryChannelItem(
    override var viewType: Int = HomeAdapter.VIEW_CHANNEL_ITEM,
    val content: ChannelResponseItem = ChannelResponseItem()
) : HomeAdapter.HomeData

data class CategoryChannel(
    val name: String = "",
    val list: ArrayList<CategoryChannelItem> = arrayListOf(),
    override val viewType: Int = HomeAdapter.VIEW_CHANNEL
) : HomeAdapter.HomeData

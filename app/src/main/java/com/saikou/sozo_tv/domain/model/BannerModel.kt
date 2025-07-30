package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.data.model.jikan.Data
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class BannerModel(
    override val viewType: Int = HomeAdapter.VIEW_BANNER,
    val data: List<BannerItem>
) : HomeAdapter.HomeData

data class BannerItem(
    val isFocusable: Boolean = false,
    val contentItem: Data,
    override val viewType: Int = HomeAdapter.VIEW_BANNER_ITEM
) : HomeAdapter.HomeData



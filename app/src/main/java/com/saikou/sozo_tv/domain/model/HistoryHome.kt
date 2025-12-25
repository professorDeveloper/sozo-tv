package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class HistoryHome(
    val name: String = "Watch History", override val viewType: Int = HomeAdapter.VIEW_HISTORY,
    val list: List<HistoryHomeItem> = emptyList()
) : HomeAdapter.HomeData

data class HistoryHomeItem(
    override var viewType: Int = HomeAdapter.VIEW_HISTORY_ITEM,
    val content: WatchHistoryEntity,
) : HomeAdapter.HomeData
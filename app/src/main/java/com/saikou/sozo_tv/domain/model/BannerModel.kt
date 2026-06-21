package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class BannerModel(
    override val viewType: Int = HomeAdapter.VIEW_BANNER,
    val data: List<BannerItem>
) : HomeAdapter.HomeData

data class BannerItem(
    val isFocusable: Boolean = false,
    val contentItem: BannerHomeData,
    override val viewType: Int = HomeAdapter.VIEW_BANNER_ITEM
) : HomeAdapter.HomeData

/** Home banner card (was previously in the now-removed Jikan model package). */
data class BannerHomeData(
    val image: String,
    val title: String,
    val description: String,
    val mal_id: Int = -1,
    val anilistId: Int = -1,
    val imdb_id: Int = -1,
    val genre_ids: List<Int>? = null,
    val isMovie: Boolean = false,
    val isSeries: Boolean = false
)

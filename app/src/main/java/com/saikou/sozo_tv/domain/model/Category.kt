package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class Category(
    val name: String,
    val list: List<CategoryDetails>,
    override val viewType: Int = HomeAdapter.VIEW_CATEGORY_FILMS
) : HomeAdapter.HomeData

data class CategoryDetails(
    override var viewType: Int = HomeAdapter.VIEW_CATEGORY_FILMS_ITEM,
    val content: HomeModel,
    var isBookmarked: Boolean = false,
) : HomeAdapter.HomeData
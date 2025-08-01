package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class CategoryGenre(
    val name: String,
    val list: List<CategoryGenreItem>,
    override val viewType: Int = HomeAdapter.VIEW_GENRE
) : HomeAdapter.HomeData

data class CategoryGenreItem(
    override var viewType: Int = HomeAdapter.VIEW_GENRE_ITEM,
    val content: GenreModel
) : HomeAdapter.HomeData
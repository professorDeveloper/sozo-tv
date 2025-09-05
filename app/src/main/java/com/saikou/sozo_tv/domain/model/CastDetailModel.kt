package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class CastDetailModel(
    val image: String,
    val gender: String,
    val name: String,
    val role: String,
    val media: List<MainModel>,
    val age: String,
    val favorites: Int,
)

data class CastAdapterModel(

    val image: String,
    val gender: String,
    val name: String,
    val role: String,
    val age: String,
    val media: List<MainModel>,
    val favorites: Int,

    override val viewType: Int = -1
) : HomeAdapter.HomeData

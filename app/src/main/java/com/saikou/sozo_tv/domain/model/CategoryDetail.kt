package com.saikou.sozo_tv.domain.model

import android.os.Parcelable
import com.saikou.sozo_tv.presentation.screens.detail.MovieDetailsAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import kotlinx.parcelize.Parcelize


data class DetailCategory(
    override var viewType: Int = MovieDetailsAdapter.DETAILS_ITEM_THIRD,
    val content: DetailModel,
    var isBookmarked: Boolean = false,
) : HomeAdapter.HomeData


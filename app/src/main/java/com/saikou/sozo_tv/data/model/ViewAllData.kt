package com.saikou.sozo_tv.data.model

import android.os.Parcelable
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter.Companion.VIEW_ALL
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class ViewAllData(
    val rowId: RowId,
    val categoryTitle: String
) : HomeAdapter.HomeData, Parcelable, Serializable {
    override val viewType: Int get() = VIEW_ALL
}

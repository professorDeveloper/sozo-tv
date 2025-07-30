package com.saikou.sozo_tv.presentation.screens.home.vh

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.BannerItemBinding
import com.saikou.sozo_tv.databinding.ContentBannerBinding
import com.saikou.sozo_tv.databinding.ItemCategoryBinding
import com.saikou.sozo_tv.databinding.ItemMovieBinding
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

object ViewHolderFactory {

    /**
     * viewType asosida mos ViewHolder yaratadi.
     */
    fun create(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HomeAdapter.VIEW_BANNER -> {
                val binding = ContentBannerBinding.inflate(inflater, parent, false)
                HomeAdapter.BannerViewHolder(binding)
            }

            HomeAdapter.VIEW_BANNER_ITEM -> {
                val binding = BannerItemBinding.inflate(inflater, parent, false)
                HomeAdapter.BannerItemViewHolder(binding)
            }
//
//            HomeAdapter.VIEW_CHANNEL -> {
//                val binding = ItemCategoryBinding.inflate(inflater, parent, false)
//                HomeAdapter.ChannelViewHolder(binding)
//            }
//
//            HomeAdapter.VIEW_CHANNEL_ITEM -> {
//                val binding = ItemChannelBinding.inflate(inflater, parent, false)
//                HomeAdapter.ChannelItemViewHolder(binding)
//            }
//
            HomeAdapter.VIEW_CATEGORY_FILMS_ITEM -> {
                val binding = ItemMovieBinding.inflate(inflater, parent, false)
                HomeAdapter.CategoryFilmsItemViewHolder(binding)
            }

            HomeAdapter.VIEW_CATEGORY_FILMS -> {
                val binding = ItemCategoryBinding.inflate(inflater, parent, false)
                HomeAdapter.ItemCategoryViewHolder(binding)
            }
//
//            MovieDetailsAdapter.DETAILS_ITEM_HEADER -> {
//                val binding = ItemPlayDetailsHeaderBinding.inflate(inflater, parent, false)
//                MovieDetailsAdapter.ItemPlayDetailsHeaderViewHolder(binding)
//            }
//
//            MovieDetailsAdapter.DETAILS_ITEM_SECTION -> {
//                val binding = ItemPlayDetailsSectionBinding.inflate(inflater, parent, false)
//                MovieDetailsAdapter.ItemPlayDetailsSectionViewHolder(binding)
//            }
//
//            DETAILS_ITEM_THIRD -> {
//                MovieDetailsAdapter.ItemPlayDetailsThirdViewHolder(
//                    ItemPlayRecommendedBinding.inflate(
//                        inflater,
//                        parent,
//                        false
//                    )
//                )
//            }


            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }
}


package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

//
//import android.os.Parcelable
//import com.ipsat.ipsat_tv.domain.model.BannerItem
//import com.ipsat.ipsat_tv.domain.model.BannerModel
//import com.ipsat.ipsat_tv.domain.model.CategoryChannel
//import com.ipsat.ipsat_tv.domain.model.CategoryChannelItem
//import com.ipsat.ipsat_tv.domain.model.ChannelResponse
//import com.ipsat.ipsat_tv.domain.model.Movie
//import com.ipsat.ipsat_tv.presentation.screens.home.HomeAdapter
//import kotlinx.parcelize.Parcelize
//

fun JikanBannerResponse.toDomain(): BannerModel {
    return BannerModel(viewType = HomeAdapter.VIEW_BANNER, data = this.data.map {
        BannerItem(
            contentItem = it,
        )
    })
}

fun List<GenreModel>.toDomain():CategoryGenre {
    return CategoryGenre(
        "Genres",
        this.map {
            CategoryGenreItem(
                content = it
            )
        }
    )
}

////import uz.kinoplus.tv.presentation.screens.home.HomeAdapter.Companion.VIEW_CHANNEL_ITEM
//
//fun List<Movie>.toDomain(): BannerModel {
//    return BannerModel(viewType = HomeAdapter.VIEW_BANNER, data = this.map {
//        BannerItem(
//            contentItem = it,
//        )
//    })
//}
//
//fun ChannelResponse.toDomain(): CategoryChannel {
//    return CategoryChannel(
//        name = "Online TV", list = this.map {
//            CategoryChannelItem(
//                content = it
//            )
//        }, viewType = HomeAdapter.VIEW_CHANNEL
//    )
//}
////
////fun ProfileResponse.toDomain(): DisplayUser {
////    return DisplayUser(
////        name = this.username,
////        email = this.email,
////        phoneNumber = this.phoneNumber,
////    )
////}
////
////fun ContentItem.toDomain(): BannerItem {
////    return BannerItem(
////        thumbnailImageUrl = this.thumbnailImageUrl,
////        contentItem = this,
////    )
////}
////
////@Parcelize
////class ChannelDomain(
////    val name: String,
////    val img: String,
////    val currentShow: String,
////    val time: String,
////    override var viewType: Int = HomeAdapter.VIEW_CHANNEL_ITEM
////) : HomeAdapter.HomeData, Parcelable
////
////fun List<CategoryDetailsResponse>.toDomain(): List<CategoryDetails> {
////    return this.flatMap { response ->
////        response.data.content.map { content ->
////            CategoryDetails(
////                viewType = HomeAdapter.VIEW_CATEGORY_FILMS_ITEM,
////                id = content.id,
////                genre = content.genre,
////                director = content.director ?: Director(id = -1, name = "Unknown"),
////                rating = content.rating,
////                isMobileOnly = content.isMobileOnly,
////                thumbnailImage = content.thumbnailImage,
////                title = content.title,
////                isPremiere = content.isPremiere,
////                description = content.description,
////                trailerUrl = content.trailerUrl ?: "",
////                isFree = content.isFree,
////                isFavorited = content.isFavorited,
////                releaseDate = content.releaseDate,
////                widescreenThumbnailImage = content.widescreenThumbnailImage,
////                slug = content.slug,
////                isMovie = content.isMovie
////            )
////        }
////    }
////}
////
////fun List<Channel>.toChannelDomain(): List<ChannelDomain> {
////    return this.map { channel ->
////        ChannelDomain(
////            name = channel.name,
////            img = channel.img,
////            currentShow = channel.currentShow,
////            time = channel.time,
////            viewType = VIEW_CHANNEL_ITEM
////        )
////    }
////}
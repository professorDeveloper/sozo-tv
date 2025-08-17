package com.saikou.sozo_tv.utils

import com.animestudios.animeapp.GetAnimeByGenreQuery
import com.animestudios.animeapp.GetAnimeByIdQuery
import com.animestudios.animeapp.GetAnimeByOnlGenreQuery
import com.animestudios.animeapp.GetRelationsByIdQuery
import com.animestudios.animeapp.SearchAnimeQuery
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchModel
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

fun GetRelationsByIdQuery.Media.toDomain(): MainModel {
    return MainModel(
        this.id,
        title = this.title?.userPreferred ?: "",
        idMal = -1,
        this.coverImage?.large ?: LocalData.anime404,
        this.genres,
        this.studios?.nodes?.map {
            it?.name ?: ""
        },
        this.averageScore ?: -1,
        this.meanScore ?: -1
    )
}

fun GetAnimeByIdQuery.Media.toDomain(): DetailModel {
    val extraLinksD = this.externalLinks?.map {
        it?.url ?: ""
    }
    val studiosD = this.studios?.nodes?.map {
        it?.name ?: ""
    }
    val detail = DetailModel(
        this.id,
        this.idMal ?: 0,
        CoverImage(this.coverImage?.large ?: LocalData.anime404),
        this.bannerImage ?: LocalData.anime404,
        description = this.description ?: "",
        title = this.title?.english ?: "",
        this.episodes ?: 0,
        this.genres,
        extraLinksD,
        studios = studiosD,
        this.seasonYear ?: 0,
        this.source,
    )
    return detail
}

fun JikanBannerResponse.toDomain(): BannerModel {
    val itRemovedFirst = this.data
    val list = arrayListOf<BannerItem>()
    itRemovedFirst.forEachIndexed { index, data ->
        if (index != 0) {
           list.add(
               BannerItem(
                   contentItem = data,
               )
           )
        }
    }
    return BannerModel(viewType = HomeAdapter.VIEW_BANNER, data = list)
}

fun List<GenreModel>.toDomain(): CategoryGenre {
    return CategoryGenre("Genres", this.map {
        CategoryGenreItem(
            content = it
        )
    })
}

fun SearchAnimeQuery.Medium.toDomain(): SearchModel {
    return SearchModel(
        this.id,
        this.title?.userPreferred,
        this.coverImage?.large,
        studios = this.studios!!.nodes!!.map { it?.name },
        genres = this.genres!!.map { it },
        this.averageScore
    )
}

fun GetAnimeByGenreQuery.Medium.toDomain(): MainModel {
    val it = this
    return MainModel(
        id = it.id,
        title = it.title?.english ?: "",
        image = it.coverImage?.large ?: LocalData.anime404,
        studios = it.studios!!.nodes!!.map { it?.name ?: "" },
        genres = it.genres!!.map { it },
        averageScore = it.averageScore ?: -1,
        idMal = it.idMal ?: -1,
    )
}

fun GetAnimeByOnlGenreQuery.Medium.toDomain(): MainModel {
    val it = this
    return MainModel(
        id = it.id,
        title = it.title?.english ?: "",
        image = it.coverImage?.large ?: LocalData.anime404,
        studios = it.studios!!.nodes!!.map { it?.name ?: "" },
        genres = it.genres!!.map { it },
        averageScore = it.averageScore ?: -1,
        idMal = it.idMal ?: -1,
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
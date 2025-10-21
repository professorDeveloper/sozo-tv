package com.saikou.sozo_tv.utils

import com.animestudios.animeapp.GetAnimeByGenreQuery
import com.animestudios.animeapp.GetAnimeByIdQuery
import com.animestudios.animeapp.GetAnimeByOnlGenreQuery
import com.animestudios.animeapp.GetCharacterDetailQuery
import com.animestudios.animeapp.GetCharactersAnimeByIdQuery
import com.animestudios.animeapp.GetRelationsByIdQuery
import com.animestudios.animeapp.SearchAnimeQuery
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.jikan.BannerHomeData
import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.data.model.tmdb.TmdbListItem
import com.saikou.sozo_tv.domain.model.AiringSchedule
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

fun TmdbListItem.toDomain(): MainModel {
    return MainModel(
        this.id ?: 0,
        this.titleFormat ?: "",
        -1,
        this.imageUrl ?: "",
        null,
        null,
        -1, -1
    )
}

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

fun DetailModel.toDomain(): AnimeBookmark {
    return AnimeBookmark(
        this.id,
        this.title,
        this.malId,
        this.coverImage.large,
    )

}

fun CastAdapterModel.toDomain(id: Int): CharacterEntity {
    return CharacterEntity(
        id = id,
        name = this.name,
        image = this.image,
        role = this.role,
        age = this.age
    )
}

fun AnimeBookmark.toDomain(): MainModel {
    return MainModel(
        this.id,
        title = this.title,
        idMal = this.idMal,
        this.image,
        null,
        null,
        -1,
    )
}

fun GetAnimeByIdQuery.Media.toDomain(): DetailModel {
    val extraLinksD = this.externalLinks?.map {
        it?.url ?: ""
    }
    val studiosD = this.studios?.nodes?.map {
        it?.name ?: ""
    }
    var airingSchedule = AiringSchedule()
    if (this.airingSchedule?.nodes?.isNotEmpty() == true) {
        val upcomingEpisodes = this.airingSchedule.nodes.filter { it?.timeUntilAiring!! > 0 }
        if (upcomingEpisodes.isNotEmpty()) {
            airingSchedule = AiringSchedule(
                (upcomingEpisodes[0]?.airingAt ?: 0).toLong(),
                upcomingEpisodes[0]?.episode ?: 0,
                upcomingEpisodes[0]?.timeUntilAiring ?: 0
            )
        }
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
        airingSchedule = airingSchedule,
        isAdult = this.isAdult ?: false
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
                    contentItem = BannerHomeData(
                        data.images.jpg.large_image_url ?: "",
                        data.title,
                        data.synopsis,
                        mal_id = data.mal_id
                    ),
                )
            )
        }
    }
    return BannerModel(viewType = HomeAdapter.VIEW_BANNER, data = list)
}

fun GetCharacterDetailQuery.Node.toDomain(): MainModel {
    return MainModel(
        this.id,
        this.title?.userPreferred ?: "",
        -1,
        image = this.coverImage?.large ?: LocalData.anime404,
        this.genres,
        this.studios?.nodes?.map {
            it?.name ?: ""
        },
        this.averageScore ?: -1,
        this.meanScore ?: -1
    )

}

fun GetCharacterDetailQuery.Character.toDomain(): CastDetailModel {
    val list = ArrayList<MainModel>()
    this.media?.nodes?.map {
        list.add(it?.toDomain()!!)

    }
    return CastDetailModel(
        image = this.image?.medium ?: LocalData.anime404,
        name = this.name?.userPreferred ?: "",
        role = this.name?.middle ?: "",
        gender = this.gender ?: "",
        media = list,
        age = this.age ?: "",
        favorites = this.favourites ?: -1
    )
}

fun GetCharactersAnimeByIdQuery.Node.toDomain(): Cast {
    return Cast(
        this.id,
        (this.image?.medium ?: LocalData.anime404).toString(),
        (this.name?.userPreferred ?: "").toString(),
        this.name?.middle ?: "",
        this.age ?: ""
    )
}


fun List<GenreModel>.toDomain(): CategoryGenre {
    return CategoryGenre("Genres", this.map {
        CategoryGenreItem(
            content = it
        )
    })
}

fun TmdbListItem.toSearchDomain(): SearchModel {
    return SearchModel(
        this.id,
        this.titleFormat,
        this.imageUrl,
        null,
        null,
        -1
    )
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

package com.saikou.sozo_tv.data.model

import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.utils.LocalData

fun HomeModel.toDomain(): MainModel = MainModel(
    id = this.id,
    idMal = this.idMal,
    title = this.title.english,
    image = this.coverImage.large,
    genres = arrayListOf(),
    studios = arrayListOf(),
    averageScore = 0,
    meanScore = -1,
    isSeries = this.isSeries,
    isAnime = this.isAnime
)

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
        id = id, name = this.name, image = this.image, role = this.role, age = this.age,
        isAnime = LocalData.isAnimeEnabled
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
        isSeries = this.isSeries,
        isAnime = this.isAnime,
    )
}

fun SubtitleItem.toDomain(): SubTitle {
    return SubTitle(
        this.url,
        this.lang,
        this.flagUrl
    )
}

package com.saikou.sozo_tv.data.extensions

import com.animestudios.animeapp.type.MediaFormat
import com.animestudios.animeapp.type.MediaSource
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.domain.model.BannerHomeData
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.data.model.RowId
import com.saikou.sozo_tv.utils.LocalData

/**
 * Converts the engine's [ExtCard]/[ExtDetail]/[ExtMedia] into the legacy UI models
 * the screens/adapters already render. Every card is run through
 * [ExtensionContentRegistry] so its `(provider,url)` collapses to a stable Int id.
 */

private fun ExtCard.isMovie(): Boolean = type?.equals("Movie", true) == true

fun ExtCard.toHomeModel(): HomeModel {
    val id = ExtensionContentRegistry.encode(this)
    return HomeModel(
        id = id,
        idMal = -1,
        coverImage = CoverImage(thumbnail ?: LocalData.anime404),
        format = if (isMovie()) MediaFormat.MOVIE else MediaFormat.TV,
        source = MediaSource.ORIGINAL,
        title = Title(title),
        isSeries = !isMovie(),
        isAnime = isAnime,
    )
}

fun ExtCard.toMainModel(): MainModel {
    val id = ExtensionContentRegistry.encode(this)
    return MainModel(
        id = id,
        title = title,
        idMal = -1,
        image = thumbnail ?: LocalData.anime404,
        genres = null,
        studios = null,
        averageScore = 0,
        isSeries = !isMovie(),
        isAnime = isAnime,
    )
}

fun ExtCard.toSearchModel(): SearchModel {
    val id = ExtensionContentRegistry.encode(this)
    return SearchModel(
        id = id,
        title = title,
        image = thumbnail ?: LocalData.anime404,
        studios = null,
        genres = null,
        // SearchScreen.openOnSelectedProvider reads averageScore == 1 as the "is movie" flag.
        averageScore = if (isMovie()) 1 else 0,
    )
}

fun ExtCard.toBannerData(description: String = ""): BannerHomeData {
    val id = ExtensionContentRegistry.encode(this)
    return BannerHomeData(
        image = thumbnail ?: LocalData.anime404,
        title = title,
        description = description,
        mal_id = id,
        anilistId = id,
        imdb_id = id,
        isMovie = isMovie(),
        isSeries = !isMovie(),
    )
}

fun ExtSection.toCategory(): Category = Category(
    name = label,
    list = items.map { CategoryDetails(content = it.toHomeModel()) },
    rowId = RowId.POPULAR,
    slug = slug,
)

fun List<ExtGenre>.toCategoryGenre(name: String = "Genres"): CategoryGenre = CategoryGenre(
    name = name,
    list = map { CategoryGenreItem(content = GenreModel(title = it.name, image = it.image ?: "")) },
)

fun ExtCast.toCast(index: Int): Cast = Cast(
    id = index,
    image = image ?: LocalData.anime404,
    name = name,
    role = character ?: "",
    age = "",
)

fun ExtDetail.toDetailModel(id: Int): DetailModel = DetailModel(
    id = id,
    malId = -1,
    coverImage = CoverImage(thumbnail ?: LocalData.anime404),
    bannerImage = banner ?: thumbnail ?: LocalData.anime404,
    description = description,
    title = title,
    episodes = episodes.size.takeIf { it > 0 },
    genres = genres.ifEmpty { null },
    extraLinks = null,
    studios = director?.let { listOf(it) },
    seasonYear = year,
    mediaSource = MediaSource.ORIGINAL,
    isAdult = false,
    isSeries = isSerial,
)

/** Pick the default/best [ExtVideoSource] and wrap it for the player. */
fun ExtMedia.toVod(thumbnail: String = "", language: String = ""): VodMovieResponse? {
    val chosen = sources.firstOrNull { it.isDefault } ?: sources.firstOrNull()
    val url = chosen?.videoUrl ?: videoUrl ?: return null
    val mime = when ((chosen?.type ?: type)) {
        "hls" -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
        "dash" -> androidx.media3.common.MimeTypes.APPLICATION_MPD
        else -> androidx.media3.common.MimeTypes.VIDEO_MP4
    }
    return VodMovieResponse(
        authInfo = "",
        header = chosen?.headers ?: headers,
        subtitleList = subtitles.map { SubTitle(file = it.file, label = it.label) },
        urlobj = url,
        type = mime,
        thumbnail = thumbnail,
        language = language,
    )
}

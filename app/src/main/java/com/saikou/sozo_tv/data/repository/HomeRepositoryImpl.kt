package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.animestudios.animeapp.ConvertMalToIdQuery
import com.animestudios.animeapp.GetBannerQuery
import com.animestudios.animeapp.GetPopularQuery
import com.animestudios.animeapp.GetRecommendationsQuery
import com.animestudios.animeapp.GetTrendingQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.RowId
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.data.model.jikan.BannerHomeData
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.domain.repository.HomeRepository
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.FILE_NAME_GENRES
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData
import kotlin.random.Random

class HomeRepositoryImpl(
    private val apolloClient: ApolloClient
) : HomeRepository {
    override suspend fun getTopBannerAnime(): Result<BannerModel> {
        return try {
            val response = apolloClient.safeExecute(GetBannerQuery())
            val data = response?.Page?.media ?: arrayListOf()
            val newData = BannerModel(data =
            data.map {
                val media = it
                BannerItem(
                    contentItem = BannerHomeData(
                        media?.bannerImage ?: LocalData.anime404,
                        media?.title?.userPreferred ?: "",
                        media?.description ?: "",
                        mal_id = media?.idMal ?: -1,
                        anilistId = media?.id ?: -1,
                    )
                )
            })
            Result.success(newData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // AniList
    override suspend fun loadCategories(): Result<List<Category>> = safeExecute {
        val categories = mutableListOf<Category>()

        val recommendationData = apolloClient.safeExecute(
            GetRecommendationsQuery(page = Optional.present(Random.nextInt(1, 4)))
        )
        recommendationData?.Page?.recommendations
            ?.filter { it?.media?.title?.english != null }
            ?.let { filteredList ->
                categories.add(
                    Category(
                        name = "Recommended",
                        rowId = RowId.RECOMMENDED,
                        list = filteredList.map {
                            CategoryDetails(
                                content = HomeModel(
                                    id = it!!.media!!.id,
                                    idMal = it.media!!.idMal!!,
                                    coverImage = CoverImage(it.media.coverImage!!.large ?: LocalData.anime404),
                                    format = it.media.format!!,
                                    source = it.media.source!!,
                                    title = Title(it.media.title?.english ?: "")
                                )
                            )
                        }
                    )
                )
            }

        val trendData = apolloClient.safeExecute(
            GetTrendingQuery(page = Optional.present(Random.nextInt(1, 4)))
        )
        trendData?.Page?.mediaTrends
            ?.filter { it?.media?.title?.english != null || it?.media?.title?.userPreferred != null && it.media.source != null }
            ?.let { filteredList ->
                categories.add(
                    Category(
                        name = "Trending",
                        rowId = RowId.TRENDING,
                        list = filteredList.map {
                            CategoryDetails(
                                content = HomeModel(
                                    id = it!!.media!!.id,
                                    idMal = it.media!!.idMal!!,
                                    coverImage = CoverImage(it.media.coverImage!!.large ?: LocalData.anime404),
                                    format = it.media.format!!,
                                    source = it.media.source!!,
                                    title = Title(it.media.title?.english ?: it.media.title?.userPreferred ?: "")
                                )
                            )
                        }
                    )
                )
            }

        val popularData = apolloClient.safeExecute(
            GetPopularQuery(page = Optional.present(Random.nextInt(1, 8)))
        )
        popularData?.Page?.media
            ?.filter { it?.title?.english != null || it?.title?.userPreferred != null && it.source != null }
            ?.let { filteredList ->
                categories.add(
                    Category(
                        name = "Popular",
                        rowId = RowId.POPULAR,
                        list = filteredList.map {
                            CategoryDetails(
                                content = HomeModel(
                                    id = it!!.id,
                                    idMal = it.idMal!!,
                                    coverImage = CoverImage(it.coverImage!!.large ?: LocalData.anime404),
                                    format = it.format!!,
                                    source = it.source!!,
                                    title = Title(it.title?.english ?: it.title?.userPreferred ?: "")
                                )
                            )
                        }
                    )
                )
            }

        categories
    }
    override suspend fun convertMalId(id: Int): Result<Int> {
        try {
            val data = apolloClient.safeExecute(
                ConvertMalToIdQuery(Optional.present(id))
            )
            val media = data?.Media
            return Result.success(media?.id ?: -1)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun loadGenres(): Result<List<GenreModel>> {
        try {
            val isExist = readData("isAdded", context = MyApp.context) ?: false
            val localGenres = ArrayList<GenreModel>()
            LocalData.genres.forEachIndexed { index, s ->
                localGenres.add(
                    GenreModel(
                        s,
                        readData<String>(FILE_NAME_GENRES + "$index$index")
                            ?: "https://via.placeholder.com/150",
                    )
                )
            }
            if (isExist) {
                Log.d("GGG", "loadGenres:${localGenres.size} ")
                return Result.success(localGenres)
            } else {
                val recommendationData = apolloClient.safeExecute(
                    GetRecommendationsQuery(
                        page = Optional.present(Random.nextInt(1, 4))
                    )
                )

                val genres = ArrayList<GenreModel>()
                recommendationData?.Page?.recommendations?.let { recommendationMediaList ->
                    LocalData.genres.forEachIndexed { index, s ->
                        if (recommendationMediaList.size > index) {
                            genres.add(
                                GenreModel(
                                    s,
                                    recommendationMediaList[index]?.media?.coverImage?.large
                                        ?: "https://via.placeholder.com/150",
                                )
                            )
                        } else {
                            genres.add(
                                GenreModel(
                                    s,
                                    LocalData.anime404,
                                )
                            )
                        }
                    }
                }
                genres.forEachIndexed { index, genre ->
                    saveData(FILE_NAME_GENRES + "$index", genre.title)
                    saveData(FILE_NAME_GENRES + "$index$index", genre.image)
                }
                saveData("isAdded", true)
                return Result.success(genres)
            }

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

}
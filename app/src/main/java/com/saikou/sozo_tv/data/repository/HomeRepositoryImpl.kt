package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.animestudios.animeapp.GetPopularQuery
import com.animestudios.animeapp.GetRecentlyAddedQuery
import com.animestudios.animeapp.GetRecommendationsQuery
import com.animestudios.animeapp.GetTrendingQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.data.remote.JikanApiService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.repository.HomeRepository
import kotlin.random.Random

class HomeRepositoryImpl(
    private val jikanApiService: JikanApiService, private val apolloClient: ApolloClient
) : HomeRepository {
    override suspend fun getTopBannerAnime(): Result<JikanBannerResponse> {
        return safeApiCall {
            jikanApiService.getTopAnime()
        }.map {
            it
        }
    }

    override suspend fun loadCategories(): Result<List<Category>> {
        return try {
            val categories = mutableListOf<Category>()
            val recommendationApolloResponse =
                apolloClient.query(
                    GetRecommendationsQuery(
                        page = Optional.present(
                            Random.nextInt(
                                1,
                                4
                            )
                        )
                    )
                )
                    .execute()


            val recommendationMediaList =
                recommendationApolloResponse.data!!.Page?.recommendations!!
            recommendationMediaList.let {
                val filteredList = it.filter {
                    it?.media?.title?.english != null
                }

                categories.add(
                    Category(name = "Recommended", list = filteredList.map {
                        CategoryDetails(
                            content = HomeModel(
                                id = it!!.media!!.id,
                                idMal = it.media!!.idMal!!,
                                coverImage = CoverImage(it.media.coverImage!!.large ?: ""),
                                format = it.media.format!!,
                                source = it.media.source!!,
                                title = Title(
                                    it.media.title?.english ?: ""
                                )
                            )
                        )
                    })
                )
            }
            val trendApolloResponse = apolloClient.query(
                GetTrendingQuery(
                    page = Optional.present(
                        Random.nextInt(
                            1,
                            4
                        )
                    )
                )
            ).execute()
            val trendMediaList = trendApolloResponse.data!!.Page?.mediaTrends!!

            trendMediaList.let {
                val filteredList = it.filter {
                    it?.media?.title?.english != null || it?.media?.title?.userPreferred != null && it.media.source != null
                }

                categories.add(
                    Category(name = "Trending", list = filteredList.map {
                        CategoryDetails(
                            content = HomeModel(
                                id = it!!.media!!.id,
                                idMal = it.media!!.idMal!!,
                                coverImage = CoverImage(it.media.coverImage!!.large ?: ""),
                                format = it.media.format!!,
                                source = it.media.source!!,
                                title = Title(
                                    it.media.title?.english ?: it.media.title?.userPreferred ?: ""
                                )
                            )
                        )
                    })
                )
            }


            val getPopularApolloResponse = apolloClient.query(
                GetPopularQuery(
                    page = Optional.present(
                        Random.nextInt(
                            1,
                            8
                        )
                    )
                )
            ).execute()

            val popularMediaList = getPopularApolloResponse.data!!.Page?.media!!

            popularMediaList.let {
                val filteredList = it.filter {
                    it?.title?.english != null || it?.title?.userPreferred != null && it.source != null
                }
                categories.add(
                    Category(name = "Popular", list = filteredList.map {
                        CategoryDetails(
                            content = HomeModel(
                                id = it!!.id,
                                idMal = it.idMal!!,
                                coverImage = CoverImage(it.coverImage!!.large ?: ""),
                                format = it.format!!,
                                source = it.source!!,
                                title = Title(
                                    it.title?.english ?: it.title?.userPreferred ?: ""
                                )
                            )
                        )
                    })
                )
            }


            val recentlyAddedApolloResponse = apolloClient.query(
                GetRecentlyAddedQuery(
                    page = Optional.present(
                        Random.nextInt(
                            1,
                            8
                        )
                    ),
                )
            ).execute()

            val recentlyAddedMediaList = recentlyAddedApolloResponse.data!!.Page?.airingSchedules!!

            recentlyAddedMediaList.let {
                val filteredList = it.filter {
                    it?.media?.title?.english != null || it?.media?.title?.userPreferred != null && it.media.source != null
                }
                categories.add(
                    Category(name = "Recently Added", list = filteredList.map {
                        CategoryDetails(
                            content = HomeModel(
                                id = it!!.media!!.id,
                                idMal = it.media!!.idMal!!,
                                coverImage = CoverImage(it.media.coverImage!!.large ?: ""),
                                format = it.media.format!!,
                                source = it.media.source!!,
                                title = Title(
                                    it.media.title?.english ?: it.media.title?.userPreferred ?: ""
                                )
                            )
                        )
                    })
                )
            }



            Result.success(categories)
        } catch (e: Exception) {
            Log.d("GGGG", "loadCategories:${e} ")
            Result.failure(e)
        }
    }

}
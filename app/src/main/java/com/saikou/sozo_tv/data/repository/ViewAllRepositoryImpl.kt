package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetPopularQuery
import com.animestudios.animeapp.GetRecommendationsQuery
import com.animestudios.animeapp.GetTrendingQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.model.RowId
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.data.model.mapToPaginated
import com.saikou.sozo_tv.data.model.toDomain
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.data.remote.safeApolloCall
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.model.PaginatedResult
import com.saikou.sozo_tv.domain.repository.ViewAllRepository
import com.saikou.sozo_tv.utils.LocalData

class ViewAllRepositoryImpl(
    private val api: ImdbService,
    private val apollo: ApolloClient
) : ViewAllRepository {

    override suspend fun loadMore(rowId: RowId, page: Int): Result<PaginatedResult> {
        return when (rowId) {

            RowId.RECOMMENDED -> safeApolloCall {
                val data = apollo.safeExecute(
                    GetRecommendationsQuery(page = Optional.present(page))
                )
                val list = data?.Page?.recommendations
                    ?.filter { it?.media?.title?.english != null }
                    ?.map {
                        HomeModel(
                            id = it!!.media!!.id,
                            idMal = it.media!!.idMal!!,
                            coverImage = CoverImage(
                                it.media.coverImage!!.large ?: LocalData.anime404
                            ),
                            format = it.media.format!!,
                            source = it.media.source!!,
                            title = Title(it.media.title?.english ?: ""),
                            isAnime = true
                        ).toDomain()
                    } ?: emptyList()

                PaginatedResult(
                    page = page,
                    totalPages = data?.Page?.pageInfo?.lastPage ?: 1,
                    totalResults = data?.Page?.pageInfo?.total ?: 0,
                    list = list
                )
            }

            RowId.TRENDING -> safeApolloCall {
                val data = apollo.safeExecute(
                    GetTrendingQuery(page = Optional.present(page))
                )
                val list = data?.Page?.mediaTrends
                    ?.filter { it?.media?.title?.english != null || it?.media?.title?.userPreferred != null }
                    ?.map {
                        HomeModel(
                            id = it!!.media!!.id,
                            idMal = it.media!!.idMal!!,
                            coverImage = CoverImage(
                                it.media.coverImage!!.large ?: LocalData.anime404
                            ),
                            format = it.media.format!!,
                            source = it.media.source!!,
                            title = Title(
                                it.media.title?.english ?: it.media.title?.userPreferred ?: ""
                            ),
                            isAnime = true
                        ).toDomain()
                    } ?: emptyList()

                PaginatedResult(
                    page = page,
                    totalPages = data?.Page?.pageInfo?.lastPage ?: 1,
                    totalResults = data?.Page?.pageInfo?.total ?: 0,
                    list = list
                )
            }

            RowId.POPULAR -> safeApolloCall {
                val data = apollo.safeExecute(
                    GetPopularQuery(page = Optional.present(page))
                )
                val list = data?.Page?.media
                    ?.filter { it?.title?.english != null || it?.title?.userPreferred != null }
                    ?.map {
                        HomeModel(
                            id = it!!.id,
                            idMal = it.idMal!!,
                            coverImage = CoverImage(
                                it.coverImage!!.large ?: LocalData.anime404
                            ),
                            format = it.format!!,
                            source = it.source!!,
                            title = Title(it.title?.english ?: it.title?.userPreferred ?: ""),
                            isAnime = true
                        ).toDomain()
                    } ?: emptyList()

                PaginatedResult(
                    page = page,
                    totalPages = data?.Page?.pageInfo?.lastPage ?: 1,
                    totalResults = data?.Page?.pageInfo?.total ?: 0,
                    list = list
                )
            }

            RowId.TMDB_TRENDING_ALL -> safeApiCall {
                api.getPopularSeries(page = page)
            }.mapToPaginated { item ->
                item.toDomain()
            }

            RowId.TMDB_TOP_RATED -> safeApiCall {
                api.getTopRatedMovies(page = page)
            }.mapToPaginated { item ->
                item.toDomain()
            }

            RowId.TMDB_TRENDING_SERIES -> safeApiCall {
                api.getTrendingSeries()
            }.mapToPaginated { item ->
                item.toDomain()
            }

            RowId.TMDB_RECOMMEND_SERIES -> safeApiCall {
                api.getPopularSeries(page = page)
            }.mapToPaginated { item ->
                item.toDomain()
            }
        }
    }
}
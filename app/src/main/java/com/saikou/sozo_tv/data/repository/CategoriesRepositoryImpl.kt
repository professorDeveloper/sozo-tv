package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetAnimeByGenreQuery
import com.animestudios.animeapp.GetAnimeByOnlGenreQuery
import com.animestudios.animeapp.type.MediaSort
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.toDomain
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.utils.LocalData

class CategoriesRepositoryImpl(
    private val apolloClient: ApolloClient,
    private val api: ImdbService
) : CategoriesRepository {

    override suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults> {
        return try {
            if (
                searchResults.tag.isNotEmpty() ||
                searchResults.year != -1 ||
                searchResults.avgScore != -1
            ) {
                // Advanced query
                val response = apolloClient.query(
                    GetAnimeByGenreQuery(
                        genre = Optional.presentIfNotNull(searchResults.genre),
                        page = Optional.present(searchResults.currentPage),
                        mediaSort = Optional.presentIfNotNull(
                            searchResults.tag.takeIf { it.isNotEmpty() }?.let {
                                try {
                                    MediaSort.valueOf(it)
                                } catch (e: IllegalArgumentException) {
                                    null
                                }
                            }
                        ),
                        year = Optional.presentIfNotNull(searchResults.year.takeIf { it != -1 }),
                        avgScore = Optional.presentIfNotNull(searchResults.avgScore.takeIf { it != -1 })
                    )
                ).execute()

                val page = response.data?.Page
                val mediaList = page?.media ?: emptyList()
                searchResults.hasNextPage = page?.pageInfo?.hasNextPage ?: false
                searchResults.results =
                    mediaList.filter { it?.title?.english != null }.map { it!!.toDomain() }

                Result.success(searchResults)
            } else {
                // Simple query
                val response = apolloClient.query(
                    GetAnimeByOnlGenreQuery(
                        genre = Optional.presentIfNotNull(searchResults.genre),
                        page = Optional.present(searchResults.currentPage),
                    )
                ).execute()

                val page = response.data?.Page
                val mediaList = page?.media ?: emptyList()
                searchResults.hasNextPage = page?.pageInfo?.hasNextPage ?: false
                searchResults.results =
                    mediaList.filter { it?.title?.english != null }.map { it!!.toDomain() }

                Result.success(searchResults)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadMovieByGenre(searchResults: SearchResults): Result<SearchResults> {
        return try {
            val genre = LocalData.genreTmdb.find { it.title == searchResults.genre }
            val preference = PreferenceManager()
            if (genre!!.title != "Adult") {
                val response = api.getMoviesByGenre(
                    genreId = genre.id,
                    page = searchResults.currentPage,
                    isAdult = preference.isNsfwEnabled()
                ).body()!!
                val mediaList = response.results
                searchResults.hasNextPage = (response.page ?: 0) != (response.total_pages ?: 0)
                searchResults.results = mediaList.map { it.toDomain() }
                Result.success(searchResults)
            } else {
                val response = api.getMoviesByGenre(
                    genreId = 10749,
                    page = searchResults.currentPage,
                    isAdult = preference.isNsfwEnabled()
                ).body()!!

                val mediaList = response.results
                searchResults.hasNextPage = (response.page ?: 0) != (response.total_pages ?: 0)
                searchResults.results = mediaList.map { it.toDomain() }
                Result.success(searchResults)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    }
}

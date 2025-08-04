package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetAnimeByGenreQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.utils.toDomain

class CategoriesRepositoryImpl(private val apolloClient: ApolloClient) : CategoriesRepository {
    override suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults> {
        try {
            val animeGenreResponse = apolloClient.query(
                GetAnimeByGenreQuery(
                    genre = Optional.present(searchResults.genre ?: "Action"),
                    page = Optional.present(searchResults.currentPage)
                )
            ).execute()
            val mediaList = animeGenreResponse.data?.Page?.media ?: emptyList()

            searchResults.hasNextPage =
                animeGenreResponse.data?.Page?.pageInfo?.hasNextPage ?: false
            searchResults.results = mediaList.map {
                it!!.toDomain()
            }
            return Result.success(searchResults)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
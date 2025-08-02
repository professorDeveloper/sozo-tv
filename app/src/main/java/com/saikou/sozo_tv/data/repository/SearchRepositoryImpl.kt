package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.SearchAnimeQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.utils.toDomain

class SearchRepositoryImpl(private val apolloClient: ApolloClient) : SearchRepository {
    override suspend fun searchAnime(query: String): Result<List<SearchModel>> {
        try {
            val searchResponse =
                apolloClient.query(SearchAnimeQuery(search = Optional.present(query))).execute()
            searchResponse.let {
                val searchList = arrayListOf<SearchModel>()
                it.data?.Page?.media?.forEach {
                    searchList.add(
                        it?.toDomain()!!
                    )
                }
                return Result.success(searchList)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
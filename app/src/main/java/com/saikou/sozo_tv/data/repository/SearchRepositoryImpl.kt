package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.SearchAnimeQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.utils.toDomain
import com.saikou.sozo_tv.utils.toSearchDomain

class SearchRepositoryImpl(private val apolloClient: ApolloClient, private val api: ImdbService) :
    SearchRepository {
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

    override suspend fun searchMovie(query: String): Result<List<SearchModel>> {
        try {
            val preference = PreferenceManager()
            val searchResponse =
                api.searchMoviesByKeyword(query, isAdult = preference.isNsfwEnabled()).body()!!
            val searchList = arrayListOf<SearchModel>()
            searchResponse.results.forEach {
                searchList.add(
                    it.toSearchDomain()
                )
            }
            return Result.success(searchList)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
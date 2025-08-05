package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetAnimeByIdQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.utils.toDomain

class DetailRepositoryImpl(private val client: ApolloClient) : DetailRepository {
    override suspend fun loadAnimeDetail(id: Int): Result<DetailModel> {
        try {
            val result = client.query(
                GetAnimeByIdQuery(
                    Optional.present(id)
                )
            ).execute()

            val animeDetail = result.data?.Media?.toDomain()
            return Result.success(animeDetail!!)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
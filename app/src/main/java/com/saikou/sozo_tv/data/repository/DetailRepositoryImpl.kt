package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetAnimeByIdQuery
import com.animestudios.animeapp.GetRelationsByIdQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.utils.toDomain
import kotlin.random.Random

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

    override suspend fun loadRandomAnime(): Result<List<MainModel>> {
        try {
            val result = client.query(
                GetRelationsByIdQuery(Optional.present(Random.nextInt(7, 21)))
            ).execute()

            val data = result.data?.Page?.mediaTrends ?: emptyList()

            val resultList = data.map {
                it?.media?.toDomain()!!
            }

            return Result.success(resultList)
        } catch (e: Exception) {
            return Result.failure(e)

        }
    }

    override suspend fun loadAnimeRelations(id: Int): Result<List<MainModel>> {
        try {
            val result = client.query(
                GetRelationsByIdQuery(Optional.present(Random.nextInt(1, 10)))
            ).execute()

            val data = result.data?.Page?.mediaTrends ?: emptyList()

            val resultList = data.map {
                it?.media?.toDomain()!!
            }

            return Result.success(resultList)

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
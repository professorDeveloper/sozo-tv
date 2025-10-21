package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.GetAnimeByIdQuery
import com.animestudios.animeapp.GetCharacterDetailQuery
import com.animestudios.animeapp.GetCharactersAnimeByIdQuery
import com.animestudios.animeapp.GetRelationsByIdQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.utils.toDomain
import kotlin.random.Random

class DetailRepositoryImpl(private val client: ApolloClient, private val api: ImdbService) :
    DetailRepository {
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

    override suspend fun loadMovieDetail(id: Int): Result<DetailModel> {
        try {
            val movieDetailRequest = api.getMovieDetails(id)
            if (movieDetailRequest.isSuccessful) {
                val response = movieDetailRequest.body()!!
                return Result.success(response.toDomain())
            } else {
                return Result.failure(Exception(movieDetailRequest.errorBody()?.string()))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun loadSeriesDetail(id: Int): Result<DetailModel> {
        try {
            val movieDetailRequest = api.getTvDetails(id)
            if (movieDetailRequest.isSuccessful) {
                val response = movieDetailRequest.body()!!
                return Result.success(response.toDomain())
            } else {
                return Result.failure(Exception(movieDetailRequest.errorBody()?.string()))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }    }

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

    override suspend fun loadCast(id: Int): Result<List<Cast>> {
        try {
            val result = client.query(
                GetCharactersAnimeByIdQuery(Optional.present(id))
            ).execute()

            val data = result.data?.Media?.characters

            val castList = data?.nodes?.map {
                it?.toDomain()!!
            }

            return Result.success(castList!!)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun loadAnimeRelations(id: Int): Result<List<MainModel>> {
        try {
            val result = client.query(
                GetRelationsByIdQuery(Optional.present(Random.nextInt(1, 4)))
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

    override suspend fun characterDetail(id: Int): Result<CastDetailModel> {
        try {
            val result = client.query(
                GetCharacterDetailQuery(Optional.present(id))
            ).execute()

            val data = result.data?.Character?.toDomain()
            return Result.success(data!!)

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
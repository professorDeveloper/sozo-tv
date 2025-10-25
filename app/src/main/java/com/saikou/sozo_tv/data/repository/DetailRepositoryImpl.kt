package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.animestudios.animeapp.GetAnimeByIdQuery
import com.animestudios.animeapp.GetCharacterDetailQuery
import com.animestudios.animeapp.GetCharactersAnimeByIdQuery
import com.animestudios.animeapp.GetRelationsByIdQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.tmdb.TmdbListResponse
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.presentation.viewmodel.CastDetailViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.toDomain
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
                return Result.success(response.toDomain(false))
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
                return Result.success(response.toDomain(true))
            } else {
                return Result.failure(Exception(movieDetailRequest.errorBody()?.string()))
            }
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

    override suspend fun loadCastMovieSeries(id: Int, isMovie: Boolean): Result<List<Cast>> {
        return runCatching {
            val response = if (isMovie) {
                api.getCreditsForMovie(id = id)
            } else {
                api.getCreditsForSeries(series_id = id)
            }

            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to load cast: ${response.message()}")
            }

            response.body()!!.cast.map {
                Cast(
                    id = it.id, it.profileImg, it.original_name, it.character, "0"
                )
            }
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

    override suspend fun loadMovieOrSeriesRelations(
        id: Int,
        isMovie: Boolean
    ): Result<List<MainModel>> = runCatching {
        val response = if (isMovie) {
            api.getRecommendationsForMovie(id)
        } else {
            api.getRecommendationsForSeries(id)
        }

        check(response.isSuccessful && response.body() != null) {
            "Failed to load relations: ${response.message()}"
        }

        response.body()!!.results.map { it.toDomain() }
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

    override suspend fun creditDetail(id: Int): Result<CastDetailModel> {
        return safeExecute {
            val response = api.getPersonDetails(personId = id).body()!!
            val seriesList = api.getPersonMovieCredits(id)
                .body()!!.cast.map { it.toDomain() } as ArrayList<MainModel>

            CastDetailModel(
                "${LocalData.IMDB_IMAGE_PATH}${response.profile_path}",
                if (response.gender == 2) "Male" else "Female",
                response.name,
                role = if (response.also_known_as?.isNotEmpty() == true) response.also_known_as[0] else "Empty",
                seriesList,
                (response.birthday.let {
                    val date =
                        LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val age = ChronoUnit.YEARS.between(date, LocalDate.now()).toInt()
                    age
                } ?: -1).toString(),

                response.popularity.toInt()
            )
        }
    }
}
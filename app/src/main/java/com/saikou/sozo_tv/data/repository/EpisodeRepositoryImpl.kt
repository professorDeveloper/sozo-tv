package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.model.tmdb.ExternalIdsResponse
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.domain.repository.EpisodeRepository

class EpisodeRepositoryImpl(private val api: ImdbService) : EpisodeRepository {
    override suspend fun extractImdbIdFromMovie(tmdbId: String): Result<ExternalIdsResponse> {
        return safeApiCall {
            api.getMovieExternalIds(tmdbId.toInt())
        }.map {
            it
        }
    }

    override suspend fun extractImdbForSeries(tmdbId: String): Result<ExternalIdsResponse> {
        return safeApiCall {
            api.getTvExternalIds(tmdbId.toInt())
        }.map {
            it
        }
    }
}
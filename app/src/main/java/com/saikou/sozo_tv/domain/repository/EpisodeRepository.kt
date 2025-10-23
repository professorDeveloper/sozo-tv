package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.tmdb.ExternalIdsResponse

interface EpisodeRepository {
    suspend fun extractImdbIdFromMovie(tmdbId: String): Result<ExternalIdsResponse>
    suspend fun extractImdbForSeries(tmdbId: String): Result<ExternalIdsResponse>

}
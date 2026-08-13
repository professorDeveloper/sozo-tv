package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.SearchModel

interface SearchRepository {
    suspend fun searchAnime(query: String): Result<List<SearchModel>>
    suspend fun searchMovie(query: String): Result<List<SearchModel>>

    /**
     * Search every installed source instead of just the active one. Bounded and
     * failure-tolerant in the engine, so this returns whatever answered in time
     * rather than failing when one source is down.
     */
    suspend fun searchAllSources(query: String): Result<List<SearchModel>>
}
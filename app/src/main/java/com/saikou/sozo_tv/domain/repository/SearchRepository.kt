package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.data.extensions.SearchLegStatus
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    suspend fun searchAnime(query: String): Result<List<SearchModel>>
    suspend fun searchMovie(query: String): Result<List<SearchModel>>

    /**
     * Search every installed source instead of just the active one.
     *
     * A Flow, not a Result: legs arrive one per source as they answer. Waiting
     * for the whole set meant one dead mirror held the screen blank for the full
     * timeout while eight other sources had already replied.
     */
    fun searchAllSources(query: String): Flow<SearchSourceLeg>
}

data class SearchSourceLeg(
    val providerId: String,
    val providerName: String,
    val items: List<SearchModel>,
    val status: SearchLegStatus,
)

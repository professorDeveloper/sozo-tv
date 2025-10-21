package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.SearchModel

interface SearchRepository {
    suspend fun searchAnime(query: String): Result<List<SearchModel>>
    suspend fun searchMovie(query: String): Result<List<SearchModel>>
}
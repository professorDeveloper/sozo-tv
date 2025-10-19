package com.saikou.sozo_tv.domain.repository
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.GenreTmdbModel

interface TMDBHomeRepository {
    suspend fun loadGenres(): Result<List<GenreTmdbModel>>
    suspend fun loadCategories(): Result<ArrayList<Category>>

}

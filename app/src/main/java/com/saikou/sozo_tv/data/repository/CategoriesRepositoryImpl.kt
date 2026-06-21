package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toMainModel
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository

/**
 * Genre/category browsing maps to a provider search on the genre name (extensions
 * expose no universal genre-filter API, and Aniyomi sources have no genre chips).
 */
class CategoriesRepositoryImpl(
    private val engine: ExtensionEngine,
) : CategoriesRepository {

    private suspend fun byGenre(sr: SearchResults): Result<SearchResults> {
        return try {
            val query = sr.genre?.takeIf { it.isNotBlank() } ?: sr.tag
            val page = engine.search(null, query)
                ?: return Result.failure(IllegalStateException("No source selected."))
            sr.results = page.items.map { it.toMainModel() }
            sr.hasNextPage = false
            sr.currentPage = 1
            Result.success(sr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults> =
        byGenre(searchResults)

    override suspend fun loadMovieByGenre(searchResults: SearchResults): Result<SearchResults> =
        byGenre(searchResults)
}

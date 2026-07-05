package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.CategoryChip
import com.saikou.sozo_tv.domain.model.SearchResults

interface CategoriesRepository {
    suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults>
    suspend fun loadMovieByGenre(searchResults: SearchResults): Result<SearchResults>

    /**
     * Filter-row chips from the active provider's real catalog: genres() first, then
     * home() sections; empty list signals the screen to use its hardcoded fallback.
     */
    suspend fun loadGenres(): Result<List<CategoryChip>>
}
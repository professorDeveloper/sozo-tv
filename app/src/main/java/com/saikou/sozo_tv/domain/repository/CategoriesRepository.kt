package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.SearchResults

interface CategoriesRepository {
    suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults>
}
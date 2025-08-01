package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.GenreModel

interface HomeRepository {
    suspend fun getTopBannerAnime(): Result<JikanBannerResponse>
    suspend fun loadCategories(): Result<List<Category>>

    suspend fun loadGenres():Result<List<GenreModel>>
}
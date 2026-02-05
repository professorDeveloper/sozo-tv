package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.GenreModel

interface HomeRepository {
    suspend fun getTopBannerAnime(): Result<BannerModel>
    suspend fun loadCategories(): Result<List<Category>>
    suspend fun convertMalId(id: Int): Result<Int>
    suspend fun loadGenres(): Result<List<GenreModel>>
}
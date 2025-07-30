package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.Category

interface HomeRepository {
    suspend fun getTopBannerAnime(): Result<JikanBannerResponse>
    suspend fun loadCategories(): Result<List<Category>>
}
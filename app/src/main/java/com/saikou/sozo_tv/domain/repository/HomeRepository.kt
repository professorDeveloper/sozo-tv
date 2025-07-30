package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse

interface HomeRepository {
    suspend fun getTopBannerAnime(): Result<JikanBannerResponse>

}
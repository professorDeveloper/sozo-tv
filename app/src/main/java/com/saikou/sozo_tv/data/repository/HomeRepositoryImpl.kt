package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.data.remote.JikanApiService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.domain.repository.HomeRepository
import javax.inject.Named

class HomeRepositoryImpl(@Named("jikan_api") private val jikanApiService: JikanApiService) :
    HomeRepository {
    override suspend fun getTopBannerAnime(): Result<JikanBannerResponse> {
        return safeApiCall {
            jikanApiService.getTopAnime()
        }.map {
            it
        }
    }

}
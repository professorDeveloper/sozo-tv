package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import retrofit2.Response
import retrofit2.http.GET


interface JikanApiService {

    @GET("/v4/top/anime/")
    suspend fun getTopAnime():Response<JikanBannerResponse>

}
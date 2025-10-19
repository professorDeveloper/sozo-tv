package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.data.model.tmdb.TmdbGenreResponse
import com.saikou.sozo_tv.data.model.tmdb.TmdbTrendingResponse
import com.saikou.sozo_tv.domain.model.GenreTmdbModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ImdbService {


    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("language") language: String = "en"
    ): Response<TmdbGenreResponse>

    @GET("trending/all/day")
    suspend fun getTrendingAll(
        @Query("language") language: String = "en-US"
    ): Response<TmdbTrendingResponse>


}
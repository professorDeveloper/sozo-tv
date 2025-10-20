package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.model.tmdb.TmdbGenreResponse
import com.saikou.sozo_tv.data.model.tmdb.TmdbListResponse
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
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<TmdbListResponse>


    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<TmdbListResponse>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<TmdbListResponse>

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("language") language: String = "en-US"
    ): Response<TmdbListResponse>

    @GET("discover/movie")
    suspend fun getMoviesByGenre(
        @Query("language") language: String = "en-US",
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("include_adult") isAdult: Boolean = false
    ): Response<TmdbListResponse>


}
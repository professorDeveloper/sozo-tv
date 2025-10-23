package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.model.tmdb.ExternalIdsResponse
import com.saikou.sozo_tv.data.model.tmdb.MediaDetails
import com.saikou.sozo_tv.data.model.tmdb.TmdbGenreResponse
import com.saikou.sozo_tv.data.model.tmdb.TmdbListResponse
import com.saikou.sozo_tv.data.model.tmdb.cast.MediaCastResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
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

    @GET("search/multi")
    suspend fun searchMoviesByKeyword(
        @Query("query") keyword: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") isAdult: Boolean = false
    ): Response<TmdbListResponse>

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("language") lang: String = "en-US"
    ): Response<MediaDetails>

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("language") lang: String = "en-US"
    ): Response<MediaDetails>

//    curl --request GET \
//    --url 'https://api.themoviedb.org/3/movie/216015/recommendations?language=en-US&page=1' \
//    --header 'Authorization: Bearer jwt.here' \
//    --header 'accept: application/json'

    @GET("movie/{id}/recommendations")
    suspend fun getRecommendationsForMovie(
        @Path("id") id: Int,
        @Query("language") lang: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<TmdbListResponse>

    @GET("tv/{id}/recommendations")
    suspend fun getRecommendationsForSeries(
        @Path("id") id: Int,
        @Query("language") lang: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<TmdbListResponse>

//    curl --request GET \
//    --url 'https://api.themoviedb.org/3/movie/movie_id/credits?language=en-US' \
//    --header 'accept: application/json'
//
    @GET("movie/{id}/credits")
    suspend fun getCreditsForMovie(
    @Path("id") id: Int,
    @Query("language") lang: String = "en-US"
): Response<MediaCastResponse>

    @GET("tv/{series_id}/credits")
    suspend fun getCreditsForSeries(
        @Path("series_id") series_id: Int,
        @Query("language") lang: String = "en-US"
    ): Response<MediaCastResponse>

    @GET("movie/{id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("id") movieId: Int,
    ): Response<ExternalIdsResponse>

    @GET("tv/{id}/external_ids")
    suspend fun getTvExternalIds(
        @Path("id") tvId: Int,
    ): Response<ExternalIdsResponse>
}
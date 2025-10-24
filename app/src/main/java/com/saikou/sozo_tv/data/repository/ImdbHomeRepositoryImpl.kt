package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.type.MediaFormat
import com.animestudios.animeapp.type.MediaSource
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.data.model.jikan.BannerHomeData
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.GenreTmdbModel
import com.saikou.sozo_tv.domain.repository.TMDBHomeRepository
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.genreTmdb
import kotlin.random.Random

class ImdbHomeRepositoryImpl(
    private val api: ImdbService
) : TMDBHomeRepository {

    override suspend fun loadGenres(): Result<List<GenreTmdbModel>> {
        return try {
            val genreResponse = api.getMovieGenres()
            if (!genreResponse.isSuccessful) {
                return Result.failure(Exception("Genre API failed: ${genreResponse.code()}"))
            }

            val genres = genreResponse.body()?.genres ?: emptyList()

            val trendingResponse = api.getTrendingAll()
            val trendingItems = trendingResponse.body()?.results ?: emptyList()

            val randomImages = trendingItems.mapNotNull { it.imageUrl }.shuffled()

            val genreModels = genres.mapIndexed { index, genre ->
                genreTmdb.add(
                    GenreTmdbModel(
                        id = genre.id,
                        title = genre.name,
                        image = randomImages.getOrNull(index % randomImages.size) ?: ""
                    )
                )
                GenreTmdbModel(
                    id = genre.id,
                    title = genre.name,
                    image = randomImages.getOrNull(index % randomImages.size) ?: ""
                )
            }

            genreTmdb.add(GenreTmdbModel("Adult", LocalData.anime404, -1))
            Result.success(genreModels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategories(): Result<ArrayList<Category>> = safeExecute {
        val categories = arrayListOf<Category>()

        val trendingResponse = api.getPopularSeries()
        val trendingList = trendingResponse.body()?.results ?: emptyList()

        val trendingCategory = Category(name = "Trending All", list = trendingList.map {
            CategoryDetails(
                content = HomeModel(
                    id = it.id ?: 0,
                    idMal = 0,
                    coverImage = CoverImage(
                        "${LocalData.IMDB_IMAGE_PATH}${it.poster_path ?: it.backdrop_path}"
                    ),
                    format = MediaFormat.MOVIE,
                    source = MediaSource.NOVEL,
                    title = Title(it.titleFormat ?: it.name ?: ""),
                    isSeries = true

                )
            )
        })

        categories.add(trendingCategory)

        val topRatedResponse = api.getTopRatedMovies()
        val topRatedList = topRatedResponse.body()?.results ?: emptyList()
        val topRatedCategory = Category(name = "Top Rated Movies", list = topRatedList.map {
            CategoryDetails(
                content = HomeModel(
                    id = it.id ?: 0,
                    idMal = 0,
                    coverImage = CoverImage(
                        "${LocalData.IMDB_IMAGE_PATH}${it.poster_path ?: it.backdrop_path}"
                    ),
                    format = MediaFormat.MOVIE,
                    source = MediaSource.NOVEL,
                    title = Title(it.titleFormat ?: it.name ?: ""),
                    isSeries = it.media_type == "tv"

                )
            )
        })
        categories.add(topRatedCategory)
        val trendingMovieResponse = api.getTrendingSeries()
        val trendingMovieList = trendingMovieResponse.body()?.results ?: emptyList()
        val trendingMovieCategory =
            Category(name = "Trending Series", list = trendingMovieList.map {
                CategoryDetails(
                    content = HomeModel(
                        id = it.id ?: 0,
                        idMal = 0,
                        coverImage = CoverImage(
                            "${LocalData.IMDB_IMAGE_PATH}${it.poster_path ?: it.backdrop_path}"
                        ),
                        format = MediaFormat.MOVIE,
                        source = MediaSource.NOVEL,
                        title = Title(it.titleFormat ?: it.name ?: ""),
                        isSeries = true

                    )
                )
            })
        categories.add(trendingMovieCategory)
        val randomPage = Random.nextInt(10, 20)
        val randomRecommendResponse = api.getPopularSeries(page = randomPage)

        val allMovies = randomRecommendResponse.body()?.results ?: emptyList()
        val randomRecommendCategory = Category(
            name = "Recommend Series",
            list = allMovies.map {
                CategoryDetails(
                    content = HomeModel(
                        id = it.id ?: 0,
                        idMal = 0,
                        coverImage = CoverImage(
                            "${LocalData.IMDB_IMAGE_PATH}${it.poster_path ?: it.backdrop_path}"
                        ),
                        format = MediaFormat.MOVIE,
                        source = MediaSource.NOVEL,
                        title = Title(it.titleFormat ?: it.name ?: ""),
                        isSeries = true
                    )
                )
            }
        )

        categories.add(randomRecommendCategory)

        categories
    }

    override suspend fun loadBanner(): Result<BannerModel> = safeExecute {
        val data = api.getTrendingAll()
        if (!data.isSuccessful) {
            throw Exception("Banner API failed: ${data.code()}")
        }
        val bannerList = data.body()?.results ?: emptyList()
        val bannerModels = BannerModel(data = bannerList.map {
            BannerItem(
                contentItem = BannerHomeData(
                    it.imageUrl ?: "",
                    it.titleFormat ?: "",
                    it.overview ?: "",
                    genre_ids = it.genre_ids,
                    isMovie = true,
                    isSeries = it.media_type == "tv",
                    imdb_id = it.id ?: 0,
                )
            )
        })
        bannerModels
    }


}

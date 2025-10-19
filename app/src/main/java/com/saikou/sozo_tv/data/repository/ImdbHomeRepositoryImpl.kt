package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.type.MediaFormat
import com.animestudios.animeapp.type.MediaSource
import com.saikou.sozo_tv.data.model.anilist.CoverImage
import com.saikou.sozo_tv.data.model.anilist.HomeModel
import com.saikou.sozo_tv.data.model.anilist.Title
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.safeApiCall
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.GenreTmdbModel
import com.saikou.sozo_tv.domain.repository.TMDBHomeRepository
import com.saikou.sozo_tv.utils.LocalData

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
                GenreTmdbModel(
                    id = genre.id,
                    title = genre.name,
                    image = randomImages.getOrNull(index % randomImages.size) ?: ""
                )
            }

            Result.success(genreModels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategories(): Result<ArrayList<Category>> = safeExecute {
        val categories = arrayListOf<Category>()

        val trendingResponse = api.getTrendingAll()
        val trendingList = trendingResponse.body()?.results ?: emptyList()

        val trendingCategory = Category(name = "Trending Movies", list = trendingList.map {
            CategoryDetails(
                content = HomeModel(
                    id = it.id ?: 0,
                    idMal = 0,
                    coverImage = CoverImage(
                        "${LocalData.IMDB_IMAGE_PATH}${it.poster_path ?: it.backdrop_path}"
                    ),
                    format = MediaFormat.MOVIE,
                    source = MediaSource.NOVEL,
                    title = Title(it.title ?: it.name ?: "")
                )
            )
        })

        categories.add(trendingCategory)

        categories
    }


}

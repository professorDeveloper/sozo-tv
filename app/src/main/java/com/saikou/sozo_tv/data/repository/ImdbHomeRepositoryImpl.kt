package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toBannerData
import com.saikou.sozo_tv.data.extensions.toCategory
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.GenreTmdbModel
import com.saikou.sozo_tv.domain.repository.TMDBHomeRepository

/**
 * The "movie/TMDB" home path is also served by the extension engine now, so the
 * Home screen renders the active provider whether anime-mode is on or off.
 */
class ImdbHomeRepositoryImpl(
    private val engine: ExtensionEngine,
) : TMDBHomeRepository {

    private fun noSource(): Exception =
        IllegalStateException("No source selected. Open Sources to install one.")

    override suspend fun loadGenres(): Result<List<GenreTmdbModel>> {
        return try {
            val genres = engine.genres()
            Result.success(genres.mapIndexed { i, g -> GenreTmdbModel(g.name, g.image ?: "", i) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategories(): Result<ArrayList<Category>> {
        return try {
            val home = engine.home() ?: return Result.failure(noSource())
            Result.success(ArrayList(home.sections.map { it.toCategory() }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadBanner(): Result<BannerModel> {
        return try {
            val home = engine.home() ?: return Result.failure(noSource())
            Result.success(BannerModel(data = home.banner.map { BannerItem(contentItem = it.toBannerData()) }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

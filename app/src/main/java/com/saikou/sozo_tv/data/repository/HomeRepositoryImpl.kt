package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toBannerData
import com.saikou.sozo_tv.data.extensions.toCategory
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.GenreModel
import com.saikou.sozo_tv.domain.repository.HomeRepository

/**
 * Home data now comes from the active extension provider's main page
 * (`ExtensionEngine.home()` → banner + sections) and category chips
 * (`ExtensionEngine.genres()`), replacing the former AniList GraphQL queries.
 */
class HomeRepositoryImpl(
    private val engine: ExtensionEngine,
) : HomeRepository {

    private fun noSource(): Exception =
        IllegalStateException("No source selected. Open Sources to install one.")

    override suspend fun getTopBannerAnime(): Result<BannerModel> {
        return try {
            val home = engine.home() ?: return Result.failure(noSource())
            val items = home.banner.map { BannerItem(contentItem = it.toBannerData()) }
            Result.success(BannerModel(data = items))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategories(): Result<List<Category>> {
        return try {
            val home = engine.home() ?: return Result.failure(noSource())
            Result.success(home.sections.map { it.toCategory() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convertMalId(id: Int): Result<Int> = Result.success(id)

    override suspend fun loadGenres(): Result<List<GenreModel>> {
        return try {
            val genres = engine.genres()
            Result.success(genres.map { GenreModel(title = it.name, image = it.image ?: "") })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

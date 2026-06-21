package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toSearchModel
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository

/** Search delegates to the active provider's `search()`. */
class SearchRepositoryImpl(
    private val engine: ExtensionEngine,
) : SearchRepository {

    private suspend fun run(query: String): Result<List<SearchModel>> {
        return try {
            val page = engine.search(null, query)
                ?: return Result.failure(IllegalStateException("No source selected."))
            Result.success(page.items.map { it.toSearchModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAnime(query: String): Result<List<SearchModel>> = run(query)
    override suspend fun searchMovie(query: String): Result<List<SearchModel>> = run(query)
}

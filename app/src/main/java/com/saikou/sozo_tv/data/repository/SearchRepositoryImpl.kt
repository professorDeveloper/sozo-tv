package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toSearchModel
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.saikou.sozo_tv.domain.repository.SearchSourceLeg

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

    // No "no source selected" failure here on purpose: a global search does not
    // depend on one being active, and an empty corpus is an empty RESULT, not an
    // error the UI should render as a crash banner.
    override fun searchAllSources(query: String): Flow<SearchSourceLeg> =
        engine.searchAllFlow(query).map { leg ->
            SearchSourceLeg(
                providerId = leg.provider.id,
                providerName = leg.provider.name,
                items = leg.items.map { it.toSearchModel() },
                status = leg.status,
            )
        }
}

package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toMainModel
import com.saikou.sozo_tv.domain.model.PaginatedResult
import com.saikou.sozo_tv.domain.repository.ViewAllRepository

/**
 * "View all" pages a single extension home row through [ExtensionEngine.section] using the
 * section slug captured when the row was built (e.g. "popular" / "latest" / a CloudStream
 * MainPageData.data). Items are mapped to the UI's [com.saikou.sozo_tv.domain.model.MainModel].
 */
class ViewAllRepositoryImpl(
    private val engine: ExtensionEngine,
) : ViewAllRepository {

    override suspend fun loadMore(slug: String?, page: Int): Result<PaginatedResult> {
        if (slug.isNullOrEmpty()) {
            return Result.success(PaginatedResult(page, page, 0, emptyList()))
        }
        return try {
            val ext = engine.section(provider = null, slug = slug, page = page)
                ?: return Result.failure(IllegalStateException("No source selected."))
            val list = ext.items.map { it.toMainModel() }
            Result.success(
                PaginatedResult(
                    page = ext.page,
                    totalPages = ext.totalPages,
                    totalResults = list.size,
                    list = list,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.PaginatedResult

interface ViewAllRepository {
    /** Page [page] of the extension home section identified by [slug] (engine.section). */
    suspend fun loadMore(slug: String?, page: Int): Result<PaginatedResult>
}

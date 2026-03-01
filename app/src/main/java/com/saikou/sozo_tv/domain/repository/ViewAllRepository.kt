package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.RowId
import com.saikou.sozo_tv.data.model.jikan.Pagination
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.PaginatedResult

interface ViewAllRepository {
    suspend fun loadMore(rowId: RowId, page: Int): Result<PaginatedResult>
}
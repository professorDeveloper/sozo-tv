package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.MyListRepository

/**
 * AniList "my list" sync was removed with the GraphQL backend. Local bookmarks
 * (Room) remain the source of saved titles; this returns empty.
 */
class MyListRepositoryImpl : MyListRepository {
    override suspend fun getMyList(userId: Int, listType: String): Result<List<MainModel>> =
        Result.success(emptyList())
}

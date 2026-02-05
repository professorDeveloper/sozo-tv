package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.MainModel

interface MyListRepository {
    suspend fun getMyList(userId: Int, listType: String): Result<List<MainModel>>
}
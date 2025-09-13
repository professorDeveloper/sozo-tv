package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity

interface WatchHistoryRepository {

    suspend fun addHistory(history: WatchHistoryEntity)

    suspend fun removeHistory(videoUrl: String)

    suspend fun isWatched(id: String): Boolean

    suspend fun getAllHistory(): List<WatchHistoryEntity>

    suspend fun getWatchHistoryByVideoUrl(videoUrl: String): WatchHistoryEntity?
    suspend fun getWatchHistoryById(id: String): WatchHistoryEntity?

    suspend fun clearAllHistory()
}

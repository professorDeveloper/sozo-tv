package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity

interface WatchHistoryRepository {

    /** Pin storage before suspending so profile switches cannot redirect in-flight writes. */
    fun snapshot(namespace: String?): WatchHistoryRepository = this

    suspend fun addHistory(history: WatchHistoryEntity)

    suspend fun removeHistory(videoUrl: String)

    suspend fun isWatched(id: String): Boolean

    suspend fun getAllHistory(): List<WatchHistoryEntity>

    suspend fun getWatchHistoryByVideoUrl(videoUrl: String): WatchHistoryEntity?
    suspend fun getWatchHistoryById(id: String): WatchHistoryEntity?

    suspend fun clearAllHistory()
}

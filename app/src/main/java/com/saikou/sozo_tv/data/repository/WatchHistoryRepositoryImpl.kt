package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.dao.WatchHistoryDao
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository


class WatchHistoryRepositoryImpl(private val watchHistoryDao: WatchHistoryDao) :
    WatchHistoryRepository {

    override suspend fun addHistory(history: WatchHistoryEntity) {
        watchHistoryDao.insertOrUpdate(history)
    }

    override suspend fun removeHistory(videoUrl: String) {
        watchHistoryDao.deleteByVideoUrl(videoUrl)
    }

    override suspend fun isWatched(id: String): Boolean {
        return watchHistoryDao.getWatchHistoryByVideoUrl(id) != null
    }


    override suspend fun getAllHistory(): List<WatchHistoryEntity> {
        return watchHistoryDao.getAllWatchHistory()
    }

    override suspend fun getWatchHistoryByVideoUrl(id: String): WatchHistoryEntity? {

        return watchHistoryDao.getWatchHistoryByVideoUrl(id)
    }

    override suspend fun getWatchHistoryById(id: String): WatchHistoryEntity? {
        return watchHistoryDao.getWatchHistoryById(id)
    }

    override suspend fun clearAllHistory() {
        watchHistoryDao.clearAllHistory()
    }
}

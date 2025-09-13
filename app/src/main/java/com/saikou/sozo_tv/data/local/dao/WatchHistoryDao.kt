package com.saikou.sozo_tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    suspend fun getAllWatchHistory(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE session= :id LIMIT 1")
    suspend fun getWatchHistoryByVideoUrl(id: Int): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE session= :id LIMIT 1")
    suspend fun getWatchHistoryById(id: Int): WatchHistoryEntity?


    @Query("DELETE FROM watch_history WHERE session= :id")
    suspend fun deleteByVideoUrl(id: Int)


    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}
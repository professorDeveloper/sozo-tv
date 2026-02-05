package com.saikou.sozo_tv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikou.sozo_tv.data.local.entity.ChannelsEntity

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(movie: ChannelsEntity)

    @Delete
    suspend fun removeBookmark(movie: ChannelsEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM channelsbookmark WHERE id = :movieId)")
    suspend fun isBookmarked(movieId: Int): Boolean

    @Query("SELECT * FROM channelsbookmark")
    suspend fun getAllBookmarks(): List<ChannelsEntity>

    @Query("DELETE FROM channelsbookmark")
    suspend fun clearAllBookmarks(): Int
}
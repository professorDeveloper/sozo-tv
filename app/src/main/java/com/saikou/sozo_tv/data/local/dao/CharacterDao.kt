package com.saikou.sozo_tv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity

@Dao
interface CharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(movie: CharacterEntity)
    @Delete
    suspend fun removeBookmark(movie: CharacterEntity)
    @Query("SELECT EXISTS(SELECT 1 FROM characterbookmark WHERE id = :movieId)")
    suspend fun isBookmarked(movieId: Int): Boolean

    @Query("SELECT * FROM characterbookmark")
    suspend fun getAllBookmarks(): List<CharacterEntity>

    @Query("DELETE FROM characterbookmark")
    suspend fun clearAllBookmarks(): Int
}
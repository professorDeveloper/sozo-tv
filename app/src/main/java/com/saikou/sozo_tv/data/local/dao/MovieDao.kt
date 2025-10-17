package com.saikou.sozo_tv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(movie: AnimeBookmark)

    @Delete
    suspend fun removeBookmark(movie: AnimeBookmark)

    @Query("SELECT EXISTS(SELECT 1 FROM moviebookmark WHERE id = :movieId)")
    suspend fun isBookmarked(movieId: Int): Boolean


    @Query("SELECT * FROM moviebookmark")
    suspend fun getAllBookmarks(): List<AnimeBookmark>

    @Query("DELETE FROM moviebookmark")
    suspend fun clearAllBookmarks(): Int
}

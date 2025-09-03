package com.saikou.sozo_tv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark

@Dao
interface MovieDao {

    // Insert a bookmark
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(movie: AnimeBookmark)

    // Remove a bookmark
    @Delete
    suspend fun removeBookmark(movie: AnimeBookmark)

    // Check if a movie is bookmarked
    @Query("SELECT EXISTS(SELECT 1 FROM moviebookmark WHERE id = :movieId)")
    suspend fun isBookmarked(movieId: Int): Boolean


    // Get all bookmarks
    @Query("SELECT * FROM moviebookmark")
    suspend fun getAllBookmarks(): List<AnimeBookmark>

    // Clear all bookmarks
    @Query("DELETE FROM moviebookmark")
    suspend fun clearAllBookmarks(): Int // Return the number of rows deleted
}

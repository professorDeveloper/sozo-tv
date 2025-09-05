package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity

interface CharacterBookmarkRepository {
    suspend fun addBookmark(movie: CharacterEntity)

    suspend fun removeBookmark(movie: CharacterEntity)
    suspend fun isBookmarked(movieId: Int): Boolean
    suspend fun getAllBookmarks(): List<CharacterEntity>

    suspend fun clearAllBookmarks()
}
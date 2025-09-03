package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.local.entity.AnimeBookmark


interface MovieBookmarkRepository {
    suspend fun addBookmark(movie: AnimeBookmark)

    suspend fun removeBookmark(movie: AnimeBookmark)
    suspend fun isBookmarked(movieId: Int): Boolean
    suspend fun getAllBookmarks(): List<AnimeBookmark>

    suspend fun clearAllBookmarks()
}
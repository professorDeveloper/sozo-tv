package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.dao.MovieDao
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository

class MovieBookmarkRepositoryImpl(private val dao: MovieDao) : MovieBookmarkRepository {

    override suspend fun addBookmark(movie: AnimeBookmark) {
        dao.insertBookmark(movie)
    }

    override suspend fun removeBookmark(movie: AnimeBookmark) {
        dao.removeBookmark(movie)
    }

    override suspend fun isBookmarked(movieId: Int): Boolean {
        return dao.isBookmarked(movieId)
    }

    override suspend fun getAllBookmarks(): List<AnimeBookmark> {
        return dao.getAllBookmarks()
    }

    override suspend fun clearAllBookmarks() {
        dao.clearAllBookmarks()
    }
}

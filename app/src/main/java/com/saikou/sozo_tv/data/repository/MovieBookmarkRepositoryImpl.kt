package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.database.ProfileDatabaseProvider
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository

class MovieBookmarkRepositoryImpl(private val databases: ProfileDatabaseProvider) : MovieBookmarkRepository {

    private val dao get() = databases.movieDao()

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

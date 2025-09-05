package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.dao.CharacterDao
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository

class CharacterBookmarkRepositoryImpl(private val dao: CharacterDao) : CharacterBookmarkRepository {
    override suspend fun addBookmark(movie: CharacterEntity) {
        dao.insertBookmark(movie)
    }

    override suspend fun removeBookmark(movie: CharacterEntity) {
        dao.removeBookmark(movie)
    }

    override suspend fun isBookmarked(movieId: Int): Boolean {
        return dao.isBookmarked(movieId)
    }

    override suspend fun getAllBookmarks(): List<CharacterEntity> {
        return dao.getAllBookmarks()
    }

    override suspend fun clearAllBookmarks() {
        dao.clearAllBookmarks()
    }
}
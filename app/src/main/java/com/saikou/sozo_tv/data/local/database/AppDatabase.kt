package com.saikou.sozo_tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.saikou.sozo_tv.data.local.dao.CharacterDao
import com.saikou.sozo_tv.data.local.dao.MovieDao
import com.saikou.sozo_tv.data.local.dao.WatchHistoryDao
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity

@Database(
    entities = [AnimeBookmark::class, CharacterEntity::class, WatchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun characterDao(): CharacterDao
    abstract fun watchHistoryDao(): WatchHistoryDao
}

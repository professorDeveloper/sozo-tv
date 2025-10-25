package com.saikou.sozo_tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.saikou.sozo_tv.data.local.dao.ChannelDao
import com.saikou.sozo_tv.data.local.dao.CharacterDao
import com.saikou.sozo_tv.data.local.dao.MovieDao
import com.saikou.sozo_tv.data.local.dao.WatchHistoryDao
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.ChannelsEntity
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity

@Database(
    entities = [AnimeBookmark::class, CharacterEntity::class, WatchHistoryEntity::class, ChannelsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun tvDao(): ChannelDao
    abstract fun characterDao(): CharacterDao
    abstract fun watchHistoryDao(): WatchHistoryDao
}

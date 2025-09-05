package com.saikou.sozo_tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.saikou.sozo_tv.data.local.dao.CharacterDao
import com.saikou.sozo_tv.data.local.dao.MovieDao
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity

@Database(
    entities = [AnimeBookmark::class, CharacterEntity::class],
    version = 1,
    exportSchema = false
)
//@TypeConverters(Converters::class) // BU YERGA QO‘SHAMIZ!
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun characterDao(): CharacterDao
//    abstract fun watchHistoryDao(): WatchHistoryDao
}

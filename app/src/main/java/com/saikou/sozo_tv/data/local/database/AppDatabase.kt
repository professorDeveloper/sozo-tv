package com.saikou.sozo_tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun tvDao(): ChannelDao
    abstract fun characterDao(): CharacterDao
    abstract fun watchHistoryDao(): WatchHistoryDao
}

/**
 * Adds `providerId` to watch history.
 *
 * A real migration rather than a destructive fallback: this database also holds
 * every bookmark and every character the user saved, and losing those to add one
 * nullable-ish column would be a far worse bug than the one being fixed.
 *
 * Existing rows get "" and keep syncing under their old identity — they were
 * written before the app knew which provider played them, and inventing one now
 * would be a guess.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_history ADD COLUMN providerId TEXT NOT NULL DEFAULT ''")
    }
}

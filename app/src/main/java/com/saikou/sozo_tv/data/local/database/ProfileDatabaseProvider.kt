package com.saikou.sozo_tv.data.local.database

import android.content.Context
import androidx.room.Room
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import com.saikou.sozo_tv.domain.model.HouseholdProfile

/**
 * Opens one Room database per household profile. The default profile keeps the
 * legacy filename, so existing history and bookmarks migrate without copying.
 * At most four profile databases can exist, therefore retaining the open Room
 * instances is both cheap and safer than closing a database under an in-flight
 * player/history write during a profile switch.
 */
class ProfileDatabaseProvider(
    private val context: Context,
    private val profiles: ActiveProfileStore,
) {
    private val databases = ConcurrentHashMap<String, AppDatabase>()

    fun database(namespace: String? = profiles.namespace()): AppDatabase {
        val name = namespace?.let { "movie_database_${safeKey(it)}" } ?: DEFAULT_DATABASE
        return databases.computeIfAbsent(name) {
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }

    fun movieDao() = database().movieDao()
    fun channelDao() = database().tvDao()
    fun characterDao() = database().characterDao()
    fun historyDao() = database().watchHistoryDao()

    @Synchronized
    fun delete(profile: HouseholdProfile) {
        val name = profile.localNamespace?.let { "movie_database_${safeKey(it)}" } ?: DEFAULT_DATABASE
        databases.remove(name)?.close()
        context.deleteDatabase(name)
    }

    /** Sign-out removes every household member's private local library. */
    @Synchronized
    fun deleteAll(profiles: List<HouseholdProfile>) {
        (profiles + listOfNotNull(this.profiles.current()))
            .distinctBy { it.id }
            .forEach(::delete)
        databases.remove(DEFAULT_DATABASE)?.close()
        context.deleteDatabase(DEFAULT_DATABASE)
    }

    private fun safeKey(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .take(10)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_DATABASE = "movie_database"
    }
}

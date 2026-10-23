package com.saikou.sozo_tv.history

import android.content.SharedPreferences
import com.google.gson.Gson
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.data.remote.profiles.ProfileHeaderInterceptor
import com.saikou.sozo_tv.data.remote.profiles.ProfileRequestScope
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ProfileHistorySyncTest {
    private val gson = Gson()
    private fun profile(id: String) = HouseholdProfile(id, id)

    @Test fun `profile switch during request never writes old response into new profile`() = runBlocking {
        val profiles = ActiveProfileStore(memoryPrefs(), gson).apply { set(profile("a")) }
        val databaseA = MemoryHistory(mutableListOf(row()))
        val databaseB = MemoryHistory()
        val switching = object : WatchHistoryRepository by databaseA {
            override fun snapshot(namespace: String?) = if (namespace == "a") databaseA else databaseB
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(ProfileHeaderInterceptor { profiles.profileId() })
            .addInterceptor { chain ->
                assertEquals("a", chain.request().header("X-Sozo-Profile"))
                profiles.set(profile("b"))
                response(chain.request(), """{"serverTime":"2026-10-06T00:00:00.000Z","items":[{"provider":"p","key":"p|show|e1","deletedAt":"2026-10-06T00:00:00.000Z"}]}""")
            }.build()
        val sync = WatchHistorySyncRepository(memoryPrefs(), WatchHistorySyncClient(client, gson, "https://example.test/api") { "token" }, switching, profiles)
        assertFalse(sync.sync())
        assertEquals(1, databaseA.rows.size)
        assertTrue(databaseB.rows.isEmpty())
        assertFalse(sync.hasSynced)
    }

    @Test fun `key only phone deletion removes matching TV row`() = runBlocking {
        val profiles = ActiveProfileStore(memoryPrefs(), gson).apply { set(profile("a")) }
        val database = MemoryHistory(mutableListOf(row()))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), """{"items":[{"provider":"p","key":"p|show|e1","deletedAt":"2026-10-06T00:00:00.000Z"}],"serverTime":"2026-10-06T00:00:00.000Z"}""")
        }.build()
        val sync = WatchHistorySyncRepository(memoryPrefs(), WatchHistorySyncClient(client, gson, "https://example.test/api") { "token" }, database, profiles)
        assertTrue(sync.sync())
        assertTrue(database.rows.isEmpty())
    }

    @Test fun `captured default profile does not adopt newly active profile header`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(ProfileHeaderInterceptor { "new-profile" })
            .addInterceptor { chain ->
                assertNull(chain.request().header("X-Sozo-Profile"))
                response(chain.request(), "{}")
            }.build()
        client.newCall(Request.Builder().url("https://example.test/")
            .tag(ProfileRequestScope::class.java, ProfileRequestScope(null)).build()).execute().close()
    }

    private fun row() = WatchHistoryEntity(
        session="session", title="Episode 1", mediaName="Show", image="", categoryProperty=null,
        categoryid="show", country=null, description=null, language=null, rating=null, page=1,
        release_year=null, videoUrl="https://example.test/video", totalDuration=100_000,
        lastPosition=1000, watchedAt=1000, isSeries=true, epIndex=0, providerId="p",
    )

    private fun response(request: Request, json: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()

    private class MemoryHistory(val rows: MutableList<WatchHistoryEntity> = mutableListOf()) : WatchHistoryRepository {
        override suspend fun addHistory(history: WatchHistoryEntity) { rows.removeAll { it.session == history.session }; rows.add(history) }
        override suspend fun removeHistory(videoUrl: String) { rows.removeAll { it.session == videoUrl } }
        override suspend fun isWatched(id: String) = rows.any { it.session == id }
        override suspend fun getAllHistory() = rows.toList()
        override suspend fun getWatchHistoryByVideoUrl(videoUrl: String) = rows.find { it.session == videoUrl }
        override suspend fun getWatchHistoryById(id: String) = rows.find { it.session == id }
        override suspend fun clearAllHistory() { rows.clear() }
    }

    private fun memoryPrefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString", "putLong", "putInt", "putBoolean", "putFloat", "putStringSet" -> { values[args[0] as String] = args[1]; editor }
                "remove" -> { values.remove(args[0]); editor }
                "clear" -> { values.clear(); editor }
                "commit" -> true
                "apply" -> null
                else -> null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args[0])
                "getString", "getLong", "getInt", "getBoolean", "getFloat", "getStringSet" -> values[args[0]] ?: args[1]
                else -> null
            }
        } as SharedPreferences
    }
}

package com.saikou.sozo_tv.history

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import com.saikou.sozo_tv.domain.player.HistoryIdentity
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class CanonicalHistorySyncTest {
    private val gson = Gson()
    private val page = "https://provider.test/show"
    private val key get() = "p|$page|e1"
    private fun row(session: String = "sv:p\u0001episode1") = WatchHistoryEntity(session,
        "Episode 1", "Show", "", null, "sv:p\u0001$page", null, null, null, null, 1, null,
        "https://cdn.test/expiring.mp4", 100000, 1000, watchedAt=1000, isSeries=true, epIndex=0, providerId="sv:p")
    private class Store(val rows: MutableList<WatchHistoryEntity>) : WatchHistoryRepository {
        override suspend fun addHistory(history: WatchHistoryEntity) { rows.removeAll { it.session == history.session }; rows.add(history) }
        override suspend fun removeHistory(videoUrl: String) { rows.removeAll { it.session == videoUrl } }
        override suspend fun isWatched(id: String) = rows.any { it.session == id }
        override suspend fun getAllHistory() = rows.toList()
        override suspend fun getWatchHistoryByVideoUrl(videoUrl: String) = rows.find { it.session == videoUrl }
        override suspend fun getWatchHistoryById(id: String) = rows.find { it.session == id }
        override suspend fun clearAllHistory() { rows.clear() }
    }
    private fun service(store: Store, requests: MutableList<String>, reply: (Int) -> String): WatchHistorySyncRepository {
        val profiles = ActiveProfileStore(MemoryPreferences.memoryPrefs(), gson).apply { set(HouseholdProfile("a","A")) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer); requests.add(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(requests.size).toResponseBody()).build()
        }.build()
        return WatchHistorySyncRepository(MemoryPreferences.memoryPrefs(), WatchHistorySyncClient(client,gson,"https://api.test/api") { "token" },store,profiles)
    }
    private fun response(vararg items: Map<String, Any?>) = gson.toJson(mapOf("items" to items, "serverTime" to "2026-10-06T00:00:00.000Z"))
    private fun phone(at: String="1970-01-01T00:00:05.000Z") = mapOf("provider" to "p", "key" to key, "contentUrl" to page,
        "isSerial" to true, "episodeNumber" to 1, "positionMs" to 5000, "durationMs" to 100000, "watchedAt" to at)
    private fun legacy() = mapOf("provider" to "sv:p", "key" to "sv:p|sv:p\u0001$page|e1",
        "contentId" to "sv:p\u0001$page", "isSerial" to true, "episodeNumber" to 1, "watchedAt" to "1970-01-01T00:00:01.000Z",
        "extra" to mapOf("session" to row().session,"categoryid" to row().categoryid,"providerId" to "sv:p", "isSeries" to true,"epIndex" to 0,"page" to 1))

    @Test fun `actual phone key updates already watched tagged TV episode and upload is portable`() = runBlocking {
        val store=Store(mutableListOf(row())); val requests=mutableListOf<String>()
        val sync=service(store,requests) { response(phone()) }
        assertTrue(sync.sync()); assertEquals(5000L,store.rows.single().lastPosition)
        assertEquals(row().session,store.rows.single().session)
        val first=JsonParser.parseString(requests.first()).asJsonObject
        assertEquals(0,first.getAsJsonArray("items").size()); assertEquals("1970-01-01T00:00:00.000Z",first["since"].asString)
        val pushed=JsonParser.parseString(requests[1]).asJsonObject.getAsJsonArray("items")[0].asJsonObject
        assertEquals("p",pushed["provider"].asString); assertEquals(page,pushed["contentId"].asString)
        assertEquals(page,pushed["contentUrl"].asString)
    }
    @Test fun `canonical phone delete prevents stale legacy alias resurrection in later pull`() = runBlocking {
        val store=Store(mutableListOf(row())); val requests=mutableListOf<String>()
        val sync=service(store,requests) { call -> if(call==1) response(mapOf("provider" to "", "key" to key,"deletedAt" to "1970-01-01T00:00:03.000Z")) else response(legacy()) }
        assertTrue(sync.sync()); assertTrue(store.rows.isEmpty())
        assertTrue(sync.sync()); assertTrue(store.rows.isEmpty())
        assertEquals(0,JsonParser.parseString(requests[1]).asJsonObject.getAsJsonArray("items").size())
    }
    @Test fun `phone only row never fabricates a TV stream session`() = runBlocking {
        val store=Store(mutableListOf()); val sync=service(store,mutableListOf()) { response(phone()) }
        assertTrue(sync.sync()); assertTrue(store.rows.isEmpty())
    }
    @Test fun `season ambiguous local identities are not updated deleted or uploaded`() = runBlocking {
        val store=Store(mutableListOf(row("season1"),row("season2"))); val requests=mutableListOf<String>()
        val sync=service(store,requests) { response(phone()) }
        assertTrue(sync.sync()); assertEquals(listOf(1000L,1000L),store.rows.map { it.lastPosition })
        assertEquals(0,JsonParser.parseString(requests[1]).asJsonObject.getAsJsonArray("items").size())
    }
    @Test fun `local deletion sends canonical and legacy aliases`() = runBlocking {
        val store=Store(mutableListOf(row())); val requests=mutableListOf<String>()
        val sync=service(store,requests) { response() }
        sync.rememberDeleted(row());store.rows.clear();assertTrue(sync.sync())
        val keys=JsonParser.parseString(requests[1]).asJsonObject.getAsJsonArray("items").map { it.asJsonObject["key"].asString }.toSet()
        assertEquals(setOf(key,"sv:p|sv:p\u0001$page|e1"),keys)
    }
    @Test fun `malformed or plugin identities stay opaque`() {
        assertEquals("sv:p|sv:other\u0001$page|e1",HistoryIdentity.canonicalKey("sv:p|sv:other\u0001$page|e1"))
        assertEquals("sv:p|sv:p\u0001x\u0001y|e1",HistoryIdentity.canonicalKey("sv:p|sv:p\u0001x\u0001y|e1"))
        assertEquals("cs:p|cs:p\u0001$page|e1",HistoryIdentity.canonicalKey("cs:p|cs:p\u0001$page|e1"))
    }
}

package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.history.HistorySyncItem
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WatchHistorySyncRepository(
    context: Context,
    private val client: WatchHistorySyncClient,
    private val local: WatchHistoryRepository,
    private val gson: Gson = Gson(),
) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tombstoneType = object : TypeToken<Map<String, String>>() {}.type

    val hasSynced: Boolean get() = prefs.getString(KEY_CURSOR, null) != null

    suspend fun sync(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val cursor = prefs.getString(KEY_CURSOR, null)
            val tombstones = readTombstones()

            val outgoing = buildList {
                val floor = prefs.getLong(KEY_PUSHED_AT, 0L)
                local.getAllHistory()
                    .filter { it.watchedAt > floor }
                    .forEach { add(it.toSyncItem()) }
                tombstones.forEach { (key, deletedAt) ->
                    add(HistorySyncItem(provider = "", key = key, deletedAt = deletedAt, watchedAt = deletedAt))
                }
            }

            when (val result = client.sync(outgoing, cursor)) {
                is ApiResult.Ok -> {
                    applyRemote(result.body.items)
                    prefs.edit()
                        .putString(KEY_CURSOR, result.body.serverTime ?: cursor)
                        .putLong(KEY_PUSHED_AT, outgoing.maxOfOrNull { parseIso(it.watchedAt) } ?: prefs.getLong(KEY_PUSHED_AT, 0L))
                        .remove(KEY_TOMBSTONES)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    fun syncAsync(): Job = scope.launch { runCatching { sync() } }

    suspend fun rememberDeleted(entity: WatchHistoryEntity) {
        val key = entity.syncKey() ?: return
        mutex.withLock { writeTombstone(key) }
    }

    suspend fun rememberClearedAll() {
        val keys = local.getAllHistory().mapNotNull { it.syncKey() }
        if (keys.isEmpty()) return
        mutex.withLock {
            val now = nowIso()
            writeTombstones(readTombstones() + keys.associateWith { now })
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private suspend fun applyRemote(items: List<HistorySyncItem>) {
        for (item in items) {
            val key = item.key ?: continue
            val session = item.extra?.get(EXTRA_SESSION) as? String

            if (item.deletedAt != null) {
                session?.let { local.removeHistory(it) }
                continue
            }

            val existing = session?.let { local.getWatchHistoryById(it) }
                ?: findByKey(key)

            when {
                existing != null -> {
                    if (item.watchedAtMillis() > existing.watchedAt) {
                        local.addHistory(
                            existing.copy(
                                lastPosition = item.positionMs,
                                totalDuration = if (item.durationMs > 0) item.durationMs else existing.totalDuration,
                                watchedAt = item.watchedAtMillis(),
                            )
                        )
                    }
                }
                item.extra != null && session != null -> {
                    item.toEntity(session)?.let { local.addHistory(it) }
                }
                else -> Unit
            }
        }
    }

    private suspend fun findByKey(key: String): WatchHistoryEntity? =
        local.getAllHistory().firstOrNull { it.syncKey() == key }

    private fun readTombstones(): Map<String, String> {
        val raw = prefs.getString(KEY_TOMBSTONES, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, tombstoneType) }
            .getOrNull() ?: emptyMap()
    }

    private fun writeTombstone(key: String) = writeTombstones(readTombstones() + (key to nowIso()))

    private fun writeTombstones(all: Map<String, String>) {
        prefs.edit().putString(KEY_TOMBSTONES, gson.toJson(all)).apply()
    }

    private fun WatchHistoryEntity.toSyncItem() = HistorySyncItem(
        provider = syncProvider(),
        contentId = categoryid,
        contentUrl = videoUrl,
        title = mediaName.ifBlank { title },
        thumbnail = image,
        isSerial = isSeries,
        episodeIndex = epIndex.takeIf { it >= 0 },
        episodeNumber = epIndex.takeIf { it >= 0 }?.plus(1),
        positionMs = lastPosition,
        durationMs = totalDuration,
        watchedAt = isoOf(watchedAt),
        extra = mapOf(
            EXTRA_SESSION to session,
            "title" to title,
            "mediaName" to mediaName,
            "image" to image,
            "categoryProperty" to categoryProperty,
            "categoryid" to categoryid,
            "country" to country,
            "description" to description,
            "language" to language,
            "rating" to rating,
            "page" to page,
            "release_year" to release_year,
            "videoUrl" to videoUrl,
            "isEpisode" to isEpisode,
            "imdbID" to imdbID,
            "epIndex" to epIndex,
            "currentQualityIndex" to currentQualityIndex,
            "isAnime" to isAnime,
            "isSeries" to isSeries,
            "source" to source,
            "currentSourceName" to currentSourceName,
            "providerId" to providerId,
        ),
    )

    private fun HistorySyncItem.toEntity(session: String): WatchHistoryEntity? {
        val e = extra ?: return null
        fun str(k: String) = e[k] as? String
        fun num(k: String) = (e[k] as? Number)
        return WatchHistoryEntity(
            session = session,
            title = str("title") ?: title.orEmpty(),
            mediaName = str("mediaName") ?: title.orEmpty(),
            image = str("image") ?: thumbnail.orEmpty(),
            categoryProperty = str("categoryProperty"),
            categoryid = str("categoryid") ?: contentId,
            country = str("country"),
            description = str("description"),
            language = str("language"),
            rating = num("rating")?.toDouble(),
            page = num("page")?.toInt(),
            release_year = str("release_year"),
            videoUrl = str("videoUrl") ?: contentUrl.orEmpty(),
            totalDuration = durationMs,
            lastPosition = positionMs,
            watchedAt = watchedAtMillis(),
            isEpisode = e["isEpisode"] as? Boolean ?: true,
            imdbID = str("imdbID") ?: "-1",
            epIndex = num("epIndex")?.toInt() ?: -1,
            currentQualityIndex = num("currentQualityIndex")?.toInt() ?: -1,
            isAnime = e["isAnime"] as? Boolean ?: true,
            isSeries = e["isSeries"] as? Boolean ?: false,
            source = str("source").orEmpty(),
            currentSourceName = str("currentSourceName").orEmpty(),
            providerId = str("providerId").orEmpty(),
        )
    }

    private fun HistorySyncItem.watchedAtMillis(): Long =
        parseIso(watchedAt).takeIf { it > 0 } ?: System.currentTimeMillis()

    private fun WatchHistoryEntity.syncProvider(): String =
        providerId.ifBlank { source.ifBlank { currentSourceName } }.trim()

    private fun WatchHistoryEntity.syncKey(): String? {
        val provider = syncProvider()
        val base = (categoryid?.takeIf { it.isNotBlank() } ?: videoUrl).trim()
        if (base.isEmpty()) return null
        val head = "$provider|$base"
        if (!isSeries) return head
        return if (epIndex < 0) head else "$head|e${epIndex + 1}"
    }

    private companion object {
        const val FILE = "sozo_history_sync"
        const val KEY_CURSOR = "cursor"
        const val KEY_PUSHED_AT = "pushed_at"
        const val KEY_TOMBSTONES = "tombstones"
        const val EXTRA_SESSION = "session"

        private fun formatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun nowIso(): String = formatter().format(Date())
        fun isoOf(millis: Long): String = formatter().format(Date(millis))
        fun parseIso(value: String?): Long =
            value?.let { runCatching { formatter().parse(it)?.time }.getOrNull() } ?: 0L
    }
}

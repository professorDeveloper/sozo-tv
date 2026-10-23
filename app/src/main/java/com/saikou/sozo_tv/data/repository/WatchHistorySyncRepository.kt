package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.remote.history.HistorySyncItem
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.sources.ExtensionParser
import com.saikou.sozo_tv.domain.player.HistoryIdentity
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

class WatchHistorySyncRepository internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val client: WatchHistorySyncClient,
    private val local: WatchHistoryRepository,
    private val profileStore: ActiveProfileStore,
    private val gson: Gson = Gson(),
) {

    constructor(
        context: Context,
        client: WatchHistorySyncClient,
        local: WatchHistoryRepository,
        profileStore: ActiveProfileStore,
        gson: Gson = Gson(),
    ) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), client, local, profileStore, gson)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tombstoneType = object : TypeToken<Map<String, String>>() {}.type

    private fun key(value: String, namespace: String? = profileStore.namespace()): String =
        namespace?.let { "profile_${it}__$value" } ?: value

    val hasSynced: Boolean get() = prefs.getString(key(KEY_CURSOR), null) != null

    suspend fun sync(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val captured = profileStore.snapshot()
            val revision = captured.revision
            val profile = captured.profile
            val namespace = profile?.localNamespace
            val storage = local.snapshot(namespace)
            fun scoped(value: String) = key(value, namespace)
            var cursor = prefs.getString(scoped(KEY_CURSOR), null)
            val needsMigration = prefs.getInt(scoped(KEY_IDENTITY_VERSION), 0) < IDENTITY_VERSION
            val requestScope = com.saikou.sozo_tv.data.remote.profiles.ProfileRequestScope(profile?.id)
            // Pull retained tombstones before replaying old rows under a canonical name. A null
            // cursor omits tombstones on the API; epoch explicitly requests them during migration.
            if (needsMigration) {
                if (revision != profileStore.revision) return@withContext false
                when (val pull = client.sync(emptyList(), isoOf(0), requestScope)) {
                    is ApiResult.Ok -> {
                        if (revision != profileStore.revision) return@withContext false
                        applyRemote(pull.body.items, storage, namespace)
                        cursor = pull.body.serverTime ?: cursor
                    }
                    else -> return@withContext false
                }
            }
            val tombstones = readTombstones(namespace)
            val remoteDeleted = readDeleted(namespace)
            val outgoing = buildList {
                val floor = if (needsMigration) 0L else prefs.getLong(scoped(KEY_PUSHED_AT), 0L)
                storage.getAllHistory().groupBy { it.syncKey() }.values
                    .filter { it.size == 1 }.map { it.single() }
                    .filter { it.watchedAt > floor && it.watchedAt > (it.syncKey()?.let(remoteDeleted::get) ?: 0L) }
                    .forEach { add(it.toSyncItem()) }
                tombstones.forEach { (key, deletedAt) ->
                    add(HistorySyncItem(provider = "", key = key, deletedAt = deletedAt, watchedAt = deletedAt))
                }
            }

            if (revision != profileStore.revision) return@withContext false
            when (val result = client.sync(outgoing, cursor, requestScope)) {
                is ApiResult.Ok -> {
                    // Sign-out/profile switch invalidates this response; retry from its old cursor later.
                    if (revision != profileStore.revision) return@withContext false
                    applyRemote(result.body.items, storage, namespace)
                    prefs.edit()
                        .putString(scoped(KEY_CURSOR), result.body.serverTime ?: cursor)
                        .putInt(scoped(KEY_IDENTITY_VERSION), IDENTITY_VERSION)
                        .putLong(scoped(KEY_PUSHED_AT), outgoing.maxOfOrNull { parseIso(it.watchedAt) } ?: prefs.getLong(scoped(KEY_PUSHED_AT), 0L))
                        .remove(scoped(KEY_TOMBSTONES))
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    fun syncAsync(): Job = scope.launch { runCatching { sync() } }

    suspend fun rememberDeleted(entity: WatchHistoryEntity) {
        val namespace = profileStore.namespace()
        val keys = entity.syncKeys()
        if (keys.isEmpty()) return
        mutex.withLock {
            val now = nowIso()
            writeTombstones(readTombstones(namespace) + keys.associateWith { now }, namespace)
        }
    }

    suspend fun rememberClearedAll() {
        val namespace = profileStore.namespace()
        val keys = local.snapshot(namespace).getAllHistory().flatMap { it.syncKeys() }
        if (keys.isEmpty()) return
        mutex.withLock {
            val now = nowIso()
            writeTombstones(readTombstones(namespace) + keys.associateWith { now }, namespace)
        }
    }

    fun clear() = prefs.edit().clear().apply()

    /**
     * Sign-out. The sync state AND the local rows belong to the account that is leaving: rows left
     * in Room were pushed into the next account on its first sync. Runs on this repository's own
     * scope because sign-out relaunches the task and tears down the caller's ViewModel.
     */
    fun forgetAccount(): Job {
        val storage = local.snapshot(profileStore.namespace())
        clear()
        return scope.launch {
            mutex.withLock { runCatching { storage.clearAllHistory() } }
        }
    }

    private suspend fun applyRemote(items: List<HistorySyncItem>, storage: WatchHistoryRepository, namespace: String?) {
        val deleted = readDeleted(namespace).toMutableMap()
        // Local pending deletes also block an old live alias until the server acknowledges them.
        readTombstones(namespace).forEach { (legacy, at) ->
            val key = HistoryIdentity.canonicalKey(legacy)
            deleted[key] = maxOf(deleted[key] ?: 0L, parseIso(at))
        }
        items.filter { it.deletedAt != null }.forEach { item ->
            item.key?.let(HistoryIdentity::canonicalKey)?.let { key ->
                deleted[key] = maxOf(deleted[key] ?: 0L, parseIso(item.deletedAt))
            }
        }
        // Delete before processing live rows, and retain the deletion watermark across later pulls.
        for ((key, at) in deleted) {
            findByKey(key, storage)?.takeIf { it.watchedAt <= at }?.let { storage.removeHistory(it.session) }
        }
        for (item in items.filter { it.deletedAt == null }) {
            val key = item.key?.let(HistoryIdentity::canonicalKey) ?: continue
            if (parseIso(item.watchedAt) <= (deleted[key] ?: -1L)) continue
            val matches = storage.getAllHistory().filter { it.syncKey() == key }
            // A key without season cannot identify one of two season-reset episodes safely.
            if (matches.size > 1) continue
            val existing = matches.singleOrNull()
            if (existing != null) {
                if (item.watchedAtMillis() > existing.watchedAt) storage.addHistory(existing.copy(
                    lastPosition = item.positionMs,
                    totalDuration = if (item.durationMs > 0) item.durationMs else existing.totalDuration,
                    watchedAt = item.watchedAtMillis(),
                ))
            } else {
                // Phone-only rows carry no resolvable TV episode reference; never invent one.
                val session = item.extra?.get(EXTRA_SESSION) as? String ?: continue
                val restored = item.toEntity(session) ?: continue
                if (restored.syncKey() == key && storage.getWatchHistoryById(session) == null)
                    storage.addHistory(restored)
            }
        }
        val bounded = deleted.entries.sortedByDescending { it.value }.take(300).associate { it.toPair() }
        prefs.edit().putString(key(KEY_REMOTE_DELETED, namespace), gson.toJson(bounded)).apply()
    }

    private suspend fun findByKey(key: String, storage: WatchHistoryRepository): WatchHistoryEntity? =
        storage.getAllHistory().filter { it.syncKey() == HistoryIdentity.canonicalKey(key) }.singleOrNull()

    private fun readDeleted(namespace: String?): Map<String, Long> {
        val raw = prefs.getString(key(KEY_REMOTE_DELETED, namespace), null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Long>>() {}.type
        return runCatching { gson.fromJson<Map<String, Long>>(raw, type) }.getOrNull().orEmpty()
    }

    private fun readTombstones(namespace: String?): Map<String, String> {
        val raw = prefs.getString(key(KEY_TOMBSTONES, namespace), null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, tombstoneType) }
            .getOrNull() ?: emptyMap()
    }

    private fun writeTombstones(all: Map<String, String>, namespace: String?) {
        prefs.edit().putString(key(KEY_TOMBSTONES, namespace), gson.toJson(all)).apply()
    }

    private fun WatchHistoryEntity.toSyncItem() = HistorySyncItem(
        provider = syncContent().provider,
        contentId = syncContent().content,
        contentUrl = if (syncContent().migrated) syncContent().content else videoUrl,
        title = mediaName.ifBlank { title },
        thumbnail = image,
        isSerial = isSeries,
        episodeIndex = epIndex.takeIf { it >= 0 },
        episodeNumber = absoluteEpisode(),
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

    /**
     * 1-based episode number across the whole series. [WatchHistoryEntity.epIndex] is relative to
     * its 100-episode part, so episode 1 and episode 101 shared a key and overwrote each other.
     * Part 1 still yields exactly the old number, so existing keys do not change.
     */
    private fun WatchHistoryEntity.absoluteEpisode(): Int? {
        if (epIndex < 0) return null
        val part = (page ?: 1).coerceAtLeast(1)
        return (part - 1) * ExtensionParser.EPISODE_PAGE_SIZE + epIndex + 1
    }

    private fun WatchHistoryEntity.syncContent() = HistoryIdentity.content(syncProvider(),
        categoryid?.takeIf { it.isNotBlank() } ?: videoUrl)

    private fun WatchHistoryEntity.syncKey(): String? = HistoryIdentity.key(syncProvider(),
        categoryid?.takeIf { it.isNotBlank() } ?: videoUrl, isSeries, absoluteEpisode())

    private fun WatchHistoryEntity.syncKeys(): Set<String> = setOfNotNull(syncKey(),
        HistoryIdentity.legacyKey(syncProvider(), categoryid?.takeIf { it.isNotBlank() } ?: videoUrl,
            isSeries, absoluteEpisode()))

    private companion object {
        const val FILE = "sozo_history_sync"
        const val KEY_CURSOR = "cursor"
        const val KEY_PUSHED_AT = "pushed_at"
        const val KEY_TOMBSTONES = "tombstones"
        const val EXTRA_SESSION = "session"
        const val KEY_IDENTITY_VERSION = "identity_version"
        const val KEY_REMOTE_DELETED = "remote_deleted"
        const val IDENTITY_VERSION = 1

        private fun formatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun nowIso(): String = formatter().format(Date())
        fun isoOf(millis: Long): String = formatter().format(Date(millis))
        fun parseIso(value: String?): Long =
            value?.let { runCatching { formatter().parse(it)?.time }.getOrNull() } ?: 0L
    }
}

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

/**
 * Keeps this box's watch history in step with the signed-in account.
 *
 * WHAT ACTUALLY SYNCS, and why it is not simply "everything":
 *
 * A TV row is a Room record keyed by `session` - a provider-specific handle for
 * one episode stream. The phone has no such handle, so a phone row cannot be
 * turned into a playable TV row. Pretending otherwise would fill the History
 * screen with entries that open nothing.
 *
 * So the split is honest:
 *   - TV -> TV     full rows. The whole Room record rides along in `extra` and
 *                  comes back intact, so a second box shows real, playable history.
 *   - TV <-> phone resume position on content both can name. If the phone got
 *                  further into an episode this box already knows about, this
 *                  box picks up there. A phone-only title is remembered on the
 *                  server for the phone, and simply not shown here.
 *
 * Deletes travel as tombstones. Room's DAO removes rows outright, so a delete
 * leaves no trace to upload - without recording one here, the next sync would
 * see the server's copy as "new" and put the row straight back.
 */
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

    /** True once the account has ever synced here; drives "sync is on" in the UI. */
    val hasSynced: Boolean get() = prefs.getString(KEY_CURSOR, null) != null

    // ─── public API ──────────────────────────────────────────────────────────

    /**
     * Push local changes, pull everyone else's, write the result to Room.
     *
     * Serialised by [mutex]: playback-end and screen-open can both land on this
     * at once, and two interleaved runs would push the same rows twice and race
     * over the cursor.
     *
     * Returns false when nothing could be done (signed out, or offline). Callers
     * treat that as "try again later", never as an error worth showing.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val cursor = prefs.getString(KEY_CURSOR, null)
            val tombstones = readTombstones()

            val outgoing = buildList {
                // Only rows touched since the last successful push. A full
                // upload every time would rewrite the whole server list and
                // wake every other device for nothing.
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
                        // Stamped from the rows we just sent, not from "now":
                        // a row written while the request was in flight must
                        // still be picked up next time.
                        .putLong(KEY_PUSHED_AT, outgoing.maxOfOrNull { parseIso(it.watchedAt) } ?: prefs.getLong(KEY_PUSHED_AT, 0L))
                        .remove(KEY_TOMBSTONES)
                        .apply()
                    true
                }
                // 401 means signed out. Keeping the tombstones is deliberate:
                // they must survive until a sync actually accepts them.
                else -> false
            }
        }
    }

    /**
     * Fire-and-forget sync on the repository's own scope.
     *
     * For callers that are about to tear themselves down - a sign-in hand-off
     * relaunches the activity in the same handler, and a coroutine on its scope
     * would be cancelled before the request left the box.
     */
    fun syncAsync(): Job = scope.launch { runCatching { sync() } }

    /** Records a delete so it can travel. Call BEFORE removing the Room row. */
    suspend fun rememberDeleted(entity: WatchHistoryEntity) {
        val key = entity.syncKey() ?: return
        mutex.withLock { writeTombstone(key) }
    }

    /** Records a full clear. Call BEFORE clearing Room. */
    suspend fun rememberClearedAll() {
        val keys = local.getAllHistory().mapNotNull { it.syncKey() }
        if (keys.isEmpty()) return
        mutex.withLock {
            val now = nowIso()
            writeTombstones(readTombstones() + keys.associateWith { now })
        }
    }

    /** Sign-out must not leave one account's cursor pointing at another's history. */
    fun clear() = prefs.edit().clear().apply()

    // ─── applying the server's answer ────────────────────────────────────────

    private suspend fun applyRemote(items: List<HistorySyncItem>) {
        for (item in items) {
            val key = item.key ?: continue
            val session = item.extra?.get(EXTRA_SESSION) as? String

            if (item.deletedAt != null) {
                // Deleted elsewhere. Only rows this box can name are removable.
                session?.let { local.removeHistory(it) }
                continue
            }

            val existing = session?.let { local.getWatchHistoryById(it) }
                ?: findByKey(key)

            when {
                // A row this box already has: take the newer position only.
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
                // A full TV row from another box: rebuild it verbatim.
                item.extra != null && session != null -> {
                    item.toEntity(session)?.let { local.addHistory(it) }
                }
                // Phone-only row: nothing playable to build here. It stays on
                // the server for the phone, and is not faked into this list.
                else -> Unit
            }
        }
    }

    /** Room is keyed by session, so a cross-device match needs a scan. History is capped small. */
    private suspend fun findByKey(key: String): WatchHistoryEntity? =
        local.getAllHistory().firstOrNull { it.syncKey() == key }

    // ─── tombstones ──────────────────────────────────────────────────────────

    private fun readTombstones(): Map<String, String> {
        val raw = prefs.getString(KEY_TOMBSTONES, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, tombstoneType) }
            .getOrNull() ?: emptyMap()
    }

    private fun writeTombstone(key: String) = writeTombstones(readTombstones() + (key to nowIso()))

    private fun writeTombstones(all: Map<String, String>) {
        prefs.edit().putString(KEY_TOMBSTONES, gson.toJson(all)).apply()
    }

    // ─── mapping ─────────────────────────────────────────────────────────────

    private fun WatchHistoryEntity.toSyncItem() = HistorySyncItem(
        provider = source.ifBlank { currentSourceName },
        contentId = categoryid,
        contentUrl = videoUrl,
        title = mediaName.ifBlank { title },
        thumbnail = image,
        isSerial = isSeries,
        episodeIndex = epIndex.takeIf { it >= 0 },
        positionMs = lastPosition,
        durationMs = totalDuration,
        watchedAt = isoOf(watchedAt),
        // The Room row rides along whole so another box can rebuild it exactly.
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
        )
    }

    private fun HistorySyncItem.watchedAtMillis(): Long =
        parseIso(watchedAt).takeIf { it > 0 } ?: System.currentTimeMillis()

    /**
     * Mirrors the server's buildHistoryKey EXACTLY. Drifting from it splits one
     * episode into two rows that never reconcile, so the two must change together.
     */
    private fun WatchHistoryEntity.syncKey(): String? {
        val provider = source.ifBlank { currentSourceName }.trim()
        val base = (categoryid?.takeIf { it.isNotBlank() } ?: videoUrl).trim()
        if (base.isEmpty()) return null
        val head = "$provider|$base"
        if (!isSeries) return head
        return if (epIndex < 0) head else "$head|e$epIndex"
    }

    private companion object {
        const val FILE = "sozo_history_sync"
        const val KEY_CURSOR = "cursor"
        const val KEY_PUSHED_AT = "pushed_at"
        const val KEY_TOMBSTONES = "tombstones"
        const val EXTRA_SESSION = "session"

        /** UTC, milliseconds - the format the server's `new Date(...)` parses without surprises. */
        private fun formatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun nowIso(): String = formatter().format(Date())
        fun isoOf(millis: Long): String = formatter().format(Date(millis))
        fun parseIso(value: String?): Long =
            value?.let { runCatching { formatter().parse(it)?.time }.getOrNull() } ?: 0L
    }
}

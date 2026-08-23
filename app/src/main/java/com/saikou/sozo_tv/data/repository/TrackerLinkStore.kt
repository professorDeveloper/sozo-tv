package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Remembers which entry on a tracker a locally-watched title corresponds to.
 *
 * The whole reason tracking works for sources a tracker has never heard of: an
 * extension hands us a title string and an id, the tracker wants a numeric one,
 * and nothing in either can derive the other. So the association is stored once
 * and reused for every episode after that.
 *
 * Subclassed per tracker rather than shared, so each one's links die with that
 * tracker's connection. A single map would take the other tracker's
 * associations down with it.
 */
open class TrackerLinkStore(
    context: Context,
    private val gson: Gson = Gson(),
    prefsFile: String,
) {

    private val prefs = context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
    private val mapType = object : TypeToken<Map<String, TrackerTitleLink>>() {}.type
    private val tombstoneType = object : TypeToken<Map<String, Long>>() {}.type

    private fun read(): Map<String, TrackerTitleLink> {
        val raw = prefs.getString(KEY_LINKS, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, TrackerTitleLink>>(raw, mapType) }
            .getOrNull() ?: emptyMap()
    }

    private fun write(all: Map<String, TrackerTitleLink>) {
        prefs.edit().putString(KEY_LINKS, gson.toJson(all)).apply()
    }

    private fun readTombstones(): Map<String, Long> {
        val raw = prefs.getString(KEY_TOMBSTONES, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, Long>>(raw, tombstoneType) }
            .getOrNull() ?: emptyMap()
    }

    private fun writeTombstones(all: Map<String, Long>) {
        prefs.edit().putString(KEY_TOMBSTONES, gson.toJson(all)).apply()
    }

    fun pendingChanges(): List<Map<String, Any?>> = buildList {
        read().forEach { (key, link) ->
            add(
                mapOf(
                    "key" to key,
                    "provider" to link.provider,
                    "contentId" to link.contentId,
                    "mediaId" to link.mediaId,
                    "title" to link.title,
                    "coverImage" to link.coverImage,
                    "totalEpisodes" to link.totalEpisodes,
                    "auto" to link.auto,
                    "updatedAt" to isoOf(link.linkedAt),
                )
            )
        }
        readTombstones().forEach { (key, at) ->
            add(mapOf("key" to key, "deletedAt" to isoOf(at), "updatedAt" to isoOf(at)))
        }
    }

    fun applyRemote(items: List<RemoteTitleLink>) {
        val map = LinkedHashMap<String, TrackerTitleLink>()
        for (item in items) {
            val key = item.key?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) continue
            if (item.deletedAt != null) continue
            val mediaId = item.mediaId ?: continue
            if (mediaId <= 0) continue
            map[key] = TrackerTitleLink(
                provider = item.provider.orEmpty(),
                contentId = item.contentId.orEmpty(),
                mediaId = mediaId,
                title = item.title.orEmpty(),
                coverImage = item.coverImage,
                totalEpisodes = item.totalEpisodes,
                linkedAt = parseIso(item.updatedAt),
                auto = item.auto ?: false,
            )
        }
        write(map)
        writeTombstones(emptyMap())
    }

    fun get(provider: String, contentId: String): TrackerTitleLink? =
        contentId.takeIf { it.isNotBlank() }?.let { read()[keyFor(provider, it)] }

    fun mediaIdFor(provider: String, contentId: String): Int? =
        get(provider, contentId)?.mediaId?.takeIf { it > 0 }

    fun save(link: TrackerTitleLink) {
        if (link.contentId.isBlank() || link.mediaId <= 0) return
        val key = keyFor(link.provider, link.contentId)
        write(read() + (key to link))
        writeTombstones(readTombstones() - key)
    }

    fun remove(provider: String, contentId: String) {
        val key = keyFor(provider, contentId)
        write(read() - key)
        writeTombstones(readTombstones() + (key to System.currentTimeMillis()))
    }

    fun all(): List<TrackerTitleLink> = read().values.sortedByDescending { it.linkedAt }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LINKS = "links"
        private const val KEY_TOMBSTONES = "tombstones"

        private fun formatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun isoOf(millis: Long): String = formatter().format(Date(millis))

        fun parseIso(value: String?): Long =
            value?.let { runCatching { formatter().parse(it)?.time }.getOrNull() }
                ?: System.currentTimeMillis()

        fun keyFor(provider: String, contentId: String): String =
            "${provider.trim().lowercase()}|${contentId.trim().lowercase()}"
    }
}

data class TrackerTitleLink(
    val provider: String,
    val contentId: String,
    val mediaId: Int,
    val title: String,
    val coverImage: String? = null,
    val totalEpisodes: Int? = null,
    val linkedAt: Long = 0,
    val auto: Boolean = false,
)

data class RemoteTitleLink(
    val key: String? = null,
    val provider: String? = null,
    val contentId: String? = null,
    val mediaId: Int? = null,
    val title: String? = null,
    val coverImage: String? = null,
    val totalEpisodes: Int? = null,
    val auto: Boolean? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
)

package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AnilistLinkStore(context: Context, private val gson: Gson = Gson()) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mapType = object : TypeToken<Map<String, AnilistTitleLink>>() {}.type
    private val tombstoneType = object : TypeToken<Map<String, Long>>() {}.type

    private fun read(): Map<String, AnilistTitleLink> {
        val raw = prefs.getString(KEY_LINKS, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, AnilistTitleLink>>(raw, mapType) }
            .getOrNull() ?: emptyMap()
    }

    private fun write(all: Map<String, AnilistTitleLink>) {
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
        val map = LinkedHashMap<String, AnilistTitleLink>()
        for (item in items) {
            val key = item.key?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) continue
            if (item.deletedAt != null) continue
            val mediaId = item.mediaId ?: continue
            if (mediaId <= 0) continue
            map[key] = AnilistTitleLink(
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

    fun get(provider: String, contentId: String): AnilistTitleLink? =
        contentId.takeIf { it.isNotBlank() }?.let { read()[keyFor(provider, it)] }

    fun mediaIdFor(provider: String, contentId: String): Int? =
        get(provider, contentId)?.mediaId?.takeIf { it > 0 }

    fun save(link: AnilistTitleLink) {
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

    fun all(): List<AnilistTitleLink> = read().values.sortedByDescending { it.linkedAt }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "sozo_anilist_links"
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

data class AnilistTitleLink(
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

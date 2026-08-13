package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.lists.UserListItem
import com.saikou.sozo_tv.data.remote.lists.UserListKind
import com.saikou.sozo_tv.data.remote.lists.UserListsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache-first, server-authoritative — the same contract the Flutter client uses,
 * so the two apps agree about what the user sees offline.
 *
 * The cache is a JSON blob in SharedPreferences rather than a Room table on
 * purpose: adding entities to `AppDatabase` means a schema version bump, and a
 * missing migration is a crash-on-launch for every existing install. These lists
 * are small, bounded server-side at 500, and only ever read whole.
 */
class UserListsRepository(
    context: Context,
    private val client: UserListsClient,
    private val gson: Gson = Gson(),
) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val itemsType = object : TypeToken<List<UserListItem>>() {}.type

    fun cached(kind: UserListKind): List<UserListItem> {
        val raw = prefs.getString(kind.slug, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<UserListItem>>(raw, itemsType) }
            .getOrNull()
            ?.filter { !it.contentUrl.isNullOrBlank() }
            ?: emptyList()
    }

    private fun store(kind: UserListKind, items: List<UserListItem>) {
        prefs.edit().putString(kind.slug, gson.toJson(items)).apply()
    }

    fun contains(kind: UserListKind, contentUrl: String): Boolean =
        cached(kind).any { it.contentUrl == contentUrl }

    /**
     * Fetch and replace the cache. A failed fetch keeps the last good copy —
     * a curated list going blank because the network blinked is worse than a
     * slightly stale one.
     */
    suspend fun refresh(kind: UserListKind): List<UserListItem> = withContext(Dispatchers.IO) {
        when (val r = client.get(kind)) {
            is ApiResult.Ok -> r.body.also { store(kind, it) }
            else -> cached(kind)
        }
    }

    /** Optimistic: the cache (and therefore the button) updates before the call. */
    suspend fun add(kind: UserListKind, item: UserListItem) = withContext(Dispatchers.IO) {
        val url = item.contentUrl ?: return@withContext
        store(kind, listOf(item) + cached(kind).filterNot { it.contentUrl == url })
        // Mirror the server's rule locally: Watched evicts from Watch Later,
        // or that tab keeps showing the title until the next refresh.
        if (kind == UserListKind.WATCHED) {
            store(
                UserListKind.WATCH_LATER,
                cached(UserListKind.WATCH_LATER).filterNot { it.contentUrl == url },
            )
        }
        client.add(kind, item)
        Unit
    }

    suspend fun remove(kind: UserListKind, contentUrl: String) = withContext(Dispatchers.IO) {
        store(kind, cached(kind).filterNot { it.contentUrl == contentUrl })
        client.remove(kind, contentUrl)
        Unit
    }

    /** Sign-out must not leave another account's lists on the box. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "sozo_user_lists"
    }
}

package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.lists.UserListItem
import com.saikou.sozo_tv.data.remote.lists.UserListKind
import com.saikou.sozo_tv.data.remote.lists.UserListsClient
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Cache-first, server-authoritative — the same contract the Flutter client uses,
 * so the two apps agree about what the user sees offline.
 *
 * The cache is a JSON blob in SharedPreferences rather than a Room table on
 * purpose: adding entities to `AppDatabase` means a schema version bump, and a
 * missing migration is a crash-on-launch for every existing install. These lists
 * are small, bounded server-side at 500, and only ever read whole.
 */
class UserListsRepository internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val client: UserListsClient,
    private val profileStore: ActiveProfileStore,
    private val gson: Gson = Gson(),
) {

    constructor(context: Context, client: UserListsClient, profileStore: ActiveProfileStore, gson: Gson = Gson()) :
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), client, profileStore, gson)
    private val mutations = Mutex()
    private val cacheGuard = Any()
    private val accountGeneration = AtomicLong()
    private val itemsType = object : TypeToken<List<UserListItem>>() {}.type

    fun cached(kind: UserListKind): List<UserListItem> = cached(kind, profileStore.namespace())

    private fun cached(kind: UserListKind, namespace: String?): List<UserListItem> {
        val raw = prefs.getString(key(kind, namespace), null) ?: return emptyList()
        return runCatching { gson.fromJson<List<UserListItem>>(raw, itemsType) }
            .getOrNull()
            ?.filter { !it.contentUrl.isNullOrBlank() }
            ?: emptyList()
    }

    private fun store(kind: UserListKind, items: List<UserListItem>, namespace: String?) {
        prefs.edit().putString(key(kind, namespace), gson.toJson(items)).apply()
    }

    private fun key(kind: UserListKind, namespace: String?): String =
        namespace?.let { "profile_${it}__${kind.slug}" } ?: kind.slug

    fun contains(kind: UserListKind, contentUrl: String): Boolean =
        cached(kind).any { it.contentUrl == contentUrl }

    /**
     * Fetch and replace the cache. A failed fetch keeps the last good copy —
     * a curated list going blank because the network blinked is worse than a
     * slightly stale one.
     */
    suspend fun refresh(kind: UserListKind): List<UserListItem> {
        val captured = profileStore.snapshot()
        val generation = accountGeneration.get()
        return mutations.withLock { withContext(Dispatchers.IO) {
            val profile = captured.profile
            val namespace = profile?.localNamespace
            if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext emptyList()
            val result = client.get(kind, com.saikou.sozo_tv.data.remote.profiles.ProfileRequestScope(profile?.id))
            synchronized(cacheGuard) {
                if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext emptyList()
                when (val r = result) {
                    is ApiResult.Ok -> r.body.also { store(kind, it, namespace) }
                    else -> cached(kind, namespace)
                }
            }
        } }
    }

    /** Optimistic cache updates are serialized with refresh and bound to the profile at the tap. */
    suspend fun add(kind: UserListKind, item: UserListItem) {
        val captured = profileStore.snapshot()
        val generation = accountGeneration.get()
        mutations.withLock { withContext(Dispatchers.IO) {
            val profile = captured.profile
            val namespace = profile?.localNamespace
            val url = item.contentUrl ?: return@withContext
            synchronized(cacheGuard) {
                if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext
                store(kind, listOf(item) + cached(kind, namespace).filterNot { it.contentUrl == url }, namespace)
                if (kind == UserListKind.WATCHED) {
                    store(UserListKind.WATCH_LATER,
                        cached(UserListKind.WATCH_LATER, namespace).filterNot { it.contentUrl == url }, namespace)
                }
            }
            if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext
            client.add(kind, item, com.saikou.sozo_tv.data.remote.profiles.ProfileRequestScope(profile?.id))
        } }
    }

    suspend fun remove(kind: UserListKind, contentUrl: String) {
        val captured = profileStore.snapshot()
        val generation = accountGeneration.get()
        mutations.withLock { withContext(Dispatchers.IO) {
            val profile = captured.profile
            val namespace = profile?.localNamespace
            synchronized(cacheGuard) {
                if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext
                store(kind, cached(kind, namespace).filterNot { it.contentUrl == contentUrl }, namespace)
            }
            if (captured.revision != profileStore.revision || generation != accountGeneration.get()) return@withContext
            client.remove(kind, contentUrl, com.saikou.sozo_tv.data.remote.profiles.ProfileRequestScope(profile?.id))
        } }
    }

    /** Sign-out must not leave another account's lists on the box. */
    fun clear() = synchronized(cacheGuard) {
        accountGeneration.incrementAndGet()
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "sozo_user_lists"
    }
}

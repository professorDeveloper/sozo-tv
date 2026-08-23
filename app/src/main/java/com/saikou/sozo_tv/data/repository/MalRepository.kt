package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.mal.MalApiClient
import com.saikou.sozo_tv.data.remote.mal.MalConnection
import com.saikou.sozo_tv.data.remote.mal.MalEntryState
import com.saikou.sozo_tv.data.remote.mal.MalException
import com.saikou.sozo_tv.data.remote.mal.MalLinkClient
import com.saikou.sozo_tv.data.remote.mal.MalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The MyAnimeList connection, as this device sees it.
 *
 * Deliberately thinner than [AnilistRepository]: there is no profile to enrich
 * and no library screen to feed, because the TV's job here is only to keep the
 * account's progress up to date while somebody watches.
 *
 * Refreshing is also what renews the token — the backend refreshes on read, so
 * a TV that has been off for a month gets a working token back rather than an
 * expired one.
 */
class MalRepository(
    private val linkClient: MalLinkClient,
    private val api: MalApiClient,
    private val links: MalLinkStore,
) {

    private val _connection = MutableStateFlow<MalConnection>(MalConnection.Unknown)
    val connection: StateFlow<MalConnection> = _connection.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var token: String? = null

    val accessToken: String? get() = token
    val isConnected: Boolean get() = !token.isNullOrBlank()

    suspend fun refresh(): MalConnection = refreshMutex.withLock {
        when (val result = linkClient.get()) {
            is ApiResult.Ok -> {
                val link = result.body
                token = link?.accessToken
                val state = link?.viewer()
                    ?.let { MalConnection.Connected(it) }
                    ?: MalConnection.NotConnected
                _connection.value = state
                if (state is MalConnection.Connected) syncLinks()
                _connection.value
            }

            is ApiResult.Http -> {
                if (result.code == 401) {
                    token = null
                    _connection.value = MalConnection.NotConnected
                }
                _connection.value
            }

            // A network failure is not a disconnection: keep whatever was known.
            is ApiResult.Network -> _connection.value
        }
    }

    suspend fun syncLinks() {
        when (val result = linkClient.syncLinks(links.pendingChanges())) {
            is ApiResult.Ok -> links.applyRemote(result.body)
            is ApiResult.Http -> Log.w(TAG, "link sync HTTP ${result.code}: ${result.message}")
            is ApiResult.Network -> Log.w(TAG, "link sync failed: ${result.cause.message}")
        }
    }

    suspend fun disconnect(): Boolean {
        val ok = linkClient.unlink() is ApiResult.Ok
        token = null
        _connection.value = MalConnection.NotConnected
        links.clear()
        return ok
    }

    fun forgetLocal() {
        token = null
        _connection.value = MalConnection.Unknown
        links.clear()
    }

    suspend fun entryState(animeId: Int): MalEntryState? {
        val token = token ?: return null
        return api.entryState(token, animeId)
    }

    suspend fun saveProgress(animeId: Int, episodes: Int, status: String?): MalEntryState {
        val token = token ?: throw MalException("MyAnimeList is not connected")
        return api.updateProgress(token, animeId, episodes, status)
    }

    /**
     * The status to send alongside a progress write, or null to leave it alone.
     *
     * The rewatch case is the one that bites: MAL models a rewatch as a flag on
     * a `completed` entry rather than as a status, so sending any status during
     * one takes the entry out of `completed` and ends the rewatch — rewriting a
     * list the user curated, with nothing on screen to say so.
     */
    fun statusFor(
        current: String?,
        isRewatching: Boolean,
        episode: Int,
        total: Int?,
    ): String? = when {
        isRewatching -> null
        total != null && total > 0 && episode >= total -> MalStatus.COMPLETED
        current == MalStatus.WATCHING -> null
        else -> MalStatus.WATCHING
    }
}

private const val TAG = "MalRepository"

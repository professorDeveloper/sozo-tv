package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.saikou.sozo_tv.data.remote.anilist.AnilistEntryState
import com.saikou.sozo_tv.data.remote.anilist.AnilistException
import com.saikou.sozo_tv.data.remote.anilist.AnilistGraphQlClient
import com.saikou.sozo_tv.data.remote.anilist.AnilistLinkClient
import com.saikou.sozo_tv.data.remote.anilist.AnilistListEntry
import com.saikou.sozo_tv.data.remote.anilist.AnilistMedia
import com.saikou.sozo_tv.data.remote.anilist.AnilistSaveResult
import com.saikou.sozo_tv.data.remote.anilist.AnilistStatus
import com.saikou.sozo_tv.data.remote.anilist.AnilistViewer
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnilistRepository(
    private val linkClient: AnilistLinkClient,
    private val api: AnilistGraphQlClient,
    private val links: AnilistLinkStore,
) {

    private val _connection = MutableStateFlow<AnilistConnection>(AnilistConnection.Unknown)
    val connection: StateFlow<AnilistConnection> = _connection.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var token: String? = null

    val accessToken: String? get() = token
    val isConnected: Boolean get() = !token.isNullOrBlank()

    suspend fun refresh(): AnilistConnection = refreshMutex.withLock {
        when (val result = linkClient.get()) {
            is ApiResult.Ok -> {
                val link = result.body
                token = link?.accessToken
                val state = link?.viewer()
                    ?.let { AnilistConnection.Connected(it) }
                    ?: AnilistConnection.NotConnected
                _connection.value = state
                if (state is AnilistConnection.Connected) {
                    syncLinks()
                    enrichViewer()
                }
                _connection.value
            }
            is ApiResult.Http -> {
                if (result.code == 401) {
                    token = null
                    _connection.value = AnilistConnection.NotConnected
                }
                _connection.value
            }
            is ApiResult.Network -> _connection.value
        }
    }

    /**
     * Fills in what the link cannot carry.
     *
     * The backend link stores an id, a name and an avatar — enough to say who is
     * connected and nothing more. The banner and the watch statistics belong to
     * AniList, so they are fetched from AniList with the user's own token once
     * the connection is known to be good.
     *
     * Silent on failure and never destructive: a profile fetch that does not
     * come back leaves the link's own viewer in place, so the screen still says
     * who is connected.
     */
    private suspend fun enrichViewer() {
        val accessToken = token ?: return
        val current = (_connection.value as? AnilistConnection.Connected)?.viewer ?: return
        if (current.hasStats) return
        runCatching { api.viewer(accessToken) }
            .onSuccess { full ->
                if (_connection.value is AnilistConnection.Connected) {
                    _connection.value = AnilistConnection.Connected(full)
                }
            }
    }

    suspend fun syncLinks() {
        when (val result = linkClient.syncLinks(links.pendingChanges())) {
            is ApiResult.Ok -> links.applyRemote(result.body)
            // Logged, not swallowed. This failing silently is how a 404 from a
            // wrong path survived every launch looking like "nothing to sync".
            is ApiResult.Http -> Log.w(TAG, "link sync HTTP ${result.code}: ${result.message}")
            is ApiResult.Network -> Log.w(TAG, "link sync failed: ${result.cause.message}")
        }
    }

    suspend fun disconnect(): Boolean {
        val ok = linkClient.unlink() is ApiResult.Ok
        token = null
        _connection.value = AnilistConnection.NotConnected
        links.clear()
        return ok
    }

    fun forgetLocal() {
        token = null
        _connection.value = AnilistConnection.Unknown
        links.clear()
    }

    suspend fun library(): List<AnilistListEntry> {
        val token = token ?: throw AnilistException("AniList is not connected")
        val viewer = (_connection.value as? AnilistConnection.Connected)?.viewer
            ?: throw AnilistException("Could not identify the AniList account")
        return api.mediaList(token, viewer.id)
    }

    suspend fun entryState(mediaId: Int): AnilistEntryState? {
        val token = token ?: return null
        return api.entryState(token, mediaId)
    }

    suspend fun saveProgress(mediaId: Int, progress: Int, status: String?): AnilistSaveResult {
        val token = token ?: throw AnilistException("AniList is not connected")
        return api.saveProgress(token, mediaId, progress, status)
    }

    suspend fun search(query: String): List<AnilistMedia> = api.searchMedia(query)

    fun statusFor(current: String?, episode: Int, total: Int?): String? = when {
        current == AnilistStatus.REPEATING.value -> null
        total != null && total > 0 && episode >= total -> AnilistStatus.COMPLETED.value
        current == AnilistStatus.CURRENT.value -> null
        else -> AnilistStatus.CURRENT.value
    }
}

private const val TAG = "AnilistRepository"

sealed class AnilistConnection {
    data object Unknown : AnilistConnection()
    data object NotConnected : AnilistConnection()
    data class Connected(val viewer: AnilistViewer) : AnilistConnection()
}

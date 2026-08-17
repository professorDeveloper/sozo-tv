package com.saikou.sozo_tv.data.repository

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

/**
 * Owns the AniList connection on this box.
 *
 * The connection is NOT made here. It is made once on the phone, stored against
 * the Sozo account, and read from the account by [refresh] — which is what
 * spares anyone from typing an AniList password with a d-pad. The consequence
 * worth stating: this repository has no "connect" action at all, only a state
 * that follows the account.
 */
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

    /**
     * Re-reads the link from the account.
     *
     * Serialised so a screen opening and a playback event cannot both refresh at
     * once. Being offline leaves the previous state alone rather than reporting
     * a disconnection that did not happen — a box that loses Wi-Fi for a minute
     * must not stop tracking.
     */
    suspend fun refresh(): AnilistConnection = refreshMutex.withLock {
        when (val result = linkClient.get()) {
            is ApiResult.Ok -> {
                val link = result.body
                token = link?.accessToken
                val state = link?.viewer()
                    ?.let { AnilistConnection.Connected(it) }
                    ?: AnilistConnection.NotConnected
                _connection.value = state
                state
            }
            // 401 means signed out of Sozo, which really is "no connection here".
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

    /** Removes the link from the ACCOUNT — every device loses it, not just this one. */
    suspend fun disconnect(): Boolean {
        val ok = linkClient.unlink() is ApiResult.Ok
        token = null
        _connection.value = AnilistConnection.NotConnected
        links.clear()
        return ok
    }

    /** Sign-out. Drops this box's copy without touching the account's link. */
    fun forgetLocal() {
        token = null
        _connection.value = AnilistConnection.Unknown
        links.clear()
    }

    /** The viewer's library. Throws when not connected, because a caller must not guess. */
    suspend fun library(): List<AnilistListEntry> {
        val token = token ?: throw AnilistException("AniList ulanmagan")
        val viewer = (_connection.value as? AnilistConnection.Connected)?.viewer
            ?: throw AnilistException("AniList hisobi aniqlanmadi")
        return api.mediaList(token, viewer.id)
    }

    suspend fun entryState(mediaId: Int): AnilistEntryState? {
        val token = token ?: return null
        return api.entryState(token, mediaId)
    }

    suspend fun saveProgress(mediaId: Int, progress: Int, status: String?): AnilistSaveResult {
        val token = token ?: throw AnilistException("AniList ulanmagan")
        return api.saveProgress(token, mediaId, progress, status)
    }

    suspend fun search(query: String): List<AnilistMedia> = api.searchMedia(query)

    /**
     * The status to send alongside a progress write.
     *
     * Returns null — "leave it alone" — whenever the existing status already
     * describes active viewing, so a rewatch stays REPEATING instead of being
     * demoted to CURRENT.
     */
    fun statusFor(current: String?, episode: Int, total: Int?): String? = when {
        current == AnilistStatus.REPEATING.value -> null
        total != null && total > 0 && episode >= total -> AnilistStatus.COMPLETED.value
        current == AnilistStatus.CURRENT.value -> null
        else -> AnilistStatus.CURRENT.value
    }
}

/**
 * Three states, not two.
 *
 * [Unknown] exists because "we have not asked the account yet" and "the account
 * has no AniList" look identical to a boolean, and showing "not connected" during
 * the first request is a claim rather than a blank.
 */
sealed class AnilistConnection {
    data object Unknown : AnilistConnection()
    data object NotConnected : AnilistConnection()
    data class Connected(val viewer: AnilistViewer) : AnilistConnection()
}

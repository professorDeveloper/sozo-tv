package com.saikou.sozo_tv.data.remote.mal

/** The MyAnimeList link as the backend hands it back. */
data class MalLink(
    val userId: Int? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String? = null,
    val expiresAt: String? = null,
    val linkedAt: String? = null,
) {
    fun viewer(): MalViewer? {
        val id = userId ?: return null
        return MalViewer(id = id, name = name.orEmpty(), avatarUrl = avatarUrl)
    }
}

data class MalViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
)

/**
 * Where the account currently stands on one anime.
 *
 * [isRewatching] is read and never written back: MAL models a rewatch as a flag
 * on a `completed` entry rather than as a status, so sending any status during
 * one knocks the entry out of `completed` and ends the rewatch.
 */
data class MalEntryState(
    val watchedEpisodes: Int,
    val status: String? = null,
    val totalEpisodes: Int? = null,
    val isRewatching: Boolean = false,
)

/** The statuses MAL accepts on a write. There is deliberately no "rewatching". */
object MalStatus {
    const val WATCHING = "watching"
    const val COMPLETED = "completed"
    const val ON_HOLD = "on_hold"
    const val DROPPED = "dropped"
    const val PLAN_TO_WATCH = "plan_to_watch"
}

/** Connection state, mirroring AnilistConnection so the UI can treat them alike. */
sealed interface MalConnection {
    data object Unknown : MalConnection
    data object NotConnected : MalConnection
    data class Connected(val viewer: MalViewer) : MalConnection
}

class MalException(message: String, cause: Throwable? = null) : Exception(message, cause)

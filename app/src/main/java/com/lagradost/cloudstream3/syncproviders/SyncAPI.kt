package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.utils.UiText

/**
 * Stand-ins for CloudStream's account/sync layer. See [com.lagradost.cloudstream3.utils.Event]'s
 * file for why these classes exist.
 *
 * Every one of them reports "nobody is signed in". That is not a placeholder waiting to be
 * filled — it is the intended answer. This app has its own AniList and MyAnimeList integration
 * under `data/remote/anilist` and `data/remote/mal`, with its own tokens, its own list state and
 * its own progress writes. A plugin syncing through a second, invisible account would put a
 * viewer's list in two places that disagree, and the viewer would have no way to tell which one
 * the app was reading.
 *
 * So a plugin that offers "sync with AniList" finds no session and does nothing, while
 * everything it does for playback keeps working. `SyncIdName` is deliberately absent: the
 * `library` artifact already ships it, and a second copy is a duplicate class at dex time.
 */

/** A signed-in account. Never produced here. */
data class AuthUser(
    val name: String? = null,
    val id: Int? = null,
)

/**
 * What a sync service implements upstream. Declared with no members because plugins only name it
 * — as the type [SyncRepo] is constructed from — and never call through it.
 */
interface SyncAPI {
    /** One of the viewer's lists — "Watching", "Completed", … */
    data class LibraryList(
        val name: UiText,
        val items: List<Any> = emptyList(),
    )

    /** Every list, which is how a plugin walks somebody's library. */
    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList> = emptyList(),
    )
}

/** Reads a service's library on behalf of a plugin. */
class SyncRepo(val api: SyncAPI) {
    /**
     * Always null, which is what stops a plugin's sync path before it starts: the code that
     * would walk [SyncAPI.LibraryMetadata] sits behind this check, so the lists stay untouched
     * rather than being answered wrongly.
     */
    fun authUser(): AuthUser? = null
}

/** CloudStream's account registry. */
class AccountManager {
    companion object {
        /**
         * A live object, not null: plugins chain straight off it (`AccountManager.aniListApi`
         * passed into a [SyncRepo]) with no null check, so returning null here would trade a
         * no-op for a NullPointerException inside the plugin.
         */
        val aniListApi = AniListApi()
    }
}

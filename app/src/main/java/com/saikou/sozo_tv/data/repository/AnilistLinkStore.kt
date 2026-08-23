package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson

/**
 * The AniList half of the title map.
 *
 * Everything that decides anything lives in [TrackerLinkStore]; this names the
 * preferences file AniList owns. The name is unchanged from before the split —
 * changing it would orphan every link already on a TV.
 */
class AnilistLinkStore(context: Context, gson: Gson = Gson()) :
    TrackerLinkStore(context, gson, FILE) {

    companion object {
        private const val FILE = "sozo_anilist_links"

        /** Kept so existing callers keep compiling; the rule itself is shared. */
        fun keyFor(provider: String, contentId: String): String =
            TrackerLinkStore.keyFor(provider, contentId)
    }
}

/** The row shape, shared by every tracker. */
typealias AnilistTitleLink = TrackerTitleLink

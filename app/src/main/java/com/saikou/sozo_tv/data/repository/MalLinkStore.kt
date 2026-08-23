package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson

/**
 * The MyAnimeList half of the title map.
 *
 * Its own file rather than a second id on the AniList row: disconnecting one
 * tracker clears that tracker's map, and a shared row would take the other's
 * associations with it.
 *
 * Rows here are written automatically rather than chosen by hand — see
 * [MalTracker] for where the id comes from.
 */
class MalLinkStore(context: Context, gson: Gson = Gson()) :
    TrackerLinkStore(context, gson, FILE) {

    companion object {
        private const val FILE = "sozo_mal_links"

        fun keyFor(provider: String, contentId: String): String =
            TrackerLinkStore.keyFor(provider, contentId)
    }
}

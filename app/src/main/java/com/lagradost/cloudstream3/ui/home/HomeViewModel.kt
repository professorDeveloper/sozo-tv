package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.utils.DataStoreHelper

/**
 * A stand-in for CloudStream's home screen model. See [com.lagradost.cloudstream3.utils.Event]'s
 * file for why these classes exist.
 *
 * Ultima reads [getResumeWatching] to build its own "continue watching" row. It returns nothing
 * on purpose: this app's watch history is the Room `watch_history` table, keyed by playback
 * session and emptied by the viewer's own "clear history" action, and CloudStream's
 * `ResumeWatchingResult` — a pair of integer ids into CloudStream's own store — has nowhere to
 * carry that from. What a plugin could do with the rows, it could also do after the viewer
 * cleared them.
 */
class HomeViewModel {
    companion object {
        suspend fun getResumeWatching(): List<DataStoreHelper.ResumeWatchingResult> = emptyList()
    }
}

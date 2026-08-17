package com.saikou.sozo_tv.data.repository

import android.content.Context

/**
 * Which installed sources address their content by AniList id.
 *
 * Some providers report the AniList entry a page corresponds to; most do not.
 * That distinction matters more than it looks:
 *
 *   - a source that reports an id can be tracked EXACTLY. No title
 *     normalisation, no season ambiguity, no chance of filing episodes into the
 *     wrong show — the provider has already told us which entry this is.
 *   - a source that does not falls back to matching by title, which is strict
 *     on purpose and therefore often declines to match at all.
 *
 * The list is OBSERVED, not declared. There is no manifest anywhere saying
 * which extensions support this, and a hardcoded list would be wrong the day
 * after it was written — repos add and update providers constantly. So a
 * provider is recorded the first time it actually reports an id, and the record
 * is what the UI badges.
 *
 * The consequence worth stating: a source appears here only after the user has
 * opened one title on it. That is the honest state — before then, nobody knows.
 */
class AnilistSourceRegistry(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Provider ids (`cs:…` / `an:…`) known to report AniList ids. */
    fun all(): Set<String> = prefs.getStringSet(KEY_PROVIDERS, emptySet()).orEmpty()

    fun supports(providerId: String): Boolean = providerId.trim() in all()

    /**
     * Records that [providerId] reported an AniList id.
     *
     * Idempotent, and cheap enough to call on every detail load — which is what
     * keeps the list current as repos update their providers.
     */
    fun remember(providerId: String) {
        val id = providerId.trim()
        if (id.isEmpty() || id in all()) return
        prefs.edit().putStringSet(KEY_PROVIDERS, all() + id).apply()
    }

    // No remove/clear on purpose. The set is only ever read to decorate
    // providers that are currently installed, so an entry for an extension the
    // user deleted matches nothing and shows nothing. Pruning it would be
    // bookkeeping with no observable effect, and an unused method that claims
    // otherwise is worse than none.

    private companion object {
        const val FILE = "sozo_anilist_sources"
        const val KEY_PROVIDERS = "providers"
    }
}

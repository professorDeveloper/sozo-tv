package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Remembers which AniList title a locally-watched title corresponds to.
 *
 * This mapping is the whole reason tracking works for sources AniList has never
 * heard of. A CloudStream or Aniyomi extension gives us a title string and a
 * content id; AniList wants a numeric media id. Nothing in either can derive the
 * other, so the association is stored once — by the user, or by an exact title
 * match — and reused for every episode after that.
 *
 * Keyed by provider AND content id: the same show carried by two sources is two
 * separate links, because the episode numbering often differs between them.
 */
class AnilistLinkStore(context: Context, private val gson: Gson = Gson()) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mapType = object : TypeToken<Map<String, AnilistTitleLink>>() {}.type

    private fun read(): Map<String, AnilistTitleLink> {
        val raw = prefs.getString(KEY_LINKS, null) ?: return emptyMap()
        // A corrupt value is treated as empty rather than crashing every lookup —
        // this is read on the playback path.
        return runCatching { gson.fromJson<Map<String, AnilistTitleLink>>(raw, mapType) }
            .getOrNull() ?: emptyMap()
    }

    private fun write(all: Map<String, AnilistTitleLink>) {
        prefs.edit().putString(KEY_LINKS, gson.toJson(all)).apply()
    }

    fun get(provider: String, contentId: String): AnilistTitleLink? =
        contentId.takeIf { it.isNotBlank() }?.let { read()[keyFor(provider, it)] }

    fun mediaIdFor(provider: String, contentId: String): Int? =
        get(provider, contentId)?.mediaId?.takeIf { it > 0 }

    fun save(link: AnilistTitleLink) {
        if (link.contentId.isBlank() || link.mediaId <= 0) return
        write(read() + (keyFor(link.provider, link.contentId) to link))
    }

    fun remove(provider: String, contentId: String) {
        write(read() - keyFor(provider, contentId))
    }

    /** Every link, newest first — for a "linked titles" screen. */
    fun all(): List<AnilistTitleLink> = read().values.sortedByDescending { it.linkedAt }

    /**
     * Drops every link.
     *
     * Called on sign-out and on disconnect: these describe what one account
     * watches and where, and leaving them behind would attribute the next
     * person's viewing to a stranger's AniList list.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "sozo_anilist_links"
        private const val KEY_LINKS = "links"

        /**
         * The identity of a local title. Trimmed and lowercased so an id that
         * differs only in case does not create a second, unlinked entry.
         */
        fun keyFor(provider: String, contentId: String): String =
            "${provider.trim().lowercase()}|${contentId.trim().lowercase()}"
    }
}

/** One local title tied to one AniList media id. */
data class AnilistTitleLink(
    val provider: String,
    val contentId: String,
    val mediaId: Int,
    val title: String,
    val coverImage: String? = null,
    val totalEpisodes: Int? = null,
    val linkedAt: Long = 0,
    /**
     * True when the match was made by title rather than chosen by the user.
     * Surfaced in the UI so a wrong automatic guess is visibly a guess.
     */
    val auto: Boolean = false,
)

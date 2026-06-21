package com.saikou.sozo_tv.data.remote.anilist

import com.google.gson.Gson
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager

/**
 * AniList catalog + list management over GraphQL.
 *  - [searchAnime]: catalog search (works logged-out; per-item list status is null without a token).
 *  - [saveListEntry]: `SaveMediaListEntry` mutation — requires the user's AniList token.
 */
class AniListRepository(
    private val client: AniListClient,
    private val gson: Gson,
    private val prefs: PreferenceManager,
) {

    fun isLoggedIn(): Boolean = prefs.getString(AuthPrefKeys.ANILIST_TOKEN).isNotBlank()

    suspend fun searchAnime(query: String): Result<List<AniListMedia>> =
        client.post(AniListQueries.SEARCH_ANIME, mapOf("search" to query, "page" to 1))
            .mapCatching { data ->
                val page = gson.fromJson(data.getAsJsonObject("Page"), AniListPage::class.java)
                page?.media.orEmpty()
            }

    /** AniList MediaListStatus enum values used by [saveListEntry]. */
    suspend fun saveListEntry(mediaId: Int, status: String, progress: Int?): Result<Unit> {
        if (!isLoggedIn()) {
            return Result.failure(IllegalStateException("Log in to AniList (Account) to manage your list."))
        }
        val vars = HashMap<String, Any?>()
        vars["mediaId"] = mediaId
        vars["status"] = status
        if (progress != null) vars["progress"] = progress
        return client.post(AniListQueries.SAVE_ENTRY, vars).map { }
    }
}

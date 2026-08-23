package com.saikou.sozo_tv.data.remote.mal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Talks to MyAnimeList directly with the account's own token.
 *
 * Two details of MAL's API are easy to get wrong and silent when missed: a
 * write is a `PATCH` with FORM encoding (a JSON body is answered with 400), and
 * the current list position is read from the ANIME endpoint via a
 * `my_list_status` field rather than from any list endpoint.
 */
class MalApiClient(private val okHttpClient: OkHttpClient) {

    /**
     * The account's position on [animeId], or null when the anime is not on the
     * list yet — the normal case for a first write, not an error.
     */
    suspend fun entryState(token: String, animeId: Int): MalEntryState? =
        withContext(Dispatchers.IO) {
            val url = "$BASE/anime/$animeId".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "id,num_episodes,my_list_status")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@use null
                val raw = resp.body.string()
                if (!resp.isSuccessful) throw MalException(errorOf(resp.code, raw))
                val json = JSONObject(raw)
                val listStatus = json.optJSONObject("my_list_status")
                val total = json.optInt("num_episodes", 0)
                MalEntryState(
                    watchedEpisodes = listStatus?.optInt("num_episodes_watched", 0) ?: 0,
                    status = listStatus?.optStringOrNull("status"),
                    // MAL reports 0 for a show still airing. Carrying that
                    // through as a real total makes every episode look final.
                    totalEpisodes = total.takeIf { it > 0 },
                    isRewatching = listStatus?.optBoolean("is_rewatching", false) ?: false,
                )
            }
        }

    /**
     * Writes [episodes] watched, moving the status only when [status] is given.
     *
     * A null status means "leave it alone" — sending one unconditionally is what
     * would demote a rewatch or reopen a dropped show.
     */
    suspend fun updateProgress(
        token: String,
        animeId: Int,
        episodes: Int,
        status: String?,
    ): MalEntryState = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("num_watched_episodes", episodes.toString())
            .apply { if (status != null) add("status", status) }
            .build()

        val request = Request.Builder()
            .url("$BASE/anime/$animeId/my_list_status")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .patch(form)
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) throw MalException(errorOf(resp.code, raw))
            val json = JSONObject(raw)
            MalEntryState(
                watchedEpisodes = json.optInt("num_episodes_watched", episodes),
                status = json.optStringOrNull("status"),
                isRewatching = json.optBoolean("is_rewatching", false),
            )
        }
    }

    private fun errorOf(code: Int, raw: String): String {
        if (code == 401 || code == 403) return "MyAnimeList connection has expired"
        val detail = runCatching { JSONObject(raw).optStringOrNull("message") }.getOrNull()
        return detail ?: "MyAnimeList returned $code"
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private companion object {
        const val BASE = "https://api.myanimelist.net/v2"
    }
}

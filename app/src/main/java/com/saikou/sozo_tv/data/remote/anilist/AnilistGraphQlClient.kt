package com.saikou.sozo_tv.data.remote.anilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to AniList's GraphQL API directly.
 *
 * Hand-rolled JSON rather than Gson models: AniList nests every field two or
 * three levels deep behind optional containers, and a tree of nullable DTOs to
 * mirror that costs more to read than the twenty lines of parsing below.
 *
 * Uses the auth OkHttp client (platform TLS) — the app's content client trusts
 * any certificate, which must never carry someone's AniList token.
 */
class AnilistGraphQlClient(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Runs [query] and returns its `data` object.
     *
     * @throws AnilistException on a transport failure OR on a GraphQL error,
     *   which AniList reports inside a 200 body — a non-throwing HTTP call is
     *   not the same as a successful query.
     */
    private suspend fun run(
        query: String,
        variables: JSONObject = JSONObject(),
        token: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .post(payload)
            .build()

        val raw = try {
            okHttpClient.newCall(request).execute().use { it.body.string() }
        } catch (t: Throwable) {
            throw AnilistException("AniList bilan bog'lanib bo'lmadi", t)
        }

        val body = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw AnilistException("AniList javobini o'qib bo'lmadi")

        body.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
            val message = errors.optJSONObject(0)?.optString("message").orEmpty()
            throw AnilistException(message.ifBlank { "AniList so'rovni rad etdi" })
        }

        body.optJSONObject("data") ?: throw AnilistException("AniList ma'lumot qaytarmadi")
    }

    /** Who the stored token belongs to. Also the cheapest check that it still works. */
    suspend fun viewer(token: String): AnilistViewer {
        val data = run("query { Viewer { id name avatar { large } } }", token = token)
        val v = data.optJSONObject("Viewer")
            ?: throw AnilistException("AniList hisobi topilmadi")
        return AnilistViewer(
            id = v.optInt("id"),
            name = v.optString("name"),
            avatarUrl = v.optJSONObject("avatar")?.optStringOrNull("large"),
        )
    }

    /**
     * The viewer's anime list, every status in one call.
     *
     * Flattened here so the grouping decision stays in the UI rather than being
     * baked into the transport — and de-duped by entry id, because a title on a
     * custom list is repeated under several list objects.
     */
    suspend fun mediaList(token: String, userId: Int): List<AnilistListEntry> {
        val query = """
            query (${'$'}userId: Int) {
              MediaListCollection(userId: ${'$'}userId, type: ANIME) {
                lists {
                  entries {
                    id
                    status
                    progress
                    score(format: POINT_10)
                    updatedAt
                    media { $MEDIA_FIELDS }
                  }
                }
              }
            }
        """.trimIndent()

        val data = run(query, JSONObject().put("userId", userId), token)
        val lists = data.optJSONObject("MediaListCollection")?.optJSONArray("lists")
            ?: return emptyList()

        val seen = HashSet<Int>()
        val out = ArrayList<AnilistListEntry>()
        for (i in 0 until lists.length()) {
            val entries = lists.optJSONObject(i)?.optJSONArray("entries") ?: continue
            for (j in 0 until entries.length()) {
                val entry = entries.optJSONObject(j)?.toListEntry() ?: continue
                if (seen.add(entry.id)) out.add(entry)
            }
        }
        return out
    }

    /**
     * The viewer's own entry for one title, or null when it is not on their list.
     *
     * Read before every automatic write so progress is never moved BACKWARDS: a
     * rewatch, a second device, or a partly-watched episode would otherwise
     * overwrite a higher number the account already holds.
     */
    suspend fun entryState(token: String, mediaId: Int): AnilistEntryState? {
        val query = """
            query (${'$'}mediaId: Int) {
              Media(id: ${'$'}mediaId, type: ANIME) {
                episodes
                mediaListEntry { progress status }
              }
            }
        """.trimIndent()

        val media = run(query, JSONObject().put("mediaId", mediaId), token)
            .optJSONObject("Media") ?: return null
        val entry = media.optJSONObject("mediaListEntry")
        return AnilistEntryState(
            onList = entry != null,
            progress = entry?.optInt("progress", 0) ?: 0,
            status = entry?.optStringOrNull("status"),
            totalEpisodes = media.optIntOrNull("episodes"),
        )
    }

    /** Public title search — used to attach an AniList id to a locally-watched title. */
    suspend fun searchMedia(search: String, perPage: Int = 20): List<AnilistMedia> {
        if (search.isBlank()) return emptyList()
        val query = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) { $MEDIA_FIELDS }
              }
            }
        """.trimIndent()

        val variables = JSONObject().put("search", search.trim()).put("perPage", perPage)
        val media = run(query, variables).optJSONObject("Page")?.optJSONArray("media")
            ?: return emptyList()
        return media.mapObjects { it.toMedia() }
    }

    /**
     * Writes progress back to AniList.
     *
     * [progress] is an episode COUNT, not an index — AniList means "episodes
     * finished", so passing a zero-based index silently reports one episode less
     * than the viewer actually watched.
     *
     * Returns what AniList stored, which is not always what was sent: it clamps
     * progress to the episode total and flips status to COMPLETED on the last one.
     */
    suspend fun saveProgress(
        token: String,
        mediaId: Int,
        progress: Int,
        status: String? = null,
    ): AnilistSaveResult {
        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                progress
                status
              }
            }
        """.trimIndent()

        val variables = JSONObject()
            .put("mediaId", mediaId)
            .put("progress", progress)
        status?.let { variables.put("status", it) }

        val saved = run(mutation, variables, token).optJSONObject("SaveMediaListEntry")
            ?: throw AnilistException("AniList saqlamadi")
        return AnilistSaveResult(
            progress = saved.optInt("progress", progress),
            status = saved.optStringOrNull("status") ?: status ?: AnilistStatus.CURRENT.value,
        )
    }

    // ─── parsing ─────────────────────────────────────────────────────────────

    private fun JSONObject.toListEntry(): AnilistListEntry? {
        val media = optJSONObject("media")?.toMedia() ?: return null
        return AnilistListEntry(
            id = optInt("id"),
            media = media,
            status = optStringOrNull("status") ?: AnilistStatus.CURRENT.value,
            progress = optInt("progress", 0),
            score = optDoubleOrNull("score"),
            updatedAt = optLong("updatedAt", 0L),
        )
    }

    private fun JSONObject.toMedia(): AnilistMedia {
        val title = optJSONObject("title")
        val airing = optJSONObject("nextAiringEpisode")?.let {
            val at = it.optLong("airingAt", 0L)
            if (at > 0) AnilistAiring(episode = it.optInt("episode", 0), airingAt = at) else null
        }
        return AnilistMedia(
            id = optInt("id"),
            romajiTitle = title?.optStringOrNull("romaji"),
            englishTitle = title?.optStringOrNull("english"),
            nativeTitle = title?.optStringOrNull("native"),
            coverImage = optJSONObject("coverImage")?.optStringOrNull("large"),
            bannerImage = optStringOrNull("bannerImage"),
            episodes = optIntOrNull("episodes"),
            averageScore = optIntOrNull("averageScore"),
            seasonYear = optIntOrNull("seasonYear"),
            format = optStringOrNull("format"),
            nextAiring = airing,
        )
    }

    /** `optString` returns "null" for a JSON null, which is a real trap here. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            optJSONObject(i)?.let { out.add(transform(it)) }
        }
        return out
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * The media selection every query shares. One constant rather than a copy
         * per query: the parser reads these fields unconditionally, so a field
         * added to search but forgotten in the library query would show up as a
         * silently missing value rather than an error.
         */
        const val MEDIA_FIELDS = """
            id
            episodes
            averageScore
            seasonYear
            format
            title { romaji english native }
            coverImage { large }
            bannerImage
            nextAiringEpisode { episode airingAt }
        """
    }
}

/** A failure that is AniList's fault, not the transport's — carried to the UI as text. */
class AnilistException(message: String, cause: Throwable? = null) : Exception(message, cause)

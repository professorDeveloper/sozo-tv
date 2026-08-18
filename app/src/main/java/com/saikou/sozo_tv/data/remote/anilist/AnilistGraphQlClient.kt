package com.saikou.sozo_tv.data.remote.anilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AnilistGraphQlClient(
    private val okHttpClient: OkHttpClient,
) {

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
            throw AnilistException("Could not reach AniList", t)
        }

        val body = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw AnilistException("AniList sent a response we could not read")

        body.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
            val message = errors.optJSONObject(0)?.optString("message").orEmpty()
            throw AnilistException(message.ifBlank { "AniList rejected the request" })
        }

        body.optJSONObject("data") ?: throw AnilistException("AniList returned no data")
    }

    suspend fun viewer(token: String): AnilistViewer {
        val data = run("query { Viewer { id name avatar { large } } }", token = token)
        val v = data.optJSONObject("Viewer")
            ?: throw AnilistException("AniList did not recognise this account")
        return AnilistViewer(
            id = v.optInt("id"),
            name = v.optString("name"),
            avatarUrl = v.optJSONObject("avatar")?.optStringOrNull("large"),
        )
    }

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
            ?: throw AnilistException("AniList did not save the change")
        return AnilistSaveResult(
            progress = saved.optInt("progress", progress),
            status = saved.optStringOrNull("status") ?: status ?: AnilistStatus.CURRENT.value,
        )
    }

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

class AnilistException(message: String, cause: Throwable? = null) : Exception(message, cause)

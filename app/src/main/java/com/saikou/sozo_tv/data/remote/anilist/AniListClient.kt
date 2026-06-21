package com.saikou.sozo_tv.data.remote.anilist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Minimal AniList GraphQL transport. POSTs `{query, variables}` to https://graphql.anilist.co/,
 * attaches the user's Bearer token when present (required for `mediaListEntry` and mutations),
 * and returns the `data` object — or a failure carrying the first GraphQL error message.
 */
class AniListClient(
    private val okHttpClient: OkHttpClient,
    private val prefs: PreferenceManager,
) {

    suspend fun post(query: String, variables: Map<String, Any?>): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("query", query)
                    .put("variables", JSONObject(variables))
                val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

                val builder = Request.Builder()
                    .url(ENDPOINT)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")

                val token = prefs.getString(AuthPrefKeys.ANILIST_TOKEN)
                if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")

                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body.string()
                    val root = JsonParser.parseString(raw).asJsonObject
                    val errors = root.getAsJsonArray("errors")
                    if (errors != null && errors.size() > 0) {
                        val msg = errors[0].asJsonObject.get("message")?.asString
                            ?: "AniList request failed (${resp.code})"
                        return@use Result.failure(IllegalStateException(msg))
                    }
                    val data = root.getAsJsonObject("data")
                        ?: return@use Result.failure(IllegalStateException("Empty AniList response"))
                    Result.success(data)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

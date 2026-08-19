package com.saikou.sozo_tv.data.remote.anilist

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.saikou.sozo_tv.data.repository.RemoteTitleLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnilistLinkClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    suspend fun get(): ApiResult<AnilistLink?> = call("GET")

    suspend fun unlink(): ApiResult<AnilistLink?> = call("DELETE")

    suspend fun syncLinks(items: List<Map<String, Any?>>): ApiResult<List<RemoteTitleLink>> =
        withContext(Dispatchers.IO) {
            val token = runCatching { tokenProvider() }.getOrNull()
                ?: return@withContext ApiResult.Http(401, "Not signed in", null)

            try {
                val payload = gson.toJson(mapOf("items" to items)).toRequestBody(JSON)
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$SYNC_PATH")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(payload)
                    .build()

                okHttpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body.string()
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            gson.fromJson(raw, ApiMessage::class.java)?.message
                        }.getOrNull()
                        return@use ApiResult.Http(resp.code, msg, null)
                    }
                    val parsed = runCatching { gson.fromJson(raw, LinksResponse::class.java) }
                        .getOrNull()
                    ApiResult.Ok(parsed?.items.orEmpty())
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private suspend fun call(method: String): ApiResult<AnilistLink?> =
        withContext(Dispatchers.IO) {
            val token = runCatching { tokenProvider() }.getOrNull()
                ?: return@withContext ApiResult.Http(401, "Not signed in", null)

            try {
                val builder = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$PATH")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                when (method) {
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }

                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body.string()
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            gson.fromJson(raw, ApiMessage::class.java)?.message
                        }.getOrNull()
                        return@use ApiResult.Http(
                            resp.code, msg, resp.header("Retry-After")?.trim()?.toIntOrNull(),
                        )
                    }
                    val parsed = runCatching { gson.fromJson(raw, LinkResponse::class.java) }
                        .getOrNull()
                    ApiResult.Ok(parsed?.anilist?.takeIf { !it.accessToken.isNullOrBlank() })
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private data class LinkResponse(val anilist: AnilistLink? = null)

    private data class LinksResponse(val items: List<RemoteTitleLink>? = null)

    private data class ApiMessage(@SerializedName("message") val message: String?)

    private companion object {
        const val PATH = "/anilist/link"

        // NOT "$PATH/sync". The server mounts this as /anilist/links/sync —
        // plural — and deriving it from PATH produced a 404 on every launch,
        // which the caller swallowed as "nothing to sync".
        const val SYNC_PATH = "/anilist/links/sync"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class AnilistLink(
    val userId: Int? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String? = null,
    val expiresAt: String? = null,
    val linkedAt: String? = null,
) {
    fun viewer(): AnilistViewer? {
        val id = userId ?: return null
        return AnilistViewer(id = id, name = name.orEmpty(), avatarUrl = avatarUrl)
    }
}

package com.saikou.sozo_tv.data.remote.mal

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.repository.RemoteTitleLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Sozo backend's view of the MyAnimeList link.
 *
 * There is no connect flow here on purpose. Linking needs a browser and a
 * consent screen, which a remote control is a miserable way to drive — so the
 * link is made on the phone and this device simply inherits it from the
 * account, exactly as AniList does.
 *
 * `GET /mal/link` also renews the token server-side when it is near expiry, so
 * a TV that has been unplugged for a month comes back with a working one.
 */
class MalLinkClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    suspend fun get(): ApiResult<MalLink?> = call("GET")

    suspend fun unlink(): ApiResult<MalLink?> = call("DELETE")

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

    private suspend fun call(method: String): ApiResult<MalLink?> =
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
                    ApiResult.Ok(parsed?.mal?.takeIf { !it.accessToken.isNullOrBlank() })
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private data class LinkResponse(val mal: MalLink? = null)

    private data class LinksResponse(val items: List<RemoteTitleLink>? = null)

    private data class ApiMessage(@SerializedName("message") val message: String?)

    private companion object {
        const val PATH = "/mal/link"

        // Plural, matching the server mount — the same shape that made the
        // AniList client 404 on every launch when it was derived from PATH.
        const val SYNC_PATH = "/mal/links/sync"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

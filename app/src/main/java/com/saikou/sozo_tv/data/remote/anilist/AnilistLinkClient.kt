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

/**
 * Reads the AniList connection stored on the Sozo account.
 *
 * The TV never performs the OAuth handshake itself, and that is the whole point
 * of putting the exchange on the server: an OAuth flow on a television means
 * typing an AniList password with a d-pad. The phone connects once, the token is
 * stored against the account, and this box simply picks it up.
 *
 * Shares the `authOkHttp` client with device sign-in for the same reason the
 * history and lists clients do: this call carries a bearer token, and the app's
 * content client trusts any certificate.
 */
class AnilistLinkClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    /**
     * The stored link, or `Ok(null)` when the account has none.
     *
     * "No link" is a successful answer, not an error — the caller shows an
     * instruction rather than a failure.
     */
    suspend fun get(): ApiResult<AnilistLink?> = call("GET")

    /** Removes the link from the account, and therefore from every device. */
    suspend fun unlink(): ApiResult<AnilistLink?> = call("DELETE")

    /**
     * Exchanges this box's title->media map with the account.
     *
     * The reason the TV can track anything at all: a d-pad cannot realistically
     * search AniList, and a translated source title will never match one exactly
     * — so this box depends on associations the phone made by hand.
     */
    suspend fun syncLinks(items: List<Map<String, Any?>>): ApiResult<List<RemoteTitleLink>> =
        withContext(Dispatchers.IO) {
            val token = runCatching { tokenProvider() }.getOrNull()
                ?: return@withContext ApiResult.Http(401, "Not signed in", null)

            try {
                val payload = gson.toJson(mapOf("items" to items)).toRequestBody(JSON)
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$PATH/sync")
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
            // No token => signed out. Reported as 401 so callers branch on the
            // code rather than on message text.
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
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * The AniList connection as the account holds it.
 *
 * The access token travels to the client on purpose: this box talks to AniList
 * directly, which keeps that traffic off the Sozo server and means an AniList
 * outage degrades one feature instead of the whole app.
 */
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

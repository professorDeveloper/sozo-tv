package com.saikou.sozo_tv.data.remote.lists

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One entry in a curated list. Mirrors the server's list item shape. */
data class UserListItem(
    val provider: String? = null,
    val contentUrl: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
)

private data class UserListResponse(
    val kind: String? = null,
    val items: List<UserListItem>? = null,
)

private data class AddRequest(
    val provider: String,
    val contentUrl: String,
    val title: String?,
    val thumbnail: String?,
)

private data class RemoveRequest(val contentUrl: String)

private data class ApiMessage(@SerializedName("message") val message: String?)

/**
 * Transport for `/auth/lists/<kind>` — the TV's FIRST authenticated API surface.
 *
 * Until now the device session minted and rotated an access token that nothing
 * ever spent (see DeviceAuthRepository). These lists live on the account, so
 * every call here carries `Authorization: Bearer <access>`, supplied per-request
 * by [tokenProvider] rather than captured once: the token is 15-minute lived and
 * the repository refreshes it transparently, so a cached copy would go stale
 * mid-session.
 *
 * Uses the same `authOkHttp` client as device sign-in — platform TLS, no
 * body logging. The content client trusts any certificate, which must never
 * carry a bearer token.
 */
class UserListsClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    suspend fun get(kind: UserListKind): ApiResult<List<UserListItem>> =
        request(kind, "GET", null, UserListResponse::class.java)
            .map { it.items.orEmpty().filter { i -> !i.contentUrl.isNullOrBlank() } }

    suspend fun add(kind: UserListKind, item: UserListItem): ApiResult<Unit> {
        val provider = item.provider ?: return ApiResult.Http(400, "provider required", null)
        val contentUrl = item.contentUrl ?: return ApiResult.Http(400, "contentUrl required", null)
        return request(
            kind, "POST",
            AddRequest(provider, contentUrl, item.title, item.thumbnail),
            ApiMessage::class.java,
        ).map { }
    }

    suspend fun remove(kind: UserListKind, contentUrl: String): ApiResult<Unit> =
        request(kind, "DELETE", RemoveRequest(contentUrl), ApiMessage::class.java).map { }

    private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Ok -> ApiResult.Ok(transform(body))
        is ApiResult.Http -> this
        is ApiResult.Network -> this
    }

    private suspend fun <T> request(
        kind: UserListKind,
        method: String,
        body: Any?,
        type: Class<T>,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        // No token => not signed in. Reported as 401 so callers branch on the
        // code, never on message text (the server's errors are bare strings).
        val token = runCatching { tokenProvider() }.getOrNull()
            ?: return@withContext ApiResult.Http(401, "Not signed in", null)

        try {
            val payload = body?.let { gson.toJson(it).toRequestBody(JSON) }
            val builder = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$PATH/${kind.slug}")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                // DELETE carries a body here, which OkHttp allows and the
                // server's express.json() parses.
                else -> {
                    builder.addHeader("Content-Type", "application/json")
                    builder.method(method, payload ?: "".toRequestBody(JSON))
                }
            }

            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val raw = resp.body.string()
                if (!resp.isSuccessful) {
                    val msg = runCatching { gson.fromJson(raw, ApiMessage::class.java)?.message }.getOrNull()
                    return@use ApiResult.Http(
                        resp.code, msg, resp.header("Retry-After")?.trim()?.toIntOrNull(),
                    )
                }
                val parsed = runCatching { gson.fromJson(raw, type) }.getOrNull()
                    ?: return@use ApiResult.Http(resp.code, "Empty response", null)
                ApiResult.Ok(parsed)
            }
        } catch (t: Throwable) {
            ApiResult.Network(t)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val PATH = "/auth/lists"
    }
}

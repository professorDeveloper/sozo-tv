package com.saikou.sozo_tv.data.remote.history

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One history row on the wire. The field names are the server's contract and
 * are shared verbatim with the Flutter client - renaming one here desyncs the
 * two apps into separate rows for the same episode.
 *
 * [extra] carries whatever a platform stores beyond the shared shape (the TV's
 * Room row has a dozen such fields) and comes back untouched, so a device gets
 * its own full record rather than a lossy projection of it.
 */
data class HistorySyncItem(
    val provider: String,
    val contentUrl: String? = null,
    val contentId: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val isSerial: Boolean = false,
    val episodeIndex: Int? = null,
    val episodeNumber: Int? = null,
    val episodeLabel: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val extra: Map<String, Any?>? = null,
    val watchedAt: String? = null,
    val deletedAt: String? = null,
    /** Server-assigned identity. Sent back as-is; never invented by the client. */
    val key: String? = null,
)

data class HistorySyncResult(
    val items: List<HistorySyncItem>,
    /** Server clock. Becomes the next request's `since`, so client skew cannot lose rows. */
    val serverTime: String?,
)

private data class SyncRequest(
    val items: List<HistorySyncItem>,
    val since: String?,
)

private data class SyncResponse(
    val items: List<HistorySyncItem>? = null,
    val serverTime: String? = null,
)

private data class ApiMessage(@SerializedName("message") val message: String?)

/**
 * Transport for `/auth/history/sync` - push and pull in one round trip.
 *
 * Shares the `authOkHttp` client with device sign-in for the same reason the
 * lists client does: this call carries a bearer token, and the app's content
 * client trusts any certificate.
 */
class WatchHistorySyncClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    /**
     * Sends [items] and returns everything changed elsewhere since [since].
     *
     * An empty [items] is a normal pull, not a special case - a device with no
     * local changes still wants the other devices' progress.
     */
    suspend fun sync(items: List<HistorySyncItem>, since: String?): ApiResult<HistorySyncResult> =
        withContext(Dispatchers.IO) {
            // No token => signed out. Reported as 401 so callers branch on the
            // code rather than on message text.
            val token = runCatching { tokenProvider() }.getOrNull()
                ?: return@withContext ApiResult.Http(401, "Not signed in", null)

            try {
                val payload = gson.toJson(SyncRequest(items, since)).toRequestBody(JSON)
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$PATH")
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
                        return@use ApiResult.Http(
                            resp.code, msg, resp.header("Retry-After")?.trim()?.toIntOrNull(),
                        )
                    }
                    val parsed = runCatching { gson.fromJson(raw, SyncResponse::class.java) }
                        .getOrNull()
                        ?: return@use ApiResult.Http(resp.code, "Empty response", null)
                    ApiResult.Ok(
                        HistorySyncResult(parsed.items.orEmpty(), parsed.serverTime)
                    )
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val PATH = "/auth/history/sync"
    }
}

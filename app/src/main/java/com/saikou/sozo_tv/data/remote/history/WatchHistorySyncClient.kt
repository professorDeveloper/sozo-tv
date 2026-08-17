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
    val key: String? = null,
)

data class HistorySyncResult(
    val items: List<HistorySyncItem>,
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

class WatchHistorySyncClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    suspend fun sync(items: List<HistorySyncItem>, since: String?): ApiResult<HistorySyncResult> =
        withContext(Dispatchers.IO) {
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

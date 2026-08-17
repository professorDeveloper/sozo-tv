package com.saikou.sozo_tv.data.remote.version

import com.google.gson.Gson
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class AppVersionClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
) {

    suspend fun check(currentVersion: Long): ApiResult<AppVersionCheck> =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}$PATH".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("platform", PLATFORM)
                ?.addQueryParameter("currentVersion", currentVersion.toString())
                ?.build()
                ?: return@withContext ApiResult.Http(0, "Bad base URL", null)

            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body.string()
                    if (!resp.isSuccessful) {
                        return@use ApiResult.Http(resp.code, raw.take(200), null)
                    }
                    val parsed = runCatching { gson.fromJson(raw, AppVersionCheck::class.java) }
                        .getOrNull()
                        ?: return@use ApiResult.Http(resp.code, "Unparseable response", null)
                    ApiResult.Ok(parsed)
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private companion object {
        const val PATH = "/app-version"

        const val PLATFORM = "androidtv"
    }
}

data class AppVersionCheck(
    val platform: String? = null,
    val updateAvailable: Boolean? = null,
    val forceUpdate: Boolean? = null,
    val version: Long? = null,
    val minVersion: Long? = null,
    val downloadUrl: String? = null,
    val storeUrl: String? = null,
    val releaseNotes: String? = null,
) {
    val isActionable: Boolean
        get() = updateAvailable == true && !installUrl.isNullOrBlank()

    val installUrl: String?
        get() = downloadUrl?.takeIf { it.isNotBlank() } ?: storeUrl?.takeIf { it.isNotBlank() }
}

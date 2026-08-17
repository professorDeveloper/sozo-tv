package com.saikou.sozo_tv.data.remote.version

import com.google.gson.Gson
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Asks the backend whether a newer TV build exists.
 *
 * The same endpoint the phone uses, so ONE admin screen governs both apps. It is
 * public — no bearer token — but it still rides the auth OkHttp client, and that
 * choice is load-bearing rather than incidental: this response decides which APK
 * the box downloads and installs. The app's content client trusts any
 * certificate, so answering this question over it would let anyone on the
 * network hand a TV an arbitrary "update" to install.
 *
 * `androidtv` is its own platform on the server. The TV ships a separate APK with
 * its own versionCode sequence, so comparing against the phone's row would offer
 * an update that does not exist, or hide one that does.
 */
class AppVersionClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
) {

    /**
     * Returns the server's answer, or a failure.
     *
     * A missing row is a SUCCESS carrying `updateAvailable = false`, not an
     * error: "no TV version published yet" is an ordinary state, and the caller
     * must not treat it as something to report.
     */
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

        /** Must match AppVersion.PLATFORMS on the server. */
        const val PLATFORM = "androidtv"
    }
}

/**
 * The server's answer. Every field is nullable because this is wire JSON, and a
 * schema change should degrade to "no update" rather than crash a splash screen.
 */
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
    /**
     * Whether this answer is worth showing the user.
     *
     * An update with nothing to download is not an update: the TV has no store
     * to fall back to, so the dialog would offer a button that cannot work.
     * Checked here rather than in the UI so every caller gets the same answer.
     */
    val isActionable: Boolean
        get() = updateAvailable == true && !installUrl.isNullOrBlank()

    /** The APK to fetch. `storeUrl` is a fallback for boxes that have a store. */
    val installUrl: String?
        get() = downloadUrl?.takeIf { it.isNotBlank() } ?: storeUrl?.takeIf { it.isNotBlank() }
}

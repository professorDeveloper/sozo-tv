package com.saikou.sozo_tv.data.remote.device

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Casing below is EXACTLY the backend's snake/camel mix: request/response bodies use
// device_code / user_code (snake) but refreshToken / deviceName / accessToken (camel).
data class DeviceCodeRequest(@SerializedName("deviceName") val deviceName: String?)

data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("interval") val interval: Int,
)

data class DevicePollRequest(@SerializedName("device_code") val deviceCode: String)

data class DevicePollResponse(
    val status: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val sessionId: String?,
    val user: DeviceUserDto?,
)

/**
 * Only the fields the TV renders. The server returns the whole user document minus
 * password/refreshToken, including unbounded `favorites` / `watchHistory` arrays — never map
 * those. `firebaseUid` is sparse and may be absent entirely.
 */
data class DeviceUserDto(
    @SerializedName("_id") val id: String?,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val photoURL: String?,
    val banned: Boolean = false,
)

data class DeviceRefreshRequest(val refreshToken: String)

/** No user object here — identity has to be carried over from the existing session. */
data class DeviceRefreshResponse(val accessToken: String?, val refreshToken: String?)

data class DeviceLogoutRequest(val refreshToken: String?)

sealed class ApiResult<out T> {
    data class Ok<T>(val body: T) : ApiResult<T>()

    /**
     * Non-2xx. The backend's entire error contract is a bare `{"message":"<uzbek text>"}` with no
     * code and no field, so `message` is for logs and display ONLY — branch on [code], never on text.
     */
    data class Http(val code: Int, val message: String?, val retryAfterSec: Int?) :
        ApiResult<Nothing>()

    data class Network(val cause: Throwable) : ApiResult<Nothing>()
}

/**
 * Transport for the RFC-8628 device-authorization endpoints.
 *
 * The injected client MUST be the `authOkHttp` one: the shared `baseOkHttp` accepts any
 * certificate, skips hostname verification and logs at BODY level — and `/auth/device/token`
 * returns both tokens in its body.
 */
class DeviceAuthClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
) {

    suspend fun createCode(deviceName: String?): ApiResult<DeviceCodeResponse> =
        post(PATH_CODE, DeviceCodeRequest(deviceName), DeviceCodeResponse::class.java)

    suspend fun poll(deviceCode: String): ApiResult<DevicePollResponse> =
        post(PATH_TOKEN, DevicePollRequest(deviceCode), DevicePollResponse::class.java)

    suspend fun refresh(refreshToken: String): ApiResult<DeviceRefreshResponse> =
        post(PATH_REFRESH, DeviceRefreshRequest(refreshToken), DeviceRefreshResponse::class.java)

    suspend fun logout(refreshToken: String?): ApiResult<Unit> =
        when (val r = post(PATH_LOGOUT, DeviceLogoutRequest(refreshToken), ApiMessage::class.java)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Http -> r
            is ApiResult.Network -> r
        }

    private suspend fun <T> post(path: String, body: Any?, type: Class<T>): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(baseUrl.trimEnd('/') + path)
                    .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                okHttpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body.string()
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            gson.fromJson(raw, ApiMessage::class.java)?.message
                        }.getOrNull()
                        // Set on every 429, and on the probabilistic load-shed 503.
                        val retry = resp.header("Retry-After")?.trim()?.toIntOrNull()
                        return@use ApiResult.Http(resp.code, msg, retry)
                    }
                    val parsed = gson.fromJson(raw, type)
                        ?: return@use ApiResult.Http(resp.code, "Empty response", null)
                    ApiResult.Ok(parsed)
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // The TV must NEVER call /auth/refresh or /auth/logout. Those act on User.refreshToken,
        // which is the PHONE's session — calling them here would sign the user's phone out.
        // Every path below is scoped to this one DeviceSession row.
        const val PATH_CODE = "/auth/device/code"
        const val PATH_TOKEN = "/auth/device/token"
        const val PATH_REFRESH = "/auth/device/refresh"
        const val PATH_LOGOUT = "/auth/device/logout"
    }
}

private data class ApiMessage(val message: String?)

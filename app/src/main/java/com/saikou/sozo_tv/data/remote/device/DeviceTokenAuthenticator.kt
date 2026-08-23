package com.saikou.sozo_tv.data.remote.device

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Gets a fresh token when the server rejects the one we sent.
 *
 * The TV used to refresh only when the stored token was near its own `exp`. That misses the case
 * that actually strands a device: a token the SERVER rejects while its `exp` still looks fine.
 * Nothing then asked for a new one, so every authenticated call — AniList, MyAnimeList,
 * watch-history sync, clear history, the phone remote — answered 401 indefinitely while the TV
 * still showed itself as signed in, and each feature reported its own private symptom
 * ("Not connected", "nothing synced") rather than the shared cause.
 *
 * An Authenticator rather than an interceptor per client: OkHttp invokes this on any 401 and
 * retries the request with whatever we return, so one object covers every client that shares this
 * HTTP stack instead of each one growing its own retry.
 */
class DeviceTokenAuthenticator(
    private val refresh: suspend () -> String?,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request

        // The device-auth endpoints must never trigger this. Refreshing in response to a failed
        // refresh recurses until the session is cleared or the stack gives out.
        if (request.url.encodedPath.contains(DEVICE_AUTH_PREFIX)) return null

        // Retry once, never more. OkHttp chains priorResponse on each attempt, so a second 401
        // means the freshly minted token was rejected too — the session is genuinely gone, and
        // looping would only hammer the server on every screen that polls.
        if (response.priorResponse != null) return null

        // Runs on OkHttp's own thread, which is already off the main thread and is allowed to
        // block — that is the contract for this callback.
        val fresh = runBlocking { runCatching { refresh() }.getOrNull() } ?: return null

        // The refresh handed back the same token we just sent, so replaying the request would
        // reproduce the same 401.
        if (request.header(AUTHORIZATION) == "Bearer $fresh") return null

        return request.newBuilder().header(AUTHORIZATION, "Bearer $fresh").build()
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val DEVICE_AUTH_PREFIX = "/auth/device/"
    }
}

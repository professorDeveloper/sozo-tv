package com.saikou.sozo_tv.data.repository

import android.os.Build
import android.util.Base64
import com.saikou.sozo_tv.data.local.pref.DeviceSession
import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.device.DeviceAuthClient
import com.saikou.sozo_tv.data.remote.device.DeviceCodeResponse
import com.saikou.sozo_tv.data.remote.device.DevicePollResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** One poll step, already interpreted. Nothing above this layer ever sees an HTTP status code. */
sealed class PollOutcome {
    data object Pending : PollOutcome()

    /** The session is already persisted by the time this is emitted. */
    data object Approved : PollOutcome()

    /** The pairing is gone; a brand new device_code is required. */
    data object Expired : PollOutcome()

    /** Rate limited — the pairing is STILL VALID, only the cadence changes. */
    data class RateLimited(val retryAfterMs: Long) : PollOutcome()

    data class Transient(val message: String) : PollOutcome()

    data class Fatal(val message: String) : PollOutcome()
}

/**
 * The RFC-8628 state machine: create a pairing, interpret one poll, rotate tokens, log out.
 *
 * All of the endpoints used here are scoped to this single device session. The TV must never
 * touch /auth/refresh or /auth/logout — those act on the user's phone session.
 */
class DeviceAuthRepository(
    private val client: DeviceAuthClient,
    private val store: DeviceSessionStore,
) {

    /** Outlives any screen: token rotation must never be tied to a UI lifecycle. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val session: StateFlow<DeviceSession?> get() = store.session

    fun isSignedIn(): Boolean = store.isSignedIn()

    /** `deviceName` is a display label only; the server truncates it to 80 characters. */
    suspend fun createPairing(): ApiResult<DeviceCodeResponse> =
        client.createCode("${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80))

    /**
     * One poll. On `approved` this PERSISTS the session before returning: the pairing flips to
     * `claimed` server-side before the response is written, so a repeat poll answers 410 and the
     * tokens are never minted again. Persist-before-emit is the whole reason this lives here and
     * not in the ViewModel.
     *
     * [NonCancellable] is load-bearing, not defensive: the screen cancels this job on every
     * `onStop`, and cancelling between the server writing `claimed` and [persist] running would
     * throw away the only copy of the tokens that will ever exist. One poll costs a few seconds
     * at most, so finishing it is cheap; losing it is unrecoverable.
     */
    suspend fun poll(deviceCode: String): PollOutcome = withContext(Dispatchers.IO + NonCancellable) {
        when (val r = client.poll(deviceCode)) {
            is ApiResult.Ok -> when (r.body.status) {
                // An approved body with no tokens is unusable AND unrepeatable — the pairing is
                // already claimed — so surface it as a failure rather than a phantom sign-in.
                STATUS_APPROVED -> if (persist(r.body)) {
                    PollOutcome.Approved
                } else {
                    PollOutcome.Fatal("Sign-in response was incomplete")
                }

                // The TTL sweep is lazy: an expired pairing answers 200 expired for a while and
                // 404 afterwards. Both mean the same thing to us.
                STATUS_EXPIRED -> PollOutcome.Expired
                else -> PollOutcome.Pending
            }

            is ApiResult.Http -> when (r.code) {
                404, 410 -> PollOutcome.Expired
                429 -> PollOutcome.RateLimited((r.retryAfterSec ?: DEFAULT_RETRY_SEC) * 1000L)
                // The load shedder and the 15s server-side timeout both answer with the same bare
                // {message} body as a real failure; treating either as terminal would drop a live
                // pairing.
                in 500..599 -> PollOutcome.Transient(r.message ?: "Server error")
                else -> PollOutcome.Fatal(r.message ?: "Sign-in failed (${r.code})")
            }

            is ApiResult.Network -> PollOutcome.Transient(r.cause.message ?: "Network error")
        }
    }

    private val refreshMutex = Mutex()

    /**
     * The accessor every authenticated call goes through. Refreshes 60s before `exp` rather than
     * on a timer, and is Mutex-serialized because rotation is destructive — two concurrent
     * refreshes would leave the second holding a token the first already invalidated.
     */
    suspend fun accessToken(): String? = refreshMutex.withLock {
        val current = store.current() ?: return null
        if (System.currentTimeMillis() < current.accessExpiresAtMs - REFRESH_SKEW_MS) {
            return current.accessToken
        }
        // The server overwrites the stored refresh hash BEFORE it answers, so the old token is
        // dead the instant the response exists. Cancellation must not be able to land between
        // that response and the write below, or the TV keeps a token the server already killed
        // and is signed out for good. Call + write are therefore one uninterruptible unit.
        withContext(Dispatchers.IO + NonCancellable) {
            when (val r = client.refresh(current.refreshToken)) {
                is ApiResult.Ok -> {
                    val access = r.body.accessToken
                    val refresh = r.body.refreshToken
                    if (access == null || refresh == null) {
                        current.accessToken
                    } else {
                        store.updateTokens(access, refresh, expiryOf(access))
                        access
                    }
                }

                // 401 (rotated/unlinked token) and 403 (banned) are terminal; anything else keeps
                // the session so a flaky network cannot sign the TV out.
                is ApiResult.Http -> if (r.code == 401 || r.code == 403) {
                    store.clear()
                    null
                } else {
                    current.accessToken
                }

                is ApiResult.Network -> current.accessToken
            }
        }
    }

    /** Best-effort refresh on app start. Callers must cap it — this can hit the network. */
    suspend fun bootstrap() {
        if (store.isSignedIn()) runCatching { accessToken() }
    }

    /**
     * [bootstrap] on a scope of our own, for callers that want to give up WAITING without
     * cancelling the work. Timing out a [bootstrap] call directly would abandon a rotation the
     * server has already committed; abandoning a `join()` on this job cannot.
     */
    fun bootstrapAsync(): Job = scope.launch { bootstrap() }

    /** Revokes only THIS device session, then wipes locally regardless of the result. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val refreshToken = store.current()?.refreshToken
        store.clear()
        withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { runCatching { client.logout(refreshToken) } }
        Unit
    }

    /**
     * [logout] on the repository's own scope. The caller of a sign-out typically tears its own
     * scope down in the same handler, which would abandon the revocation call and leave a live
     * session on the server after a user-visible sign-out.
     */
    fun logoutAsync(): Job = scope.launch { logout() }

    /** @return false when the response carried no usable token pair. */
    private fun persist(body: DevicePollResponse): Boolean {
        val access = body.accessToken ?: return false
        val refresh = body.refreshToken ?: return false
        store.save(
            DeviceSession(
                userId = body.user?.id.orEmpty(),
                username = body.user?.displayName ?: body.user?.username.orEmpty(),
                email = body.user?.email,
                displayName = body.user?.displayName,
                photoUrl = body.user?.photoURL,
                accessToken = access,
                refreshToken = refresh,
                accessExpiresAtMs = expiryOf(access),
                sessionId = body.sessionId.orEmpty(),
            )
        )
        return true
    }

    /** Falls just short of the server's 15m access lifetime when the claim can't be read. */
    private fun expiryOf(accessToken: String): Long =
        jwtExpiryMs(accessToken) ?: (System.currentTimeMillis() + FALLBACK_ACCESS_LIFETIME_MS)

    /** Dependency-free `exp` reader: base64url-decode segment 1 and read the claim. */
    private fun jwtExpiryMs(jwt: String): Long? = runCatching {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        val json = String(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
            Charsets.UTF_8,
        )
        val exp = JSONObject(json).optLong("exp", 0L)
        if (exp > 0L) exp * 1000L else null
    }.getOrNull()

    private companion object {
        const val STATUS_APPROVED = "approved"
        const val STATUS_EXPIRED = "expired"
        const val REFRESH_SKEW_MS = 60_000L
        const val FALLBACK_ACCESS_LIFETIME_MS = 14 * 60_000L
        const val DEFAULT_RETRY_SEC = 3
        const val LOGOUT_TIMEOUT_MS = 5_000L
    }
}

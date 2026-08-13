package com.saikou.sozo_tv.presentation.viewmodel

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.BuildConfig
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.device.DeviceCodeResponse
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.repository.PollOutcome
import com.saikou.sozo_tv.utils.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

sealed class DeviceLoginState {
    data object Creating : DeviceLoginState()

    data class Awaiting(
        val userCode: String,
        val qr: Bitmap?,
        val verificationUri: String,
        val secondsLeft: Int,
        @StringRes val noticeRes: Int? = null,
    ) : DeviceLoginState()

    /** Carries the linked account so an unexpected rebind by a second phone is visible. */
    data class Approved(val username: String) : DeviceLoginState()

    data object Expired : DeviceLoginState()

    /** Either a local reason ([messageRes]) or the server's own message — never both. */
    data class Failed(@StringRes val messageRes: Int?, val message: String?) : DeviceLoginState()
}

/**
 * Drives one pairing attempt: mint a code, poll it at the server-supplied cadence, and hand the
 * result to the screen. Everything that interprets an HTTP status lives in the repository; this
 * only decides how long to wait and when to give up.
 */
class DeviceLoginViewModel(private val repo: DeviceAuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<DeviceLoginState>(DeviceLoginState.Creating)
    val state: StateFlow<DeviceLoginState> get() = _state

    private var job: Job? = null
    private var autoRenewsUsed = 0

    private var pairing: DeviceCodeResponse? = null
    private var qr: Bitmap? = null
    private var deadlineMs = 0L

    /** [userInitiated] is the Refresh button, which also restores the auto-renew allowance. */
    fun start(userInitiated: Boolean = false) {
        if (userInitiated) autoRenewsUsed = 0
        launchSession(fresh = true)
    }

    /**
     * The reference is kept: [repo.poll] is NonCancellable, so a cancelled loop can still be
     * running for as long as the request's call timeout. Dropping the handle here would let
     * [resume] start a second loop against the same device_code, and the loser's 410 would
     * auto-renew a pairing on top of the winner's approval.
     */
    fun pause() {
        job?.cancel()
    }

    /**
     * Resumes the CURRENT pairing rather than minting a new one — the device-code bucket is far
     * tighter than the poll bucket, so backgrounding the screen must not burn a code.
     */
    fun resume() {
        // `isCompleted` rather than `isActive`: a cancelled-but-still-draining job is neither
        // active nor finished, and starting a rival loop while it drains is the race above.
        if (job?.isCompleted == false) return
        when (_state.value) {
            // Keyed on the pairing, not on the state: a code minted just before onStop is still
            // usable, and re-minting would burn the far tighter device-code bucket. If the
            // deadline passed while backgrounded, the poll loop reports it as an expiry, so the
            // renewal still costs an auto-renew rather than a free extra code.
            is DeviceLoginState.Awaiting,
            DeviceLoginState.Creating -> launchSession(fresh = pairing == null)
            // Terminal states wait for the Refresh button.
            else -> Unit
        }
    }

    /** Entry point for `onCreate`, which also runs on every activity recreation. */
    fun startIfIdle() {
        if (job == null && pairing == null) start()
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private fun launchSession(fresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            var needCode = fresh
            while (isActive) {
                if (needCode && !createPairing()) return@launch
                needCode = true
                if (!pollUntilExpiry()) return@launch
                if (autoRenewsUsed >= MAX_AUTO_RENEWS) {
                    _state.value = DeviceLoginState.Expired
                    return@launch
                }
                autoRenewsUsed++
            }
        }
    }

    /** @return false when a terminal state was published instead of a usable pairing. */
    private suspend fun createPairing(): Boolean {
        _state.value = DeviceLoginState.Creating
        pairing = null
        qr = null
        val code = when (val r = repo.createPairing()) {
            is ApiResult.Ok -> r.body

            is ApiResult.Http -> {
                _state.value = if (r.code == 429) {
                    DeviceLoginState.Failed(R.string.device_login_rate_limited, null)
                } else {
                    DeviceLoginState.Failed(null, r.message)
                }
                return false
            }

            is ApiResult.Network -> {
                _state.value = DeviceLoginState.Failed(R.string.device_login_no_internet, null)
                return false
            }
        }
        pairing = code
        deadlineMs = SystemClock.elapsedRealtime() + code.expiresIn * 1000L
        // A per-pixel setPixel loop over the whole matrix — on a low-end TV SoC that is visible
        // jank if it runs on the main thread.
        qr = withContext(Dispatchers.Default) {
            runCatching { QrCodeUtil.generate(LINK_BASE + code.userCode, QR_SIZE) }.getOrNull()
        }
        return true
    }

    /** @return true when the pairing merely expired, false when a terminal state was published. */
    private suspend fun pollUntilExpiry(): Boolean {
        val code = pairing ?: return false
        val baseDelayMs = code.interval.coerceAtLeast(1) * 1000L
        var delayMs = baseDelayMs
        var failStreak = 0
        var notice: Int? = null

        emitAwaiting(notice)
        while (coroutineContext.isActive) {
            if (SystemClock.elapsedRealtime() >= deadlineMs) return true
            delayTicking(delayMs) { emitAwaiting(notice) }

            when (val outcome = repo.poll(code.deviceCode)) {
                PollOutcome.Pending -> {
                    failStreak = 0
                    delayMs = baseDelayMs
                    notice = null
                }

                PollOutcome.Approved -> {
                    _state.value = DeviceLoginState.Approved(repo.session.value?.username.orEmpty())
                    return false
                }

                PollOutcome.Expired -> return true

                // The pairing survives a 429; only slow down, and honour the server's own hint.
                is PollOutcome.RateLimited -> {
                    delayMs = max(outcome.retryAfterMs, baseDelayMs)
                    notice = R.string.device_login_rate_limited
                }

                is PollOutcome.Transient -> {
                    if (++failStreak >= MAX_FAILS) {
                        _state.value = DeviceLoginState.Failed(null, outcome.message)
                        return false
                    }
                    delayMs = min(delayMs * 2, MAX_BACKOFF_MS)
                    notice = R.string.device_login_reconnecting
                }

                is PollOutcome.Fatal -> {
                    _state.value = DeviceLoginState.Failed(null, outcome.message)
                    return false
                }
            }
            emitAwaiting(notice)
        }
        return false
    }

    private fun emitAwaiting(@StringRes noticeRes: Int?) {
        val code = pairing ?: return
        val secondsLeft = ((deadlineMs - SystemClock.elapsedRealtime()) / 1000L).toInt()
        _state.value = DeviceLoginState.Awaiting(
            userCode = code.userCode,
            qr = qr,
            verificationUri = VERIFICATION_URI,
            secondsLeft = secondsLeft.coerceAtLeast(0),
            noticeRes = noticeRes,
        )
    }

    /** Sleeps [totalMs] in one-second slices so the countdown stays smooth between polls. */
    private suspend fun delayTicking(totalMs: Long, onTick: () -> Unit) {
        var remaining = totalMs
        while (remaining > 0) {
            val slice = min(TICK_MS, remaining)
            delay(slice)
            remaining -= slice
            onTick()
        }
    }

    private companion object {
        const val MAX_FAILS = 6
        const val MAX_BACKOFF_MS = 30_000L
        const val TICK_MS = 1_000L

        /** One silent renewal, then a D-pad press is required — the code bucket is 10/min per IP. */
        const val MAX_AUTO_RENEWS = 1

        /** 512 rather than the 900 default: the QR view is far smaller and the encode is O(n²). */
        const val QR_SIZE = 512

        val LINK_BASE: String = BuildConfig.SOZO_LINK_BASE_URL.trimEnd('/') + "/"

        /** The same URL without its scheme — readable on a panel from couch distance. */
        val VERIFICATION_URI: String = LINK_BASE
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }
}

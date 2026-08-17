package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.databinding.ActivityDeviceLoginBinding
import com.saikou.sozo_tv.presentation.viewmodel.DeviceLoginState
import com.saikou.sozo_tv.presentation.viewmodel.DeviceLoginViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The device-pairing screen: shows a typeable code plus a QR that encodes the same code, and
 * polls until a phone approves it. D-pad only — exactly two focus stops.
 */
class DeviceLoginActivity : AppCompatActivity() {

    private var _binding: ActivityDeviceLoginBinding? = null
    private val binding get() = _binding!!

    private val model: DeviceLoginViewModel by viewModel()
    private val historySync: WatchHistorySyncRepository by inject()
    private val settingsViewModel: SettingsViewModel by viewModel()

    // Approved is a sticky StateFlow value, so re-collecting it after a STARTED restart would
    // hand off twice.
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeviceLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        // A TV screensaver kicking in mid-pairing would strand the user on a dead code.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnRefresh.applyTvFocusScale()
        binding.btnBack.applyTvFocusScale()
        binding.btnRefresh.setOnClickListener { model.start(userInitiated = true) }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.requestFocus()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { render(it) } }
                launch {
                    settingsViewModel.seasonalTheme.collect { binding.root.setTheme(it) }
                }
            }
        }

        model.startIfIdle()
    }

    private fun render(state: DeviceLoginState) {
        when (state) {
            DeviceLoginState.Creating -> {
                binding.progress.visible()
                binding.tvStatus.setText(R.string.device_login_creating)
                clearPairingViews()
            }

            is DeviceLoginState.Awaiting -> {
                binding.progress.gone()
                binding.imgQr.setImageBitmap(state.qr)
                binding.tvUserCode.text = state.userCode
                binding.tvVerificationUri.text = state.verificationUri
                binding.tvCountdown.text = getString(
                    R.string.device_login_expires_in,
                    state.secondsLeft / 60,
                    state.secondsLeft % 60,
                )
                binding.tvStatus.setText(state.noticeRes ?: R.string.waiting_for_mobile)
            }

            is DeviceLoginState.Approved -> {
                binding.progress.gone()
                clearPairingViews()
                binding.tvStatus.text =
                    getString(R.string.device_login_signed_in_as, state.username)
                handOffToProfile()
            }

            DeviceLoginState.Expired -> {
                binding.progress.gone()
                clearPairingViews()
                binding.tvStatus.setText(R.string.device_login_expired)
                focusRefreshIfIdle()
            }

            is DeviceLoginState.Failed -> {
                binding.progress.gone()
                clearPairingViews()
                binding.tvStatus.text = state.messageRes?.let { getString(it) }
                    ?: state.message
                    ?: getString(R.string.device_login_failed)
                focusRefreshIfIdle()
            }
        }
    }

    private fun focusRefreshIfIdle() {
        if (binding.root.findFocus() == null) binding.btnRefresh.requestFocus()
    }

    /** A dead code left on screen next to "expired" reads as a code you can still scan. */
    private fun clearPairingViews() {
        binding.imgQr.setImageDrawable(null)
        binding.tvUserCode.text = ""
        binding.tvCountdown.text = ""
    }

    /** Holds the linked account on screen briefly so an unexpected rebind is actually readable. */
    private fun handOffToProfile() {
        if (handedOff) return
        historySync.syncAsync()
        lifecycleScope.launch {
            delay(SIGNED_IN_DWELL_MS)
            // Latched only once the start actually happens: a hand-off that lands while the
            // activity is backgrounded is dropped by the background-activity-start rules, and the
            // sticky Approved state must be able to drive a retry on the next onStart.
            if (handedOff || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@launch
            }
            handedOff = true
            startActivity(Intent(this@DeviceLoginActivity, ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finishDeferred()
        }
    }

    override fun onStart() {
        super.onStart()
        model.resume()
    }

    override fun onStop() {
        model.pause()
        super.onStop()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private companion object {
        const val SIGNED_IN_DWELL_MS = 2_000L
    }
}

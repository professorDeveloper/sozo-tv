package com.saikou.sozo_tv.presentation.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.saikou.sozo_tv.databinding.ActivityLoginWebBinding

import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.data.repository.AuthRepository
import com.saikou.sozo_tv.data.repository.TvPairingRepository
import com.saikou.sozo_tv.utils.QrCodeUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class QrLoginActivity : AppCompatActivity() {

    private var _binding: ActivityLoginWebBinding? = null
    private val binding get() = _binding!!

    private val scope = MainScope()

    private val pairingRepo: TvPairingRepository by inject()
    private val authRepo: AuthRepository by inject()

    private var currentSid: String? = null
    private var tokenListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { startPairing() }

        startPairing()
    }

    private fun startPairing() {
        setLoading(true, "Generating QR...")
        val oldSid = currentSid
        cleanupListener()

        if (!oldSid.isNullOrBlank()) {
            pairingRepo.deleteSession(oldSid)
        }

        currentSid = null

        val tvDeviceId = "tv-${android.os.Build.MODEL}"

        pairingRepo.createSession(tvDeviceId) { result ->
            result.onFailure { e ->
                setLoading(false, "Failed: ${e.message ?: "unknown"}")
            }
            result.onSuccess { session ->
                currentSid = session.sid

                binding.imgQr.setImageBitmap(QrCodeUtil.generate(session.qrPayload))
                setLoading(false, "Waiting for mobile...")

                tokenListener = pairingRepo.listenToken(
                    sid = session.sid,
                    onToken = { token ->
                        onTokenReceived(session.sid, token)
                    },
                    onError = { msg ->
                        setLoading(false, "Firebase error: $msg")
                    }
                )
            }
        }
    }


    private fun onTokenReceived(sid: String, token: String) {
        cleanupListener()
        setLoading(true, "Connecting...")

        scope.launch {
            when (val res = authRepo.handleTokenFromPairing(token)) {
                is AuthRepository.Result.Success -> {
                    pairingRepo.markPaired(sid)
                    setLoading(false, "Connected ✅")
                }
                is AuthRepository.Result.Error -> {
                    setLoading(false, "Failed: ${res.message}")
                }
            }
        }
    }


    private fun setLoading(loading: Boolean, status: String) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvStatus.text = status
    }

    private fun cleanupListener() {
        val sid = currentSid ?: return
        val listener = tokenListener ?: return
        pairingRepo.stopListenToken(sid, listener)
        tokenListener = null
    }

    override fun onDestroy() {
        cleanupListener()
        _binding = null
        super.onDestroy()
    }
}

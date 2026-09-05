package com.saikou.sozo_tv.presentation.screens.link

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.databinding.DialogExtensionLinkBinding
import com.saikou.sozo_tv.engine.link.ExtensionLinkServer
import com.saikou.sozo_tv.utils.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * "Add a source from your phone".
 *
 * The television is the wrong device to type a repository URL into — which is why the manual
 * shortcode installer was taken off the Sources screen, leaving no way at all to install a
 * repo that is not one of the four compiled into `ShortcodeRegistry`. This is that way back,
 * moved onto the device that has a keyboard.
 *
 * The server's lifetime is this dialog's, exactly: it binds in [onViewCreated] and closes in
 * [onDestroyView]. That is a security property, not tidiness — the endpoint it exposes ends
 * at code that downloads and loads a plugin, so it must not be listening while nobody is
 * looking at the screen that authorises it.
 *
 * Called back from a NanoHTTPD worker thread, never the main one, so every hop back into the
 * UI goes through [requireActivity].runOnUiThread.
 */
class ExtensionLinkDialog : DialogFragment() {

    private var _binding: DialogExtensionLinkBinding? = null
    private val binding get() = _binding!!

    private val engine: ExtensionEngine by inject()

    private var server: ExtensionLinkServer? = null

    /** Told when an install finishes, so the Sources list behind can refresh itself. */
    var onInstalled: (() -> Unit)? = null

    /** Told when the screen closes, however it closed — Back, Done, or the host going away. */
    var onClosed: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogExtensionLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(0xFF0B0D0F.toInt()))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnClose.requestFocus()

        val s = ExtensionLinkServer(onSubmit = ::install)
        server = s
        s.start()

        // Read as ABC-234 on screen: a six-character run is misread often enough at TV
        // distance that the split is worth the two characters it costs. codeMatches strips
        // the hyphen back out, so the viewer can type it either way.
        binding.tvCode.text = s.code.chunked(3).joinToString("-")

        val url = s.typedUrl()
        if (url == null) {
            // No usable address means the page cannot be reached at all. Say so instead of
            // printing a loopback address that leads nowhere.
            binding.tvAddress.text = "—"
            binding.imgQr.setImageDrawable(null)
            binding.tvStatus.text = getString(R.string.link_ext_no_network)
            return
        }
        binding.tvAddress.text = url
        binding.tvStatus.text = getString(R.string.link_ext_waiting)
        renderQr(s.pairingUrl() ?: url)
    }

    /**
     * A per-pixel setPixel loop over the whole matrix, which on a low-end TV SoC is visible
     * jank on the main thread — the same reason the device-login QR is built off it.
     */
    private fun renderQr(content: String) {
        lifecycleScope.launch {
            val bmp: Bitmap? = withContext(Dispatchers.Default) {
                runCatching { QrCodeUtil.generate(content, QR_SIZE) }.getOrNull()
            }
            if (_binding == null) return@launch
            binding.imgQr.setImageBitmap(bmp)
        }
    }

    /** Called on a server worker thread when the phone submits a link. */
    private fun install(group: String, url: String) {
        val s = server ?: return
        val activity = activity ?: return
        activity.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            binding.tvStatus.text = getString(R.string.link_ext_received, shortUrl(url))
            lifecycleScope.launch {
                val added = runCatching {
                    withContext(Dispatchers.IO) {
                        engine.addRepo(group, url) { current, total ->
                            s.publish(ExtensionLinkServer.Status.Installing(url, current, total))
                            activity.runOnUiThread {
                                if (_binding == null) return@runOnUiThread
                                if (total > 0) {
                                    binding.tvStatus.text =
                                        getString(R.string.link_ext_installing, current, total)
                                }
                            }
                        }
                    }
                }
                if (added.isFailure) {
                    val message = added.exceptionOrNull()?.message.orEmpty()
                    s.publish(ExtensionLinkServer.Status.Failed(url, message))
                    if (_binding == null) return@launch
                    binding.tvStatus.text = getString(R.string.link_ext_failed, message)
                    return@launch
                }
                // addRepo answers with SELECTABLE SOURCES, not plugins — a repo whose
                // plugins all failed to register returns 0, and saying "added" there is the
                // one report the phone must not get.
                val count = added.getOrDefault(0)
                s.publish(ExtensionLinkServer.Status.Installed(url, count))
                if (_binding == null) return@launch
                binding.tvStatus.text = if (count > 0) {
                    getString(R.string.link_ext_installed, count)
                } else {
                    getString(R.string.link_ext_installed_empty)
                }
                if (count > 0) onInstalled?.invoke()
            }
        }
    }

    private fun shortUrl(url: String): String =
        runCatching { android.net.Uri.parse(url).host ?: url }.getOrDefault(url)

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onClosed?.invoke()
    }

    override fun onDestroyView() {
        server?.stop()
        server = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val QR_SIZE = 700
        const val TAG = "ExtensionLinkDialog"
    }
}

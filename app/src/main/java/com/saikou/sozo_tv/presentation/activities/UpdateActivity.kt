package com.saikou.sozo_tv.presentation.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.components.spoiler.SpoilerPlugin
import com.saikou.sozo_tv.databinding.ActivityUpdateBinding
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.presentation.viewmodel.UpdateViewModel
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.snackString
import com.saikou.sozo_tv.utils.visible
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import org.koin.androidx.viewmodel.ext.android.viewModel

class UpdateActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_APP_LINK = "extra_app_link"
        private const val EXTRA_CHANGE_LOG = "extra_change_log"

        fun newIntent(context: Context, update: AppUpdate): Intent {
            return Intent(context, UpdateActivity::class.java).apply {
                putExtra(EXTRA_APP_LINK, update.appLink)
                putExtra(EXTRA_CHANGE_LOG, update.changeLog)
            }
        }
    }

    private lateinit var binding: ActivityUpdateBinding
    private val vm: UpdateViewModel by viewModel()

    private val appLink: String? by lazy { intent.getStringExtra(EXTRA_APP_LINK) }
    private val changeLog: String? by lazy { intent.getStringExtra(EXTRA_CHANGE_LOG) }

    // Android 13+ Notification permission (if you need it)
    private val askNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDownload()
        } else {
            snackString("Notification permission denied")
        }
    }

    // Unknown sources settings screen (we resume install after user returns)
    private val askUnknownSources = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // When user comes back from Settings, try install again if now allowed
        if (canInstallUnknownApps()) {
            vm.installApk(this)
        } else {
            snackString("Permission still not granted to install unknown apps")
        }
    }

    // Installer UI
    private val askInstall =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // RESULT_OK is not reliable on many TV installers, so don't treat as failure.
            Toast.makeText(this, "Returned from installer", Toast.LENGTH_SHORT).show()
            // If update installed, app may restart itself. If not, user can try again.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Basic UI init
        binding.progressView1.gone()
        binding.progressView1.progress = 0f
        binding.progressView1.labelText = "0%"
        binding.bottomSheerCustomTitle.text = "Update Available"
        binding.updateTxt.text = "Update Now"
        binding.updateBtn.isEnabled = true

        // Render changelog safely
        renderMarkdown(changeLog)

        // Observe VM states/events
        observeVm()

        // Single click handler (state-based)
        binding.updateBtn.setOnClickListener {
            when (vm.uiState.value) {
                is UpdateViewModel.UiState.DownloadComplete -> {
                    vm.installApk(this)
                }
                is UpdateViewModel.UiState.Downloading -> {
                    // ignore clicks while downloading
                }
                else -> {
                    // Start download
                    if (appLink.isNullOrBlank()) {
                        snackString("Update link is missing")
                        return@setOnClickListener
                    }
                    requestNotifPermissionIfNeededAndDownload()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.registerReceiver(this)
    }

    override fun onStop() {
        vm.unregisterReceiver(this)
        super.onStop()
    }

    private fun requestNotifPermissionIfNeededAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startDownload()
    }

    private fun startDownload() {
        val link = appLink ?: return
        binding.progressView1.visible()
        binding.updateBtn.gone()
        vm.startDownload(this, link)
    }

    private fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openUnknownSourcesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(
                Uri.parse("package:$packageName")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        askUnknownSources.launch(intent)
    }

    private fun renderMarkdown(md: String?) {
        val markwon = Markwon.builder(this)
            .usePlugin(HtmlPlugin.create { it.excludeDefaults(true) })
            .usePlugin(SpoilerPlugin())
            .build()

        markwon.setMarkdown(binding.markdownText, md ?: "No update information available.")
    }

    @SuppressLint("SetTextI18n")
    private fun observeVm() {
        vm.uiState.observe(this) { st ->
            when (st) {
                is UpdateViewModel.UiState.Idle -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Update Available"
                    binding.updateBtn.isEnabled = true
                    binding.updateTxt.text = "Update Now"
                    binding.updateBtn.visible()
                }

                is UpdateViewModel.UiState.Downloading -> {
                    binding.progressView1.visible()
                    binding.bottomSheerCustomTitle.text = "Downloading..."
                    binding.updateBtn.gone()
                    binding.progressView1.progress = st.progress.toFloat()
                    binding.progressView1.labelText = "${st.progress}%"

                    if (st.progress in 94..99) {
                        binding.bottomSheerCustomTitle.text = "Finishing download..."
                    }
                }

                is UpdateViewModel.UiState.DownloadComplete -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Download Complete!"
                    binding.updateBtn.apply {
                        isEnabled = true
                        visible()
                    }
                    binding.updateTxt.text = "Install Now"
                    snackString("Download completed successfully!")
                }

                is UpdateViewModel.UiState.DownloadFailed -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Download Failed"
                    binding.updateBtn.apply {
                        isEnabled = true
                        visible()
                    }
                    binding.updateTxt.text = "Try Again"
                    snackString("Download failed: ${st.error}")
                }
            }
        }

        vm.installEvent.observe(this) { ev ->
            when (ev) {
                is UpdateViewModel.InstallEvent.RequestUnknownSources -> {
                    snackString("Please allow 'Install unknown apps' for Sozo TV")
                    openUnknownSourcesSettings()
                }

                is UpdateViewModel.InstallEvent.StartInstall -> {
                    try {
                        askInstall.launch(ev.intent)
                    } catch (e: Exception) {
                        snackString("Cannot open installer: ${e.message}")
                    }
                }

                is UpdateViewModel.InstallEvent.Error -> {
                    snackString(ev.message)
                }
            }
        }
    }
}

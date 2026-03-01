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
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
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
        private const val EXTRA_APP_IMG = "extra_app_img"
        private const val EXTRA_CHANGE_LOG = "extra_change_log"

        private const val PV_MIN = 0f
        private const val PV_MAX = 1000f

        fun newIntent(context: Context, update: AppUpdate): Intent {
            return Intent(context, UpdateActivity::class.java).apply {
                putExtra(EXTRA_APP_LINK, update.appLink)
                putExtra(EXTRA_APP_LINK, update.appLink)
                putExtra(EXTRA_APP_IMG, update.imageLink)
                putExtra(EXTRA_CHANGE_LOG, update.changeLog)
            }
        }
    }

    private lateinit var binding: ActivityUpdateBinding
    private val vm: UpdateViewModel by viewModel()

    private val appLink: String? by lazy { intent.getStringExtra(EXTRA_APP_LINK) }
    private val appImg: String? by lazy { intent.getStringExtra(EXTRA_APP_IMG) }
    private val changeLog: String? by lazy { intent.getStringExtra(EXTRA_CHANGE_LOG) }

    private val askNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startDownload() else snackString("Notification permission denied")
    }

    private val askUnknownSources = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (canInstallUnknownApps()) {
            vm.installApk(this)
        } else {
            snackString("Permission still not granted to install unknown apps")
        }
    }

    private val askInstall =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        binding.progressView1.apply {
            min = PV_MIN
            max = PV_MAX
            progressFromPrevious = true
            autoAnimate = true
        }

        binding.progressView1.gone()
        setProgressUi(0)

        binding.bottomSheerCustomTitle.text = "Update Available"
        binding.updateTxt.text = "Update Now"
        binding.updateBtn.isEnabled = true
        binding.updateBtn.visible()

        renderMarkdown(changeLog, appImg)
        observeVm()

        binding.updateBtn.setOnClickListener {
            when (vm.uiState.value) {
                is UpdateViewModel.UiState.DownloadComplete -> {
                    if (!canInstallUnknownApps()) {
                        snackString("Please allow 'Install unknown apps' for Sozo TV")
                        openUnknownSourcesSettings()
                    } else {
                        vm.installApk(this)
                    }
                }

                is UpdateViewModel.UiState.Downloading -> {
                }

                else -> {
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

    override fun onDestroy() {
        super.onDestroy()
        vm.cleanup()
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
        } else true
    }

    private fun openUnknownSourcesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }

        runCatching { askUnknownSources.launch(intent) }
            .onFailure { snackString("Cannot open settings: ${it.message}") }
    }

    private fun renderMarkdown(md: String?, imageUrl: String? = null) {
        if (!imageUrl.isNullOrEmpty()) {
            binding.updatePreviewImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .into(binding.updatePreviewImage)
        }

        val markwon = Markwon.builder(this)
            .usePlugin(HtmlPlugin.create { it.excludeDefaults(true) })
            .usePlugin(SpoilerPlugin())
            .build()

        markwon.setMarkdown(binding.markdownText, md ?: "No update information available.")
    }

    /**
     * progress1000: 0..1000
     * label: 84.4%
     */
    private fun percentText(progress1000: Int): String {
        val p = progress1000.coerceIn(0, 1000) / 10.0
        return String.format("%.1f%%", p)
    }

    /**
     * Skydoves ProgressView to'g'ri ishlashi uchun:
     * - progressView.max = 1000
     * - progressView.progress = 0..1000
     */
    private fun setProgressUi(progress1000: Int) {
        val clamped = progress1000.coerceIn(0, 1000)

        binding.progressView1.progress = clamped.toFloat()
        binding.progressView1.labelText = percentText(clamped)

        binding.bottomSheerCustomTitle.text =
            if (clamped in 900..999) "Finishing download..." else "Downloading..."
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
                    binding.updateBtn.gone()
                    setProgressUi(st.progress1000)
                }

                is UpdateViewModel.UiState.DownloadComplete -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Download Complete!"
                    binding.updateBtn.visible()
                    binding.updateBtn.isEnabled = true
                    binding.updateTxt.text = "Install Now"

                    if (!canInstallUnknownApps()) {
                        snackString("Please allow 'Install unknown apps' for Sozo TV")
                        openUnknownSourcesSettings()
                    } else {
                        vm.installApk(this)
                    }
                }

                is UpdateViewModel.UiState.DownloadFailed -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Download Failed"
                    binding.updateBtn.visible()
                    binding.updateBtn.isEnabled = true
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
                    runCatching { askInstall.launch(ev.primary) }
                        .onFailure {
                            runCatching { askInstall.launch(ev.fallback) }
                                .onFailure { e2 ->
                                    snackString("Installer cannot be opened: ${e2.message}")
                                }
                        }
                }

                is UpdateViewModel.InstallEvent.Error -> snackString(ev.message)
            }
        }

        vm.downloadProgress.observe(this) { p1000 ->
            if (p1000 in 0..1000) setProgressUi(p1000)
        }
    }
}

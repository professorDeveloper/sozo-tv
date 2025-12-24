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
import org.koin.androidx.viewmodel.ext.android.viewModel

class UpdateActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_WRITE_STORAGE = 1001

        var _update: AppUpdate? = null
        fun newIntent(context: Context, update: AppUpdate): Intent {
            _update = update
            return Intent(context, UpdateActivity::class.java)
        }
    }

    private val update get() = _update!!

    private lateinit var binding: ActivityUpdateBinding
    private val vm: UpdateViewModel by viewModel()

    private val askNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startDownload(this, update.appLink ?: "")
        else snackString("Notification permission denied")
    }

    private val askInstall =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Installation completed!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                snackString("Installation cancelled")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressView1.gone()
        binding.progressView1.progress = 0f
        binding.progressView1.labelText = "0%"
        binding.bottomSheerCustomTitle.text = "Update Available"
        binding.updateTxt.text = "Update Now"
        binding.updateBtn.isEnabled = true
        renderMarkdown(update.changeLog)
        observeVm()
        binding.updateBtn.setOnClickListener {
            val link = update.appLink ?: return@setOnClickListener
            if (vm.uiState.value is UpdateViewModel.UiState.DownloadComplete) {
                vm.installApk(this)
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                binding.progressView1.visible()
                binding.updateBtn.gone()
                vm.startDownload(this, link)
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

    private fun renderMarkdown(md: String?) {
        val markwon = Markwon.builder(this)
            .usePlugin(io.noties.markwon.html.HtmlPlugin.create { it.excludeDefaults(true) })
            .usePlugin(SpoilerPlugin()).build()
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

                    // Install tugmasini qayta o'rnatish
                    binding.updateBtn.setOnClickListener {
                        vm.installApk(this@UpdateActivity)
                    }

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

                    // Try Again tugmasini qayta o'rnatish
                    binding.updateBtn.setOnClickListener {
                        vm.startDownload(this@UpdateActivity, update.appLink ?: "")
                    }

                    snackString("Download failed: ${st.error}")
                }
            }
        }

        vm.installEvent.observe(this) { ev ->
            when (ev) {
                is UpdateViewModel.InstallEvent.RequestUnknownSources -> {
                    snackString("Please allow 'Install unknown apps' for Sozo TV")

                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:$packageName"))
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
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
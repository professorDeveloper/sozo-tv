package com.saikou.sozo_tv.presentation.activities

import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.saikou.sozo_tv.R


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.components.spoiler.SpoilerPlugin
import com.saikou.sozo_tv.databinding.ActivityUpdateBinding
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.snackString
import com.saikou.sozo_tv.utils.visible
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

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

    private val askUnknown =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.canRequestPackageInstalls()
            ) vm.installApk(this)
            else snackString("Cannot install from unknown sources")
        }

    private val askInstall =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finish()
            else snackString("Installation cancelled")
        }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderMarkdown(update.changeLog)
        observeVm()

        binding.updateBtn.setOnClickListener {
            val link = update.appLink ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            else vm.startDownload(this, link)
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
            .usePlugin(SpoilerPlugin())
            .build()
        markwon.setMarkdown(binding.markdownText, md ?: "")
    }

    private fun observeVm() {
        vm.uiState.observe(this) { st ->
            when (st) {
                is UpdateViewModel.UiState.Idle -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Update Available"
                    binding.updateBtn.apply {
                        binding.updateTxt.text = "Update Now"
                        visible()
                    }
                }

                is UpdateViewModel.UiState.Downloading -> {
                    binding.progressView1.visible()
                    binding.bottomSheerCustomTitle.text = "Downloading…"
                    binding.updateBtn.gone()
                    binding.progressView1.apply {
                        progress = st.progress.toFloat()
                        labelText = "${st.progress}%"
                    }
                }

                is UpdateViewModel.UiState.DownloadComplete -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Downloaded!"
                    binding.updateBtn.apply {
                        binding.updateTxt.text = "Install Now"
                        visible()
                        setOnClickListener { vm.installApk(this@UpdateActivity) }
                    }
                }

                is UpdateViewModel.UiState.DownloadFailed -> {
                    binding.progressView1.gone()
                    binding.bottomSheerCustomTitle.text = "Failed"
                    binding.updateBtn.apply {
                        binding.updateTxt.text = "Try Again"
                        visible()
                        setOnClickListener {
                            vm.startDownload(this@UpdateActivity, update.appLink ?: "")
                        }
                    }
                    snackString(st.error)
                }
            }
        }

        vm.installEvent.observe(this) { ev ->
            when (ev) {
                is UpdateViewModel.InstallEvent.RequestUnknownSources -> {
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:$packageName"))
                    askUnknown.launch(i)
                }

                is UpdateViewModel.InstallEvent.StartInstall -> askInstall.launch(ev.intent)
                is UpdateViewModel.InstallEvent.Error -> snackString(ev.message)
            }
        }
    }
}

class UpdateViewModel : ViewModel() {
    sealed class UiState {
        object Idle : UiState()
        data class Downloading(val progress: Int) : UiState()
        object DownloadComplete : UiState()
        data class DownloadFailed(val error: String) : UiState()
    }

    sealed class InstallEvent {
        object RequestUnknownSources : InstallEvent()
        data class StartInstall(val intent: Intent) : InstallEvent()
        data class Error(val message: String) : InstallEvent()
    }

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _installEvent = MutableLiveData<InstallEvent>()
    val installEvent: LiveData<InstallEvent> = _installEvent

    private var downloadId = -1L
    private var downloadManager: DownloadManager? = null
    private var progressJob: Job? = null

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) checkDownloadStatus()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(ctx: Context) {
        ctx.registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    fun unregisterReceiver(ctx: Context) {
        runCatching { ctx.unregisterReceiver(onDownloadComplete) }
    }

    fun startDownload(context: Context, apkUrl: String) {
        _uiState.value = UiState.Downloading(0)
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // 1) prepare your “updates” folder
        val updatesDir = File(context.getExternalFilesDir(null), "IPSAT")
            .apply {
                deleteRecursively()
                mkdirs()
            }

        // 2) name your new apk
        val apkFile = File(updatesDir, "app_update_${System.currentTimeMillis()}.apk")

        // 3) enqueue it
        val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("IPSAT Update")
            setDescription("Downloading new version…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(apkFile))
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
            )
            setAllowedOverRoaming(false)
            setVisibleInDownloadsUi(true)
        }

        downloadId = downloadManager!!.enqueue(req)
        trackProgress()
    }

    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val cursor = downloadManager
                    ?.query(DownloadManager.Query().setFilterById(downloadId))
                cursor?.use {
                    if (it.moveToFirst()) {
                        when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_RUNNING -> {
                                val done = it.getLong(
                                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                )
                                val total = it.getLong(
                                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                )
                                if (total > 0) {
                                    val p = ((done * 100) / total).toInt()
                                    _uiState.postValue(UiState.Downloading(p))
                                }
                            }

                            DownloadManager.STATUS_PENDING -> {
                                _uiState.postValue(UiState.Downloading(0))
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun checkDownloadStatus() {
        progressJob?.cancel()
        downloadManager
            ?.query(DownloadManager.Query().setFilterById(downloadId))
            ?.use { c ->
                if (c.moveToFirst()) {
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _uiState.postValue(UiState.DownloadComplete)
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reason =
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            _uiState.postValue(
                                UiState.DownloadFailed("Download failed. Code: $reason")
                            )
                        }
                    }
                }
            }
    }

    fun installApk(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                _installEvent.postValue(InstallEvent.RequestUnknownSources)
                return
            }

            val updatesDir = File(context.getExternalFilesDir(null), "IPSAT")
            val apkFile = updatesDir
                .listFiles { _, name -> name.startsWith("app_update") && name.endsWith(".apk") }
                ?.maxByOrNull { it.lastModified() }

            if (apkFile == null || !apkFile.exists()) {
                _installEvent.postValue(InstallEvent.Error("APK not found"))
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            _installEvent.postValue(InstallEvent.StartInstall(intent))
        } catch (e: Exception) {
            Log.e("UpdateVM", "installApk", e)
            _installEvent.postValue(
                InstallEvent.Error("Installation failed: ${e.localizedMessage}")
            )
        }
    }

}
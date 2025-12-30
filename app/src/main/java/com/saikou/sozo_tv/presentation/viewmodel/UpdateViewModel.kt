package com.saikou.sozo_tv.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel : ViewModel() {

    sealed class UiState {
        object Idle : UiState()

        /**
         * progress = 0..1000 (ya'ni 0.0%..100.0%)
         */
        data class Downloading(val progress1000: Int) : UiState()

        object DownloadComplete : UiState()
        data class DownloadFailed(val error: String) : UiState()
    }

    sealed class InstallEvent {
        object RequestUnknownSources : InstallEvent()
        data class StartInstall(val primary: Intent, val fallback: Intent) : InstallEvent()
        data class Error(val message: String) : InstallEvent()
    }

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _installEvent = MutableLiveData<InstallEvent>()
    val installEvent: LiveData<InstallEvent> = _installEvent

    /**
     * progress = 0..1000
     */
    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int> = _downloadProgress

    private var downloadId = -1L
    private var downloadManager: DownloadManager? = null
    private var progressJob: Job? = null
    private var lastApkFile: File? = null
    private var isDownloadCompleted = false

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId && !isDownloadCompleted) {
                Log.d("UpdateVM", "Download complete broadcast received for ID: $id")
                checkDownloadStatus()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(
                    onDownloadComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                ctx.registerReceiver(
                    onDownloadComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
            Log.d("UpdateVM", "Broadcast receiver registered")
        } catch (e: Exception) {
            Log.e("UpdateVM", "Failed to register receiver", e)
        }
    }

    fun unregisterReceiver(ctx: Context) {
        runCatching { ctx.unregisterReceiver(onDownloadComplete) }
            .onSuccess { Log.d("UpdateVM", "Broadcast receiver unregistered") }
            .onFailure { Log.e("UpdateVM", "Failed to unregister receiver", it) }
    }

    /**
     * ✅ SAFE destination for TV + Phone:
     * context.getExternalFilesDir(DOWNLOADS)/SozoTV/<file>.apk
     */
    fun startDownload(context: Context, apkUrl: String) {
        Log.d("UpdateVM", "Starting download from: $apkUrl")

        _uiState.value = UiState.Downloading(0)
        _downloadProgress.value = 0
        isDownloadCompleted = false

        try {
            downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val timestamp = System.currentTimeMillis()
            val apkFileName = "sozo_tv_update_$timestamp.apk"

            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External files dir is null")

            val sozoDir = File(baseDir, "SozoTV").apply { if (!exists()) mkdirs() }

            // eski apk larni tozalash
            sozoDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("sozo_tv_update_") && file.name.endsWith(".apk")) {
                    runCatching { file.delete() }
                }
            }

            // bizda file path aniq bo‘lishi uchun
            val apkFile = File(sozoDir, apkFileName)
            lastApkFile = apkFile

            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Sozo TV Update")
                setDescription("Downloading new version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverRoaming(false)
                setVisibleInDownloadsUi(false)

                setMimeType("application/vnd.android.package-archive")
                setRequiresDeviceIdle(false)

                // ✅ MUHIM FIX
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "SozoTV/$apkFileName"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setRequiresCharging(false)
                }
            }

            downloadId = downloadManager!!.enqueue(req)
            Log.d("UpdateVM", "Download started with ID: $downloadId")
            trackProgress()

        } catch (e: Exception) {
            Log.e("UpdateVM", "Failed to start download", e)
            _uiState.value = UiState.DownloadFailed("Failed to start download: ${e.message}")
        }
    }

    @SuppressLint("Range")
    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            var shouldContinue = true

            while (isActive && shouldContinue) {
                val cursor = downloadManager?.query(
                    DownloadManager.Query().setFilterById(downloadId)
                )

                cursor?.use { c ->
                    if (!c.moveToFirst()) {
                        delay(800)
                        return@use
                    }

                    val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val done =
                                c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total =
                                c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                            val progress1000 =
                                if (total > 0) ((done * 1000L) / total).toInt() else 0

                            _downloadProgress.postValue(progress1000.coerceIn(0, 1000))
                            _uiState.postValue(UiState.Downloading(progress1000.coerceIn(0, 1000)))

                            delay(if (progress1000 >= 940) 300 else 600)
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _downloadProgress.postValue(1000)
                            _uiState.postValue(UiState.DownloadComplete)
                            isDownloadCompleted = true
                            shouldContinue = false
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON))
                            val msg = dmReasonToText(reason)
                            Log.e("UpdateVM", "Download failed: $msg")
                            _uiState.postValue(UiState.DownloadFailed(msg))
                            shouldContinue = false
                        }

                        DownloadManager.STATUS_PENDING -> delay(800)
                        DownloadManager.STATUS_PAUSED -> delay(1200)
                    }
                } ?: delay(800)
            }
        }
    }

    @SuppressLint("Range")
    private fun checkDownloadStatus() {
        val cursor = downloadManager?.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use { c ->
            if (c.moveToFirst()) {
                when (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _downloadProgress.postValue(1000)
                        _uiState.postValue(UiState.DownloadComplete)
                        isDownloadCompleted = true
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON))
                        val msg = dmReasonToText(reason)
                        _uiState.postValue(UiState.DownloadFailed(msg))
                        isDownloadCompleted = true
                    }
                }
            }
        }
    }

    private fun dmReasonToText(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP code error"
            else -> "Error code: $reason"
        }
    }

    fun getDownloadedFile(): File? {
        return lastApkFile?.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * ✅ install - primary + fallback intent
     */
    fun installApk(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    triggerInstallEvent(InstallEvent.RequestUnknownSources)
                    return
                }
            }

            val apkFile = getDownloadedFile()
            if (apkFile == null) {
                triggerInstallEvent(InstallEvent.Error("Downloaded APK not found. Download again."))
                return
            }

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val primary = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            triggerInstallEvent(InstallEvent.StartInstall(primary, fallback))

        } catch (e: Exception) {
            Log.e("UpdateVM", "installApk error", e)
            triggerInstallEvent(
                InstallEvent.Error("Installation failed: ${e.localizedMessage ?: "Unknown error"}")
            )
        }
    }

    fun triggerInstallEvent(event: InstallEvent) {
        _installEvent.postValue(event)
    }

    fun cleanup() {
        progressJob?.cancel()
        downloadManager = null
        lastApkFile = null
        isDownloadCompleted = false
        _uiState.value = UiState.Idle
        _downloadProgress.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}

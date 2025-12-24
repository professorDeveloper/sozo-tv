package com.saikou.sozo_tv.presentation.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
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
                    Context.RECEIVER_EXPORTED
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
        runCatching {
            ctx.unregisterReceiver(onDownloadComplete)
            Log.d("UpdateVM", "Broadcast receiver unregistered")
        }.onFailure { e ->
            Log.e("UpdateVM", "Failed to unregister receiver", e)
        }
    }

    @SuppressLint("Range")
    fun startDownload(context: Context, apkUrl: String) {
        Log.d("UpdateVM", "Starting download from: $apkUrl")
        _uiState.value = UiState.Downloading(0)
        isDownloadCompleted = false

        try {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Fayl nomini yaratish
            val timestamp = System.currentTimeMillis()
            val apkFileName = "sozo_tv_update_$timestamp.apk"

            // Fayl manzilini belgilash
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val sozoDir = File(downloadsDir, "SozoTV").apply {
                if (!exists()) mkdirs()
            }

            val apkFile = File(sozoDir, apkFileName)
            lastApkFile = apkFile

            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Sozo TV Update")
                setDescription("Downloading new version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(apkFile))
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)
                setVisibleInDownloadsUi(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setRequiresCharging(false)
                }

                // MIME turi
                setMimeType("application/vnd.android.package-archive")
            }

            downloadId = downloadManager!!.enqueue(req)
            Log.d("UpdateVM", "Download started with ID: $downloadId")
            Log.d("UpdateVM", "APK will be saved to: ${apkFile.absolutePath}")

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
            var lastProgress = -1
            var stuckCount = 0
            val maxStuckCount = 10
            var shouldContinue = true

            while (isActive && shouldContinue) {
                val cursor = downloadManager?.query(DownloadManager.Query().setFilterById(downloadId))
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS))

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                val done = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                val total = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                                if (total > 0) {
                                    val progress = ((done * 100) / total).toInt()

                                    // Progress qancha vaqt bir xil bo'lib qolganligini tekshirish
                                    if (progress == lastProgress) {
                                        stuckCount++
                                        if (stuckCount >= maxStuckCount && progress >= 94) {
                                            // 94% da 10 soniya qolib ketsa, muvaffaqiyatli deb hisoblash
                                            Log.w("UpdateVM", "Download stuck at $progress% for too long, marking as complete")
                                            _uiState.postValue(UiState.DownloadComplete)
                                            isDownloadCompleted = true
                                            shouldContinue = false
                                        }
                                    } else {
                                        stuckCount = 0
                                        lastProgress = progress
                                    }

                                    _uiState.postValue(UiState.Downloading(progress))
                                    Log.d("UpdateVM", "Download progress: $progress% ($done/$total)")

                                    // 94% da qolib ketishni oldini olish
                                    if (progress >= 94 && progress < 100) {
                                        delay(2000)
                                    }
                                }
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.d("UpdateVM", "Download status: SUCCESSFUL")
                                _uiState.postValue(UiState.DownloadComplete)
                                isDownloadCompleted = true
                                shouldContinue = false
                            }

                            DownloadManager.STATUS_FAILED -> {
                                val reason = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_REASON))
                                val errorMsg = when (reason) {
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
                                Log.e("UpdateVM", "Download failed: $errorMsg")
                                _uiState.postValue(UiState.DownloadFailed(errorMsg))
                                shouldContinue = false
                            }

                            DownloadManager.STATUS_PENDING -> {
                                _uiState.postValue(UiState.Downloading(0))
                            }
                        }
                    }
                }

                if (shouldContinue) {
                    delay(1000)
                }
            }

            // Coroutine tugaganda
            Log.d("UpdateVM", "Progress tracking stopped")
        }
    }

    @SuppressLint("Range")
    private fun checkDownloadStatus() {
        progressJob?.cancel()
        val cursor = downloadManager?.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use { c ->
            if (c.moveToFirst()) {
                when (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d("UpdateVM", "Download completed successfully (from broadcast)")
                        _uiState.postValue(UiState.DownloadComplete)
                        isDownloadCompleted = true
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON))
                        val errorMsg = when (reason) {
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
                        Log.e("UpdateVM", "Download failed in broadcast: $errorMsg")
                        _uiState.postValue(UiState.DownloadFailed(errorMsg))
                        isDownloadCompleted = true
                    }
                }
            }
        }
    }

    fun installApk(context: Context) {
        try {
            Log.d("UpdateVM", "Starting APK installation...")

            // 1. Avval ruxsatlarni tekshirish
            val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            Log.d("UpdateVM", "Is TV device: $isTv")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canInstall = context.packageManager.canRequestPackageInstalls()
                Log.d("UpdateVM", "Can request package installs: $canInstall")

                if (!canInstall) {
                    _installEvent.postValue(InstallEvent.RequestUnknownSources)
                    return
                }
            }

            // 2. APK faylini topish
            val apkFile = lastApkFile ?: run {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val sozoDir = File(downloadsDir, "SozoTV")

                if (!sozoDir.exists() || !sozoDir.isDirectory) {
                    null
                } else {
                    sozoDir.listFiles { _, name ->
                        name.startsWith("sozo_tv_update") && name.endsWith(".apk")
                    }?.maxByOrNull { it.lastModified() }
                }
            }

            if (apkFile == null || !apkFile.exists()) {
                Log.e("UpdateVM", "APK file not found")
                _installEvent.postValue(InstallEvent.Error("APK file not found. Please download again."))
                return
            }

            Log.d("UpdateVM", "APK file found: ${apkFile.absolutePath}")
            Log.d("UpdateVM", "APK file size: ${apkFile.length()} bytes")
            Log.d("UpdateVM", "APK file last modified: ${apkFile.lastModified()}")

            // 3. FileProvider orqali URI olish
            val authority = "${context.packageName}.fileprovider"
            Log.d("UpdateVM", "Using FileProvider authority: $authority")

            val uri = try {
                FileProvider.getUriForFile(context, authority, apkFile)
            } catch (e: Exception) {
                Log.e("UpdateVM", "FileProvider error: ${e.message}", e)
                // FileProvider ishlamasa, fallback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7+ da file:// ishlatib bo'lmaydi
                    throw e
                } else {
                    Uri.fromFile(apkFile)
                }
            }

            Log.d("UpdateVM", "APK URI: $uri")

            // 4. O'rnatish uchun Intent yaratish
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // TV uchun qo'shimcha flag'lar
                if (isTv) {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                // Qayta o'rnatishga ruxsat berish
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            _installEvent.postValue(InstallEvent.StartInstall(installIntent))

        } catch (e: Exception) {
            Log.e("UpdateVM", "installApk error", e)
            _installEvent.postValue(
                InstallEvent.Error("Installation failed: ${e.localizedMessage ?: "Unknown error"}")
            )
        }
    }
}
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

    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int> = _downloadProgress

    private var downloadId = -1L
    private var downloadManager: DownloadManager? = null
    private var progressJob: Job? = null
    private var lastApkFile: File? = null
    private var isDownloadCompleted = false
    private var lastKnownProgress = 0

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
        _downloadProgress.value = 0
        isDownloadCompleted = false
        lastKnownProgress = 0

        try {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Fayl nomini yaratish
            val timestamp = System.currentTimeMillis()
            val apkFileName = "sozo_tv_update_$timestamp.apk"

            // TV uchun fayl manzilini belgilash
            val destinationDir = if (isTvDevice(context)) {
                // TV uchun cache directory ishlatamiz
                File(context.cacheDir, "downloads").apply {
                    if (!exists()) mkdirs()
                }
            } else {
                // Telefon uchun Downloads papkasini ishlatamiz
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }

            val sozoDir = File(destinationDir, "SozoTV").apply {
                if (!exists()) mkdirs()
            }

            // Eski fayllarni tozalash
            sozoDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("sozo_tv_update_") && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }

            val apkFile = File(sozoDir, apkFileName)
            lastApkFile = apkFile

            Log.d("UpdateVM", "Destination file: ${apkFile.absolutePath}")

            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Sozo TV Update")
                setDescription("Downloading new version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // TV va Android versiyasiga qarab destination belgilash
                if (isTvDevice(context)) {
                    // TV uchun cache directory ishlatamiz
                    setDestinationUri(Uri.fromFile(apkFile))
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ uchun
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SozoTV/$apkFileName")
                    } else {
                        setDestinationUri(Uri.fromFile(apkFile))
                    }
                }

                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)
                setVisibleInDownloadsUi(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setRequiresCharging(false)
                }

                // MIME turi
                setMimeType("application/vnd.android.package-archive")

                // DownloadManager'ni sozlamalari
                setRequiresDeviceIdle(false)
            }

            downloadId = downloadManager!!.enqueue(req)
            Log.d("UpdateVM", "Download started with ID: $downloadId")

            trackProgress()

        } catch (e: Exception) {
            Log.e("UpdateVM", "Failed to start download", e)
            _uiState.value = UiState.DownloadFailed("Failed to start download: ${e.message}")
        }
    }

    private fun isTvDevice(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    @SuppressLint("Range")
    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            var lastProgress = -1
            var stuckCount = 0
            val maxStuckCount = 30 // 30 soniya (30 * 1000ms) kutamiz
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
                                    lastKnownProgress = progress

                                    // Progressni yangilash
                                    _downloadProgress.postValue(progress)
                                    _uiState.postValue(UiState.Downloading(progress))

                                    // Progress qancha vaqt bir xil bo'lib qolganligini tekshirish
                                    if (progress == lastProgress) {
                                        stuckCount++
                                        Log.d("UpdateVM", "Progress stuck at $progress%, count: $stuckCount")

                                        // Agar 94% dan yuqorida va uzoq vaqt qolib ketsa
                                        if (progress >= 94 && stuckCount >= maxStuckCount) {
                                            Log.w("UpdateVM", "Download stuck at $progress% for too long, checking file")

                                            // Fayl mavjudligini tekshirish
                                            lastApkFile?.let { file ->
                                                if (file.exists() && file.length() > 0) {
                                                    // Fayl mavjud va bo'sh emas, download bajarilgan deb hisoblash
                                                    Log.w("UpdateVM", "File exists with size: ${file.length()} bytes")
                                                    _downloadProgress.postValue(100)
                                                    _uiState.postValue(UiState.DownloadComplete)
                                                    isDownloadCompleted = true
                                                    shouldContinue = false
                                                    return@launch
                                                }
                                            }
                                        }
                                    } else {
                                        stuckCount = 0
                                        lastProgress = progress
                                    }

                                    Log.d("UpdateVM", "Download progress: $progress% ($done/$total)")

                                    // 94% dan yuqorida bo'lsa, tekshirish chastotasini oshiramiz
                                    if (progress >= 94 && progress < 100) {
                                        delay(500) // 0.5 soniya
                                    } else {
                                        delay(1000) // 1 soniya
                                    }
                                } else {
                                    // Total size noma'lum
                                    _downloadProgress.postValue(0)
                                    delay(1000)
                                }
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.d("UpdateVM", "Download status: SUCCESSFUL")
                                _downloadProgress.postValue(100)
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

                            DownloadManager.STATUS_PAUSED -> {
                                Log.d("UpdateVM", "Download paused")
                                delay(2000)
                            }

                            DownloadManager.STATUS_PENDING -> {
                                _downloadProgress.postValue(0)
                                _uiState.postValue(UiState.Downloading(0))
                                delay(1000)
                            }
                        }
                    } else {
                        // Cursor bo'sh
                        Log.d("UpdateVM", "Cursor is empty")
                        delay(2000)
                    }
                } ?: run {
                    // Cursor null
                    Log.d("UpdateVM", "Cursor is null")
                    delay(2000)
                }
            }

            Log.d("UpdateVM", "Progress tracking stopped")
        }
    }

    @SuppressLint("Range")
    private fun checkDownloadStatus() {
        val cursor = downloadManager?.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use { c ->
            if (c.moveToFirst()) {
                when (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d("UpdateVM", "Download completed successfully (from broadcast)")
                        _downloadProgress.postValue(100)
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

            // Unknown sources ruxsatini tekshirish
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canInstall = context.packageManager.canRequestPackageInstalls()
                Log.d("UpdateVM", "Can request package installs: $canInstall")

                if (!canInstall) {
                    triggerInstallEvent(InstallEvent.RequestUnknownSources)
                    return
                }
            }

            // APK faylini topish
            val apkFile = getDownloadedFile()
            if (apkFile == null || !apkFile.exists()) {
                Log.e("UpdateVM", "APK file not found")
                triggerInstallEvent(InstallEvent.Error("APK file not found. Please download again."))
                return
            }

            Log.d("UpdateVM", "APK file found: ${apkFile.absolutePath}, size: ${apkFile.length()} bytes")

            // URI yaratish
            val authority = "${context.packageName}.provider"
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, authority, apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            Log.d("UpdateVM", "APK URI: $uri")

            // O'rnatish uchun Intent yaratish
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // TV uchun qo'shimcha flag'lar
                if (isTvDevice(context)) {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                // Qayta o'rnatishga ruxsat berish
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }

            triggerInstallEvent(InstallEvent.StartInstall(installIntent))

        } catch (e: Exception) {
            Log.e("UpdateVM", "installApk error", e)
            triggerInstallEvent(
                InstallEvent.Error("Installation failed: ${e.localizedMessage ?: "Unknown error"}")
            )
        }
    }

    fun getDownloadedFile(): File? {
        return lastApkFile?.takeIf { it.exists() && it.length() > 0 }
    }

    fun triggerInstallEvent(event: InstallEvent) {
        _installEvent.postValue(event)
    }

    fun cleanup() {
        progressJob?.cancel()
        downloadManager = null
        lastApkFile = null
        isDownloadCompleted = false
        lastKnownProgress = 0
        _uiState.value = UiState.Idle
        _downloadProgress.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
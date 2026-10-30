package com.saikou.sozo_tv.engine.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.presentation.activities.DownloadsActivity

@UnstableApi
class TvDownloadService : DownloadService(7301, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "tv_downloads", R.string.tv_downloads_title, 0) {
    override fun getDownloadManager(): DownloadManager = TvDownloads.manager(this)
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, DownloadsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return DownloadNotificationHelper(this, "tv_downloads").buildProgressNotification(
            this, android.R.drawable.stat_sys_download, pendingIntent, getString(R.string.tv_downloads_title),
            downloads, notMetRequirements)
    }
}

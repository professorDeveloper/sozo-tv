package com.saikou.sozo_tv.debug

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.video.PlaceholderSurface
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.engine.download.TvDownloads
import com.saikou.sozo_tv.presentation.activities.DownloadsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Debug only. Download, then run mode=play in a fresh process with the fixture server stopped. */
@UnstableApi
class OfflineProbeInstrumentation : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); super.onCreate(arguments); start() }
    override fun onStart() {
        val result = runCatching { runBlocking {
            if (args.getString("mode", "download") == "download") {
                startActivitySync(Intent(targetContext, DownloadsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val id = TvDownloads.enqueue(targetContext, "Offline verification", VodMovieResponse("", emptyMap(),
                    urlobj = requireNotNull(args.getString("url")), type = args.getString("type", "hls")))
                repeat(180) {
                    val download = withContext(Dispatchers.Main) { TvDownloads.list(targetContext).find { it.request.id == id } }
                    if (download?.state == Download.STATE_FAILED) error("Download failed")
                    if (download?.state == Download.STATE_COMPLETED) {
                        targetContext.filesDir.resolve("offline-probe-id.txt").writeText(id)
                        return@runBlocking "PASS downloaded bytes=${download.bytesDownloaded} id=$id streamKeys=${download.request.streamKeys}"
                    }
                    delay(500)
                }
                error("Download timed out")
            } else {
                val id = args.getString("id") ?: targetContext.filesDir.resolve("offline-probe-id.txt").readText().trim()
                val download = withContext(Dispatchers.Main) { TvDownloads.list(targetContext).first { it.request.id == id } }
                check(download.state == Download.STATE_COMPLETED)
                var player: ExoPlayer? = null
                var surface: PlaceholderSurface? = null
                var failure: Throwable? = null
                var width = 0; var height = 0; var position = 0L
                try {
                    runOnMainSync {
                        surface = PlaceholderSurface.newInstance(targetContext, false)
                        player = ExoPlayer.Builder(targetContext).build().apply {
                            setVideoSurface(surface)
                            addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failure = error } })
                            setMediaSource(DownloadHelper.createMediaSource(download.request, TvDownloads.offlineFactory(targetContext, id)))
                            prepare(); play()
                        }
                    }
                    repeat(80) {
                        delay(250)
                        runOnMainSync { player?.let { width = it.videoSize.width; height = it.videoSize.height; position = it.currentPosition } }
                        failure?.let { throw it }
                        if (width > 0 && height > 0 && position >= 1500) return@runBlocking "PASS disk-only playback ${width}x$height position=$position id=$id"
                    }
                    error("Disk-only playback timed out width=$width height=$height position=$position")
                } finally { runOnMainSync { player?.release(); surface?.release() } }
            }
        } }
        val text = result.fold({ it }, { "FAIL ${it.stackTraceToString()}" })
        android.util.Log.i("SozoOfflineProbe", text)
        targetContext.filesDir.resolve("offline-probe.txt").writeText(text)
        finish(if (result.isSuccess) 0 else 1, Bundle().apply { putString("result", text) })
    }
}

package com.saikou.sozo_tv.presentation.activities

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import com.saikou.sozo_tv.components.PlayerTvView
import com.saikou.sozo_tv.engine.download.TvDownloads
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@UnstableApi
class OfflinePlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var view: PlayerTvView? = null
    private var position = 0L
    private var playWhenReady = true
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getLong("position") ?: 0L
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerTvView(this).also {
            setContentView(it, ViewGroup.LayoutParams(-1, -1))
            it.requestFocus()
        }
    }

    override fun onStart() {
        super.onStart()
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val ownerAtStart = TvDownloads.ownerToken()
            val id = intent.getStringExtra("download_id") ?: return@launch finish()
            val download = TvDownloads.list(this@OfflinePlayerActivity).firstOrNull { it.request.id == id && it.state == Download.STATE_COMPLETED }
            if (download == null) {
                Toast.makeText(this@OfflinePlayerActivity, "This download is not available in this profile.", Toast.LENGTH_LONG).show()
                finish(); return@launch
            }
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) || TvDownloads.ownerToken() != ownerAtStart) return@launch
            player = ExoPlayer.Builder(this@OfflinePlayerActivity).build().also { p ->
                view?.player = p
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@OfflinePlayerActivity, "Offline playback failed: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                    }
                })
                p.setMediaSource(DownloadHelper.createMediaSource(download.request,
                    TvDownloads.offlineFactory(this@OfflinePlayerActivity, id)))
                p.seekTo(position); p.prepare(); p.playWhenReady = playWhenReady
            }
        }
    }

    override fun onStop() {
        loadJob?.cancel()
        loadJob = null
        player?.let { position = it.currentPosition; playWhenReady = it.playWhenReady; it.release() }
        player = null; view?.player = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("position", player?.currentPosition ?: position)
        super.onSaveInstanceState(outState)
    }
}

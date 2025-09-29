package com.saikou.sozo_tv.presentation.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerControlView
import androidx.navigation.fragment.findNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ActivityLiveTvBinding
import com.saikou.sozo_tv.databinding.ControllerLiveTvBinding
import com.saikou.sozo_tv.presentation.screens.play.TrailerPlayerScreen

class LiveTvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveTvBinding
    private val liveStreamUrl by lazy { intent.getStringExtra("url") }
    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DefaultHttpDataSource.Factory

    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ControllerLiveTvBinding.bind(this.findViewById(R.id.cl_exo_controller))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveTvBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val title = intent.getStringExtra("title") ?: ""
        binding.pvPlayer.controller.binding.filmTitle.text = title
        initializeDataSourceFactory()
        initializePlayer()
        setupPlayerControls()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializeDataSourceFactory() {
        dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, "SozoTV"))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .build()
            .also { player ->
                binding.pvPlayer.player = player
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )


                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(liveStreamUrl ?: ""))

                // Prepare and start playback
                player.setMediaSource(hlsMediaSource)
                player.playWhenReady = true
                player.prepare()
                player.setWakeMode(C.WAKE_MODE_LOCAL)

                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        handlePlayerError(error)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.pvPlayer.visibility = View.GONE
                            }

                            Player.STATE_READY -> {
                                binding.progressBar.visibility = View.GONE
                                binding.pvPlayer.visibility = View.VISIBLE
                                binding.errorLayout.visibility = View.GONE
                            }

                            Player.STATE_ENDED -> restartStream()
                        }
                    }
                })
            }
        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener {
            finish()
        }

        binding.pvPlayer.controller.binding.exoPlayPauseContainer.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                player.play()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
            }

        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupPlayerControls() {
        val exoProgress: TrailerPlayerScreen.ExtendedTimeBar =
            binding.pvPlayer.findViewById(R.id.exo_progress)
        exoProgress.setForceDisabled(true)
        binding.pvPlayer.useController = true
        binding.pvPlayer.controllerAutoShow = true
        binding.pvPlayer.controllerHideOnTouch = true

        binding.retryButton.setOnClickListener {
            retryConnection()
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        binding.progressBar.visibility = View.GONE
        binding.pvPlayer.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE

        val errorMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Network connection failed. Please check your internet connection."

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Connection timeout. Please try again."

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "Stream format error. The live stream may be temporarily unavailable."

            else -> "Playback error: ${error.message}"
        }

        binding.errorText.text = errorMessage
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun retryConnection() {
        binding.errorLayout.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        player.let { player ->
            val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(liveStreamUrl ?: ""))

            player.setMediaSource(hlsMediaSource)
            player.prepare()
            player.play()
        }
    }

    private fun restartStream() {
        player?.let { player ->
            player.seekToDefaultPosition()
            player.prepare()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()

        }
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized) {
            player.play()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            player.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player.release()

    }

}
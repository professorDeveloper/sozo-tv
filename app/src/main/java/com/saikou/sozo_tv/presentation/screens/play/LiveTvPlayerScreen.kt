package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.navigation.fragment.navArgs
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.util.Util
import androidx.media3.ui.PlayerControlView
import androidx.navigation.fragment.findNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.LiveTvPlayerScreenBinding
import com.saikou.sozo_tv.databinding.ControllerLiveTvBinding

class LiveTvPlayerScreen : Fragment() {
    private var _binding: LiveTvPlayerScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DefaultHttpDataSource.Factory

    private val args by navArgs<LiveTvPlayerScreenArgs>()

    private val liveStreamUrl by lazy { args.url }
    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ControllerLiveTvBinding.bind(this.findViewById(R.id.cl_exo_controller))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LiveTvPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pvPlayer.controller.binding.filmTitle.text = args.title
        initializeDataSourceFactory()
        initializePlayer()
        setupPlayerControls()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializeDataSourceFactory() {
        dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(requireContext(), "SozoTV"))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        player = ExoPlayer.Builder(requireContext())
            .build()
            .also { player ->
                binding.pvPlayer.player = player
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )


                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(liveStreamUrl))

                // Prepare and start playback
                player.setMediaSource(hlsMediaSource)
                player.playWhenReady = true
                player.prepare()

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
            if (isAdded) {
                findNavController().navigateUp()
            }
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
        val exoProgress:TrailerPlayerScreen.ExtendedTimeBar = binding.pvPlayer.findViewById(R.id.exo_progress)
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
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun retryConnection() {
        binding.errorLayout.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        player.let { player ->
            val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(liveStreamUrl))

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

    override fun onDestroyView() {
        super.onDestroyView()
        player.release()
    }

}

package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerControlView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ContentControllerTvBinding
import com.saikou.sozo_tv.databinding.TrailerPlayerScreenBinding
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TrailerPlayerScreen : Fragment() {
    private var _binding: TrailerPlayerScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var mediaSession: MediaSession

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TrailerPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ContentControllerTvBinding.bind(this.findViewById(R.id.cl_exo_controller))

    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory
    private val args by navArgs<TrailerPlayerScreenArgs>()

    @UnstableApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pvPlayer.controller.binding.filmTitle.text = args.trailerName
        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener {
            findNavController().popBackStack()
        }
        initializeVideo()
        playVideo()
    }

    @UnstableApi
    @OptIn(UnstableApi::class)
    private fun playVideo() {
        val videoUrl = args.trailerUrl
        val mediaItem = MediaItem.Builder().setUri(videoUrl).build()
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo() {
        val customHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7",
            "DNT" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "sec-ch-ua-mobile" to "?1",
        )

        val client = OkHttpClient.Builder()
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                )
            )
            .ignoreAllSSLErrors()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder().apply {
                    customHeaders.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }.build()

                var response: okhttp3.Response? = null
                var exception: Exception? = null

                for (attempt in 1..3) {
                    try {
                        response = chain.proceed(newRequest)
                        if (response.isSuccessful) {
                            return@addInterceptor response
                        }
                        response.close()
                    } catch (e: Exception) {
                        exception = e
                        if (attempt < 3) {
                            Thread.sleep(1000L * attempt)
                        }
                    }
                }

                throw exception ?: RuntimeException("All retry attempts failed")
            }
            .build()

        dataSourceFactory = DefaultDataSource.Factory(
            requireContext(),
            OkHttpDataSource.Factory(client).setDefaultRequestProperties(customHeaders)
        )

        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            .setEnableAudioFloatOutput(false)

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setRenderersFactory(renderersFactory)
            .setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            ).build()
            .also { player ->
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                mediaSession = MediaSession.Builder(requireContext(), player).build()
            }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Bugsnag.notify(error)
            }
        })

        binding.pvPlayer.controller.binding.exoNextTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }
        binding.pvPlayer.controller.binding.exoPrevTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition - 10_000)
        }

        binding.pvPlayer.player = player
        binding.pvPlayer.controller.binding.exoPlayPauseContainer.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                player.play()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
            }
        }

        binding.pvPlayer.controller.findViewById<ExtendedTimeBar>(R.id.exo_progress)
            .setKeyTimeIncrement(10_000)
    }

    @SuppressLint("UnsafeOptInUsageError")
    class ExtendedTimeBar(
        context: Context, attrs: AttributeSet?
    ) : androidx.media3.ui.DefaultTimeBar(context, attrs) {

        private var previewBitmap: Bitmap? = null
        private val previewPaint = Paint().apply { isFilterBitmap = true }
        private var videoDuration: Long = 0L
        private var videoPosition: Long = 0L
        private var enabled = false
        private var forceDisabled = false

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            super.setEnabled(!forceDisabled && this.enabled)
        }

        fun setForceDisabled(forceDisabled: Boolean) {
            this.forceDisabled = forceDisabled
            isEnabled = enabled
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (videoDuration > 0) {
                val relativePos = videoPosition.toFloat() / videoDuration.toFloat()
                val previewWidth = previewBitmap?.width ?: 100
                val previewHeight = previewBitmap?.height ?: 60
                val previewX = (relativePos * width - previewWidth / 2).toInt()
                val previewY = height - previewHeight - 20

                previewBitmap?.let {
                    canvas.drawBitmap(it, previewX.toFloat(), previewY.toFloat(), previewPaint)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::player.isInitialized) {
            player.release()
            mediaSession.release()
        }
        _binding = null
    }
}

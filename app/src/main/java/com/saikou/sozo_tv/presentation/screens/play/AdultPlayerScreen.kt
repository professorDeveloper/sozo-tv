package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
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
import com.saikou.sozo_tv.parser.VideoType
import com.saikou.sozo_tv.presentation.viewmodel.AdultPlayerViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AdultPlayerScreen : Fragment() {
    private var _binding: TrailerPlayerScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var mediaSession: MediaSession

    private val model: AdultPlayerViewModel by viewModel()
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
    private val args by navArgs<AdultPlayerScreenArgs>()

    @SuppressLint("SetTextI18n")
    @UnstableApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pvPlayer.controller.binding.filmTitle.text =
            args.name + " - Episode " + args.episodeNum
        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener {
            findNavController().popBackStack()
        }
        model.loadVideoServers(args.link)
        model.episodeData.observe(viewLifecycleOwner) {
            when (it) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.pvPlayer.gone()
                }

                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    binding.pvPlayer.visible()
                    model.extractVideoFromKiwi(it.data)
                    model.extractData.observe(viewLifecycleOwner) {
                        when (it) {
                            Resource.Loading -> {
                                binding.loadingLayout.visible()
                                binding.loadingText.text = "Extracting video link..."
                                binding.pvPlayer.gone()
                            }

                            is Resource.Success -> {
                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()
                                initializeVideo(
                                    args.link,
                                    it.data.type == VideoType.M3U8
                                )
                                playVideo(it.data.url)
                            }

                            else -> {

                            }
                        }
                    }
                }

                else -> {}
            }
        }

    }

    @UnstableApi
    @OptIn(UnstableApi::class)
    private fun playVideo(videoLink: String) {
        val mediaItem = MediaItem.Builder().setUri(videoLink).build()
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo(
        episodeLink: String = args.link,
        isM3u8: Boolean
    ) {
        val customHeaders = mapOf(
            "Origin" to "https://hentaimama.io",
            "Referer" to "${episodeLink}",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7",
            "DNT" to "1",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "sec-ch-ua" to "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\""
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
                            Thread.sleep(1000L * attempt) // Exponential backoff
                        }
                    }
                }

                // If all retries failed, throw the last exception
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

        binding.pvPlayer.controller.findViewById<TrailerPlayerScreen.ExtendedTimeBar>(R.id.exo_progress)
            .setKeyTimeIncrement(10_000)
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

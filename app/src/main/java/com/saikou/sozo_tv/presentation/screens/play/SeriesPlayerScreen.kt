package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodePlayerAdapter
import com.saikou.sozo_tv.components.SkipIntroView
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.screens.play.dialog.SubtitleChooserDialog
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.observeOnce
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


class SeriesPlayerScreen : Fragment() {
    private var _binding: SeriesPlayerScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var player: ExoPlayer
    private lateinit var httpDataSource: HttpDataSource.Factory
    private lateinit var dataSourceFactory: DataSource.Factory
    private val model by viewModel<PlayViewModel>()
    private lateinit var mediaSession: MediaSession
    private val args by navArgs<SeriesPlayerScreenArgs>()
    private val episodeList = arrayListOf<Data>()

    private var countdownShown = false
    private var isCountdownActive = false
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null
    private var isSubtitle = true

    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ContentControllerTvSeriesBinding.bind(this.findViewById(R.id.cl_exo_controller_tv))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SeriesPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun showNextEpisodeCountdown() {
        binding.apply {
            if (!LocalData.isHistoryItemClicked && !countdownShown) {
                val nextEpisodeIndex = model.currentEpIndex + 1
                if (nextEpisodeIndex < episodeList.size) {
                    countdownShown = true
                    isCountdownActive = true
                    countdownOverlay.startCountdown(seconds = 10,
                        nextEpisode = nextEpisodeIndex + 1,
                        currentEpisode = model.currentEpIndex + 1,
                        title = args.name,
                        useEnglish = true,
                        onFinished = {
                            playNextEpisodeAutomatically()
                        },
                        onCancelled = {
                            isCountdownActive = false
                            player.play()
                        })
                }
            }
        }
    }

    private val handler = Handler()
    private lateinit var skipIntroView: SkipIntroView

    @SuppressLint("StringFormatMatches")
    private fun playNextEpisodeAutomatically() {
        if (model.currentEpIndex < episodeList.size - 1) {
            lifecycleScope.launch {
                saveWatchHistory()
                withContext(Dispatchers.Main) {
                    model.currentEpIndex += 1
                    model.doNotAsk = false
                    model.lastPosition = 0

                    model.getCurrentEpisodeVodAnime(
                        episodeList[model.currentEpIndex].session.toString(), args.seriesMainId
                    )

                    model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                        if (resource is Resource.Success) {
                            val newUrl = resource.data.urlobj
                            playNewEpisode(newUrl, headers = resource.data.header)

                            binding.pvPlayer.controller.binding.filmTitle.text =
                                getString(R.string.episode, args.name, model.currentEpIndex + 1)

                            resetCountdownState()
                        }
                    }
                }
            }
        }
    }

    private fun resetCountdownState() {
        countdownShown = false
        isCountdownActive = false
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                if (::player.isInitialized && player.isPlaying) {
                    val currentPosition = player.currentPosition
                    val duration = player.duration

                    if (duration > 0 && currentPosition > 0) {
                        val remainingTime = duration - currentPosition

                        if (remainingTime in 9001..10000 && !countdownShown && !isCountdownActive) {
                            showNextEpisodeCountdown()
                        }
                    }
                }

                progressHandler?.postDelayed(this, 1000)
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    private fun navigateBack() {
        if (!isAdded) return

        if (LocalData.isHistoryItemClicked) {
            val intent = Intent(context, ProfileActivity::class.java)
            startActivity(intent)
            activity?.finish()
        } else {
            runCatching {
                findNavController().navigateUp()
            }.onFailure { e ->
                e.printStackTrace()
            }
        }
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
    }


    @SuppressLint("SetTextI18n", "StringFormatMatches")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener {
            navigateBack()
        }


        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            })
        model.currentEpIndex = args.currentIndex
        model.getAllEpisodeByPage(
            args.currentPage, args.seriesMainId, ShowResponse(args.name, args.id, args.image)
        )
        binding.pvPlayer.controller.binding.filmTitle.text =
            "${args.name} - Episode ${model.currentEpIndex + 1}"
        model.allEpisodeData.observe(viewLifecycleOwner) {
            when (it) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.loadingText.text =
                        getString(R.string.part_are_episodes_loading, args.currentPage)
                    binding.pvPlayer.gone()

                }

                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    episodeList.clear()
                    episodeList.addAll(it.data.data ?: listOf())
                    model.loadWatched(args.id)
                    Log.d("GGG", "onViewCreated:${args.id} ")
                    model.isWatchedLiveData.observe(viewLifecycleOwner) { history ->
                        model.getCurrentEpisodeVodAnime(args.id, args.seriesMainId, history)
                    }

                    model.currentEpisodeData.observe(viewLifecycleOwner) {
                        when (it) {
                            Resource.Loading -> {
                                binding.loadingLayout.visible()
                                binding.pvPlayer.gone()
                                binding.loadingText.text =
                                    getString(R.string.episode_is_loading, model.currentEpIndex + 1)
                            }

                            is Resource.Success -> {
                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()
                                binding.textView9.text = getString(
                                    R.string.part_episode, args.currentPage, episodeList.size
                                )
                                initializeVideo(headers = it.data.header)
                                displayVideo()

                                binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()
                                binding.pvPlayer.controller.binding.epListContainer.setOnClickListener {
                                    binding.episodeRv.scrollToPosition(model.currentEpIndex)
                                    toggleSidebarRight(true)
                                }
                                binding.btnHideMenuRight.setOnClickListener {
                                    toggleSidebarRight(false)
                                }

                                val episodeAdapter = EpisodePlayerAdapter(
                                    model.currentEpIndex,
                                    args.image,
                                )
                                episodeAdapter.submitList(episodeList)
                                binding.episodeRv.adapter = episodeAdapter

                                episodeAdapter.setOnEpisodeClick { position, data ->
                                    toggleSidebarRight(false)
                                    if (position != model.currentEpIndex) {
                                        lifecycleScope.launch {
                                            saveWatchHistory()
                                        }
                                        model.doNotAsk = false
                                        model.currentEpIndex = position
                                        model.lastPosition = 0

                                        model.getCurrentEpisodeVodAnime(
                                            episodeList[position].session.toString(),
                                            args.seriesMainId
                                        )

                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                            if (resource is Resource.Success) {
                                                val newUrl =
                                                    resource.data.urlobj ?: return@observeOnce
                                                playNewEpisode(
                                                    newUrl,
                                                    headers = resource.data.header
                                                )
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    getString(
                                                        R.string.current_episode,
                                                        args.name,
                                                        position + 1
                                                    )
                                            }
                                        }
                                    }
                                }
                                binding.pvPlayer.controller.binding.exoNextContainer.setOnClickListener {
                                    if (model.currentEpIndex < episodeList.size - 1) {
                                        lifecycleScope.launch {
                                            saveWatchHistory()
                                        }
                                        model.currentEpIndex += 1
                                        model.doNotAsk = false
                                        model.getCurrentEpisodeVodAnime(
                                            episodeList[model.currentEpIndex].session.toString(),
                                            args.seriesMainId
                                        )
                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                            episodeList[model.currentEpIndex].title
                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                            if (resource is Resource.Success) {
                                                val newUrl = resource.data.urlobj
                                                playNewEpisode(
                                                    newUrl,
                                                    headers = resource.data.header
                                                )
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    getString(
                                                        R.string.current_episode,
                                                        args.name,
                                                        model.currentEpIndex + 1
                                                    )
                                            }
                                        }

                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            getString(R.string.this_is_the_last_episode),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                binding.pvPlayer.controller.binding.exoPrevContainer.setOnClickListener {
                                    if (model.currentEpIndex > 0) {
                                        lifecycleScope.launch {
                                            saveWatchHistory()
                                            withContext(Dispatchers.Main) {
                                                model.currentEpIndex -= 1
                                                model.doNotAsk = false
                                                model.getCurrentEpisodeVodAnime(
                                                    episodeList[model.currentEpIndex].session.toString(),
                                                    args.seriesMainId
                                                )
                                                model.lastPosition = 0
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    episodeList[model.currentEpIndex].title
                                                model.currentEpisodeData.observeOnce(
                                                    viewLifecycleOwner
                                                ) { resource ->
                                                    if (resource is Resource.Success) {
                                                        val newUrl = resource.data.urlobj

                                                        playNewEpisode(
                                                            newUrl,
                                                            headers = resource.data.header
                                                        )
                                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                                            getString(
                                                                R.string.current_episode,
                                                                args.name,
                                                                model.currentEpIndex + 1
                                                            )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            getString(R.string.this_is_the_first_episode),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                            }

                            else -> {

                            }
                        }

                    }

                }

                else -> {

                }
            }

        }
    }


    private suspend fun saveWatchHistory() {
        try {
            if (!::player.isInitialized) {
                Log.w("SaveHistory", "Player is not initialized -> returning")
                return
            }
            Log.d(
                "SaveHistory",
                "player.duration=${player.duration}, player.currentPosition=${player.currentPosition}"
            )
            if (player.duration <= 0 && player.currentPosition >= 100_000 && player.currentPosition >= player.duration - 50) {
                Log.w("SaveHistory", "Skipping save: player not fully loaded")
                return
            }

            Log.d("SaveHistory", "model.isWatched=${model.isWatched}")
            if (model.isWatched) {
                val getEpIndex = model.getWatchedHistoryEntity
                Log.d("SaveHistory", "getWatchedHistoryEntity=$getEpIndex")
                Log.d("SaveHistory", "model.seriesResponse=${model.seriesResponse}")

                if (getEpIndex == null) {
                    Log.e("SaveHistory", "getWatchedHistoryEntity is NULL!")
                    return
                }
                if (model.seriesResponse == null) {
                    Log.e("SaveHistory", "seriesResponse is NULL!")
                    return
                }

                val newEp = getEpIndex.copy(
                    totalDuration = player.duration,
                    imdbID = args.seriesMainId,
                    isEpisode = true,
                    epIndex = model.currentEpIndex,
                    lastPosition = player.currentPosition,
                    videoUrl = model.seriesResponse!!.urlobj,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex
                )
                model.updateHistory(newEp)
                model.getWatchedHistoryEntity = null
            } else {
                if (episodeList.isEmpty() || model.currentEpIndex !in episodeList.indices) {
                    Log.e("SaveHistory", "episodeList is empty or currentEpIndex out of range!")
                    return
                }
                if (episodeList[model.currentEpIndex].session == null) {
                    Log.e("SaveHistory", "session is NULL!")
                    return
                }
                if (episodeList[model.currentEpIndex].snapshot == null) {
                    Log.e("SaveHistory", "snapshot is NULL!")
                    return
                }

                Log.d("SaveHistory", "Building new history entity...")
                val historyBuild = WatchHistoryEntity(
                    episodeList[model.currentEpIndex].session ?: return,
                    "${args.name} - Episode ${model.currentEpIndex + 1}" ?: return,
                    mediaName = args.name,
                    episodeList[model.currentEpIndex].snapshot ?: return,
                    "",
                    args.seriesMainId,
                    "",
                    "",
                    "",
                    0.0,
                    args.currentPage,
                    "2024/01/01",
                    model.seriesResponse?.urlobj.toString(),
                    totalDuration = player.duration,
                    lastPosition = player.currentPosition,
                    imdbID = args.seriesMainId,
                    epIndex = model.currentEpIndex,
                    isEpisode = true,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex,
                    source = readData<String>(LocalData.SOURCE) ?: ""
                )
                model.addHistory(historyBuild)
            }
        } catch (e: Exception) {
            Log.e("SaveHistory", "Exception in saveWatchHistory: ${e.message}", e)
        }
    }

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo(headers: Map<String, String> = mapOf()) {
        if (::player.isInitialized) return
        Log.d("GGG", "initializeVideo: true :${headers}")

        val client = OkHttpClient.Builder().connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT
            )
        ).addInterceptor { chain ->
            val originalRequest = chain.request()
            val modifiedRequest = originalRequest.newBuilder().headers(headers.toHeaders()).build()
            chain.proceed(modifiedRequest)
        }.connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).ignoreAllSSLErrors().build()

        dataSourceFactory =
            DefaultDataSource.Factory(requireContext(), OkHttpDataSource.Factory(client))

        val renderersFactory =
            DefaultRenderersFactory(requireContext()).setEnableDecoderFallback(true)
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT).setEnableAudioFloatOutput(false)

        httpDataSource = DefaultHttpDataSource.Factory()

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setRenderersFactory(renderersFactory).setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            ).build()

        player.setPlayWhenReady(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)

        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )

        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSession.Builder(requireContext(), player).build()
        }



        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Bugsnag.notify(error)
            }

            @SuppressLint("SwitchIntDef")
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!LocalData.isHistoryItemClicked) {
                            resetCountdownState()
                            startProgressTracking()

                        }
                        val dur = player.duration
                        if (::skipIntroView.isInitialized) {
                            skipIntroView.cleanup()
                        }
                        skipIntroView = SkipIntroView(
                            binding.pvPlayer.controller.binding.root,
                            player,
                            model,
                            handler,
                            args.idMal ?: 0,
                            episodeList[model.currentEpIndex].episode ?: 0,
                            dur / 1000
                        )
                        Log.d("GGG", "onPlaybackStateChanged:${args.id} ")
                        Log.d(
                            "GGG",
                            "onPlaybackStateChanged: ${episodeList[model.currentEpIndex].episode} || ${dur / 1000} ||| ${model.currentEpIndex} || ${episodeList[model.currentEpIndex].anime_id}  "
                        )
                        skipIntroView.initialize()
                    }

                    Player.STATE_ENDED -> {
                        Log.d("GG", "onPlaybackStateChanged:WHY ENDED ")
                        if (!LocalData.isHistoryItemClicked) {
                            stopProgressTracking()
                            if (!isCountdownActive) {
                                playNextEpisodeAutomatically()
                            }

                        }
                    }

                }
            }
        })

        binding.pvPlayer.player = player

        binding.pvPlayer.controller.binding.exoNextTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }
        binding.pvPlayer.controller.binding.exoPrevTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition - 10_000)
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

        model.videoOptionsData.observe(viewLifecycleOwner) { videoOptions ->
            binding.pvPlayer.controller.binding.exoQuality.setOnClickListener {
                val dialog = VideoQualityDialog(videoOptions, model.currentSelectedVideoOptionIndex)
                dialog.setYesContinueListener { videoOption, i ->
                    if (i != model.currentSelectedVideoOptionIndex) {
                        model.currentSelectedVideoOptionIndex = i
                        model.updateQualityByIndex()
                    }
                }
                dialog.show(parentFragmentManager, "VideoQualityDialog")
            }
            model.currentQualityEpisode.observe(viewLifecycleOwner) { resource ->
                if (resource is Resource.Success) {
                    val newUrl = resource.data.urlobj
                    playQualityVideo(newUrl)
                }

            }
        }

        binding.pvPlayer.controller.findViewById<TrailerPlayerScreen.ExtendedTimeBar>(R.id.exo_progress)
            .setKeyTimeIncrement(10_000)
    }

    @OptIn(UnstableApi::class)
    private fun playNewEpisode(videoUrl: String, headers: Map<String, String>) {
        if (!::player.isInitialized) initializeVideo(headers)

        if (::skipIntroView.isInitialized) {
            Log.d("SeriesPlayerScreen", "[v0] Resetting skip intro view for new episode")
            skipIntroView.resetSkippedTimestamps()
        }

        resetCountdownState()
        stopProgressTracking()

        player.stop()
        player.clearMediaItems()
        Log.d("GGG", "playNewEpisode curIndex:${model.currentSelectedVideoOptionIndex} ")
        Log.d(
            "GGG",
            "playNewEpisode:${model.videoOptions.get(model.currentSelectedVideoOptionIndex)} "
        )
        val mediaItem = MediaItem.Builder().setUri(videoUrl).setMimeType(
            if (model.videoOptions.get(model.currentSelectedVideoOptionIndex).isM3U8) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
        ).setTag(args.name).build()
        val mediaSource =
            if (model.videoOptions[model.currentSelectedVideoOptionIndex].isM3U8) HlsMediaSource.Factory(
                dataSourceFactory
            ).createMediaSource(mediaItem)
            else ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)


        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }


    @OptIn(UnstableApi::class)
    private fun playQualityVideo(videoUrl: String) {
        if (!::player.isInitialized) initializeVideo()

        resetCountdownState()
        model.qualityProgress = player.currentPosition
        stopProgressTracking()

        player.stop()
        player.clearMediaItems()
        Log.d("GGG", "playQualityVideo:${model.videoOptions} ")
        val mediaItem = MediaItem.Builder().setUri(videoUrl)
            .setMimeType(if (model.videoOptions.get(model.currentSelectedVideoOptionIndex).isM3U8) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .setTag(args.name).build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        player.seekTo(model.qualityProgress)
        model.qualityProgress = 0
    }

    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val videoUrl = model.seriesResponse!!.urlobj
            val subtitles = model.seriesResponse?.subtitleList.orEmpty()
            val isSubtitleHave = subtitles.isNotEmpty()
            var useSubtitles = isSubtitleHave
            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L
            if (LocalData.isHistoryItemClicked) {
                binding.pvPlayer.controller.binding.exoNextContainer.gone()
                binding.pvPlayer.controller.binding.exoPrevContainer.gone()
                binding.pvPlayer.controller.binding.epListContainer.gone()
            } else {
                binding.pvPlayer.controller.binding.exoNextContainer.visible()
                binding.pvPlayer.controller.binding.exoPrevContainer.visible()
                binding.pvPlayer.controller.binding.epListContainer.visible()
            }

            lifecycleScope.launch {
                val finalSource = withContext(Dispatchers.IO) {
                    buildMediaSourceWithSubtitle(videoUrl, useSubtitles)
                }
                applySubtitleStyleToPlayer(binding.pvPlayer, PreferenceManager())
                player.setMediaSource(finalSource)
                player.prepare()
                player.playWhenReady = true
            }


            if (!model.doNotAsk) {
                if (lastPosition > 0) {
                    Log.d("PlayerScreen", "Resuming from last position: $lastPosition")
                    player.seekTo(lastPosition)
                } else {
                    Log.d("PlayerScreen", "Starting from the beginning")
                }
            } else {
                player.seekTo(model.lastPosition)
            }

            player.prepare()
            player.play()

            binding.pvPlayer.controller.binding.exoSubtidtle.isVisible = isSubtitleHave

            if (player.isPlaying) {
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
            }

            binding.pvPlayer.controller.binding.exoSubtidtle.setOnClickListener {
                val currentSelected = subtitles.getOrNull(model.currentSubEpIndex)
                val dialog =
                    SubtitleChooserDialog.newInstance(subtitles, currentSelected, useSubtitles)
                dialog.setSubtitleSelectionListener { selectedSubtitle ->
                    if (model.currentSubEpIndex == subtitles.indexOf(selectedSubtitle)) return@setSubtitleSelectionListener
                    model.currentSubEpIndex = subtitles.indexOf(selectedSubtitle)
                    binding.pvPlayer.controller.binding.exoSubtitlee.setImageResource(R.drawable.ic_subtitle_fill)
                    binding.pvPlayer.subtitleView?.visibility = View.VISIBLE
                    val previousPos = player.currentPosition
                    player.pause()
                    useSubtitles = selectedSubtitle?.file?.isNotEmpty() ?: false
                    lifecycleScope.launch {
                        val newSource = withContext(Dispatchers.IO) {
                            buildMediaSourceWithSubtitle(videoUrl, useSubtitles)
                        }
                        player.setMediaSource(newSource)
                        player.prepare()
                        player.seekTo(previousPos)
                        player.play()
                    }
                }
                dialog.show(parentFragmentManager, "subtitle_chooser")
            }
            if (model.isWatched && model.getWatchedHistoryEntity != null && model.getWatchedHistoryEntity!!.lastPosition > 0 && !model.doNotAsk) {
                player.pause()
                val dialog = AlertPlayerDialog(model.getWatchedHistoryEntity!!)
                dialog.setNoClearListener {
                    lifecycleScope.launch {
                        dialog.dismiss()
                        model.removeHistory(args.id)
                        withContext(Dispatchers.Main) {
                            player.seekTo(0)
                            player.play()
                        }
                    }
                }
                dialog.setYesContinueListener {
                    dialog.dismiss()
                    player.play()
                }
                dialog.show(parentFragmentManager, "ConfirmationDialog")

            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun applySubtitleStyleToPlayer(
        playerView: PlayerView, prefs: PreferenceManager
    ) {
        val subtitleView = playerView.subtitleView ?: return

        if (!prefs.isSubtitleCustom()) {
            subtitleView.setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
            return
        }

        val s = prefs.getSubtitleStyle()

        subtitleView.setStyle(
            CaptionStyleCompat(
                s.color,
                if (s.background) Color.argb(180, 0, 0, 0) else Color.TRANSPARENT,
                Color.TRANSPARENT,
                if (s.outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE
                else CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.BLACK,
                when (s.font) {
                    PreferenceManager.Font.DEFAULT -> null
                    PreferenceManager.Font.POPPINS -> ResourcesCompat.getFont(
                        playerView.context, R.font.poppins
                    )

                    PreferenceManager.Font.DAYS -> ResourcesCompat.getFont(
                        playerView.context, R.font.days
                    )

                    PreferenceManager.Font.MONO -> Typeface.MONOSPACE
                }
            )
        )

        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP, s.sizeSp.toFloat()
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun buildMediaSourceWithSubtitle(
        videoUrl: String, useSubtitles: Boolean
    ): androidx.media3.exoplayer.source.MediaSource {
        val mediaItem = MediaItem.Builder().setUri(videoUrl).setMimeType(
            if (model.videoOptions[model.currentSelectedVideoOptionIndex].isM3U8) MimeTypes.APPLICATION_M3U8
            else MimeTypes.VIDEO_MP4
        ).setTag(args.name).build()
        val mediaSource =
            if (model.videoOptions[model.currentSelectedVideoOptionIndex].isM3U8) HlsMediaSource.Factory(
                dataSourceFactory
            ).createMediaSource(mediaItem) else ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

        if (!useSubtitles) return mediaSource

        return try {
            val localFile = File(requireContext().cacheDir, "sub.vtt")

            val request = Request.Builder()
                .url(model.seriesResponse!!.subtitleList[model.currentSubEpIndex].file)
                .header("User-Agent", "Mozilla/5.0").build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body!!.byteStream().use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val text = localFile.readText()
            if (!text.startsWith("WEBVTT")) {
                localFile.writeText("WEBVTT\n\n$text")
            }

            val subtitleSource =
                SingleSampleMediaSource.Factory(dataSourceFactory).createMediaSource(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(localFile))
                        .setMimeType(MimeTypes.TEXT_VTT).setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(), C.TIME_UNSET
                )

            MergingMediaSource(mediaSource, subtitleSource)
        } catch (e: Exception) {
            Log.e("Subtitle", "Subtitle load failed", e)
            mediaSource
        }
    }

    private fun toggleSidebarRight(show: Boolean) {
        val sidebar = binding.sidebarRight
        val epListContainer = binding.pvPlayer.controller.binding.epListContainer

        sidebar.post {
            val sidebarWidth = sidebar.width.toFloat()

            if (show) {
                sidebar.apply {
                    isVisible = true
                    translationX = sidebarWidth
                    animate().translationX(0f).setDuration(100)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .start()
                }

                binding.btnHideMenuRight.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isEnabled = true
                    requestFocus()
                }

                epListContainer.apply {
                    isFocusable = false
                    gone()
                }
            } else {
                sidebar.animate().translationX(sidebarWidth).setDuration(100)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction {
                        sidebar.isVisible = false
                    }.start()

                epListContainer.apply {
                    isFocusable = true
                    visible()
                }

                binding.btnHideMenuRight.apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isEnabled = false
                }



                binding.episodeRv.clearFocus()
                binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()
            }
        }
    }

    override fun onDestroyView() {
        stopProgressTracking()
        if (::skipIntroView.isInitialized) {
            skipIntroView.cleanup()
        }

        if (::player.isInitialized) {

            if (player.currentPosition > 10 && ::player.isInitialized) {
                runBlocking {
                    saveWatchHistory()
                }
            }
        }

        if (::player.isInitialized) {
            player.release()
            mediaSession.release()
        }
        _binding = null
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        stopProgressTracking()

        if (::player.isInitialized) {
            player.pause()
            lifecycleScope.launch {
                saveWatchHistory()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && player.isPlaying) {
            startProgressTracking()
        }
    }
}

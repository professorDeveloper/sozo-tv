package com.saikou.sozo_tv.presentation.screens.play

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.TimeBar
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodePlayerAdapter
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.model.HlsVariant
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.observeOnce
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.net.URI
import java.util.UUID


class SeriesPlayerScreen : Fragment() {
    private var _binding: SeriesPlayerScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var player: ExoPlayer
    private lateinit var httpDataSource: HttpDataSource.Factory
    private lateinit var dataSourceFactory: DataSource.Factory
    private val model by viewModel<PlayViewModel>()
    private val userPreferenceManager by lazy { UserPreferenceManager(requireContext()) }
    private lateinit var mediaSession: MediaSession
    private val args by navArgs<SeriesPlayerScreenArgs>()
    private val episodeList = arrayListOf<Data>()
    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ContentControllerTvSeriesBinding.bind(this.findViewById(R.id.cl_exo_controller_tv))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SeriesPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }


    private fun navigateBack() {
        if (LocalData.isHistoryItemClicked) {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        } else {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        model.getAllEpisodeByPage(args.currentPage, args.seriesMainId)
        binding.pvPlayer.controller.binding.filmTitle.text =
            "${args.name} - Episode ${model.currentEpIndex + 1}"
        model.allEpisodeData.observe(viewLifecycleOwner) {
            when (it) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.loadingText.text = "Part ${args.currentPage} are episodes loading..."
                    binding.pvPlayer.gone()

                }

                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    episodeList.clear()
                    episodeList.addAll(it.data.data ?: listOf())

                    model.getCurrentEpisodeVod(args.id, args.seriesMainId)
                    model.currentEpisodeData.observe(viewLifecycleOwner) {
                        when (it) {
                            Resource.Loading -> {
                                binding.loadingLayout.visible()
                                binding.pvPlayer.gone()
                                binding.loadingText.text =
                                    "Episode ${model.currentEpIndex + 1} is loading..."
                            }

                            is Resource.Success -> {
                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()
                                binding.textView9.text =
                                    "Part ${args.currentPage} • Episode ${episodeList.size}"
                                initializeVideo()
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
                                    lifecycleScope.launch {
                                        saveWatchHistory()
                                    }
                                    model.doNotAsk = false
                                    model.currentEpIndex = position
                                    model.lastPosition = 0

                                    model.getCurrentEpisodeVod(
                                        episodeList[position].session.toString(),
                                        args.seriesMainId
                                    )

                                    model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                        if (resource is Resource.Success) {
                                            val newUrl = resource.data.urlobj ?: return@observeOnce
                                            playNewEpisode(newUrl, args.name)
                                            binding.pvPlayer.controller.binding.filmTitle.text =
                                                "${args.name} - Episode ${position + 1}"
                                        }
                                    }
                                }


                                binding.pvPlayer.controller.binding.exoNextContainer.setOnClickListener {
                                    if (model.currentEpIndex < episodeList.size - 1) {
//                                            saveWatchHistory()
                                        model.currentEpIndex += 1
//                                            model.doNotAsk = false
                                        model.getCurrentEpisodeVod(
                                            episodeList[model.currentEpIndex].session.toString(),
                                            args.seriesMainId
                                        )
                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                            episodeList[model.currentEpIndex].title
                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                            if (resource is Resource.Success) {
                                                val newUrl =
                                                    resource.data.urlobj
                                                playNewEpisode(newUrl, args.name)
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    "${args.name} - Episode ${model.currentEpIndex + 1}"
                                            }
                                        }

                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "This is the last episode",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                binding.pvPlayer.controller.binding.exoPrevContainer.setOnClickListener {
                                    if (model.currentEpIndex > 0) {
                                        lifecycleScope.launch() {
                                            saveWatchHistory()
                                            withContext(Dispatchers.Main) {
                                                model.currentEpIndex -= 1
                                                model.doNotAsk = false
                                                model.getCurrentEpisodeVod(
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
                                                        val newUrl =
                                                            resource.data.urlobj

                                                        playNewEpisode(newUrl, args.name)
                                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                                            "${args.name} - Episode ${model.currentEpIndex + 1}"
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "This is the first episode",
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
        if (!::player.isInitialized) return // Ensure player is initialized
        if (player.duration <= 0 && player.currentPosition >= 100_000 && player.currentPosition >= player.duration - 50) return  // Avoid saving if player hasn't loaded video yet

        if (model.isWatched) {
            val getEpIndex = model.getWatchedHistoryEntity
            val newEp = getEpIndex!!.copy(
                totalDuration = player.duration,
                lastEpisodeWatchedIndex = args.seriesMainId,
                isEpisode = true,
                epIndex = model.currentEpIndex,
                lastPosition = player.currentPosition,
                videoUrl = model.seriesResponse!!.urlobj,
            )
            model.updateHistory(newEp)
            model.getWatchedHistoryEntity = null
        } else {
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
                lastEpisodeWatchedIndex = args.seriesMainId,
                epIndex = model.currentEpIndex,
                isEpisode = true,
            )
            model.addHistory(historyBuild)
        }
    }

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo() {
        if (::player.isInitialized) return // Player allaqachon yaratilgan bo'lsa, qayt

        val client = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .ignoreAllSSLErrors().build()

        dataSourceFactory =
            DefaultDataSource.Factory(requireContext(), OkHttpDataSource.Factory(client))

        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            .setEnableAudioFloatOutput(false)

        httpDataSource = DefaultHttpDataSource.Factory()

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setRenderersFactory(renderersFactory)
            .setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            ).build()

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )

        // faqat bitta marta MediaSession yaratamiz
        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSession.Builder(requireContext(), player).build()
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Bugsnag.notify(error)
            }
        })

        binding.pvPlayer.player = player

        // Seek buttons
        binding.pvPlayer.controller.binding.exoNextTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }
        binding.pvPlayer.controller.binding.exoPrevTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition - 10_000)
        }

        // Play/pause
        binding.pvPlayer.controller.binding.exoPlayPauseContainer.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_play_to_pause)
            } else {
                player.play()
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_pause_to_play)
            }
        }

        // Quality selection
        binding.pvPlayer.controller.binding.exoQuality.setOnClickListener {
            val tracks: Tracks = player.currentTracks
            if (!tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }) {
                Toast.makeText(requireContext(), "No video tracks available", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            TrackSelectionDialogBuilder(
                requireContext(),
                "Choose Quality",
                player,
                C.TRACK_TYPE_VIDEO
            )
                .setTrackNameProvider { format ->
                    val height = format.height.takeIf { it > 0 } ?: 0
                    val bitrate = format.bitrate.takeIf { it > 0 }?.div(1000) ?: 0
                    when {
                        height > 0 && bitrate > 0 -> "${height}p • ${bitrate}kbps"
                        height > 0 -> "${height}p"
                        else -> "Auto"
                    }
                }.build().show()
        }

        binding.pvPlayer.controller
            .findViewById<TrailerPlayerScreen.ExtendedTimeBar>(R.id.exo_progress)
            .setKeyTimeIncrement(10_000)
    }

    @OptIn(UnstableApi::class)
    private fun playNewEpisode(videoUrl: String, title: String) {
        if (!::player.isInitialized) initializeVideo()

        player.stop()
        player.clearMediaItems()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(MimeTypes.VIDEO_MP4) // MP4 uchun to'g'ri MIME type
            .setTag(title)
            .build()

        // MP4 uchun ProgressiveMediaSource ishlatamiz
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }


    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val videoUrl = model.seriesResponse!!.urlobj
            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L
//            Log.d("GGG", "displayVideo:${lastPosition} ")
            if (LocalData.isHistoryItemClicked) {
                binding.pvPlayer.controller.binding.exoNextContainer.gone()
                binding.pvPlayer.controller.binding.exoPrevContainer.gone()
                binding.pvPlayer.controller.binding.epListContainer.gone()
            } else {
                binding.pvPlayer.controller.binding.exoNextContainer.visible()
                binding.pvPlayer.controller.binding.exoPrevContainer.visible()
                binding.pvPlayer.controller.binding.epListContainer.visible()
            }
            val mediaItem =
                MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(MimeTypes.APPLICATION_MP4)
                    .setTag(args.name)
                    .build()

            player.setMediaItem(mediaItem)

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

            if (player.isPlaying) {
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_play_to_pause)
            } else {
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_pause_to_play)
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


    private fun toggleSidebarRight(show: Boolean) {
        val sidebarWidth = binding.sidebarRight.width.toFloat()

        if (show) {
            binding.pvPlayer.controller.binding.epListContainer.isFocusable = false
            binding.pvPlayer.controller.binding.epListContainer.gone()
            binding.sidebarRight.isVisible = true
            binding.sidebarRight.translationX = sidebarWidth
            binding.sidebarRight.animate()
                .translationX(0f)
                .setDuration(900)
                .withEndAction {
                    binding.btnHideMenuRight.requestFocus()
                }
                .start()

            binding.btnHideMenuRight.isFocusable = true
            binding.btnHideMenuRight.isFocusableInTouchMode = true
            binding.btnHideMenuRight.isEnabled = true
        } else {
            binding.sidebarRight.animate()
                .translationX(sidebarWidth)
                .setDuration(900)
                .withEndAction {
                    binding.sidebarRight.isVisible = false
                }.start()

            binding.pvPlayer.controller.binding.epListContainer.isFocusable = true
            binding.pvPlayer.controller.binding.epListContainer.visible()
            binding.btnHideMenuRight.isFocusable = false
            binding.btnHideMenuRight.isFocusableInTouchMode = false
            binding.btnHideMenuRight.isEnabled = false
            binding.episodeRv.clearFocus()

            binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()
        }
    }

    override fun onDestroyView() {
        if (player.currentPosition > 10 && ::player.isInitialized) {
            runBlocking {
                saveWatchHistory()
            }
        }
        if (::player.isInitialized) {
            player.release()
            mediaSession.release()
        }
        _binding = null
        super.onDestroyView()
    }


    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()
            lifecycleScope.launch {
                saveWatchHistory()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

}
package com.saikou.sozo_tv.presentation.screens.play

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import com.saikou.sozo_tv.data.model.HlsVariant
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.observeOnce
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener {
            findNavController().popBackStack()
        }
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
//                                            saveWatchHistory()
                                            withContext(Dispatchers.Main) {
                                                model.currentEpIndex -= 1
//                                                model.doNotAsk = false
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
                                                        binding.pvPlayer.controller.binding.filmTitle.text = "${args.name} - Episode ${model.currentEpIndex + 1}"
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
    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo() {

        // Http factory bilan timeout va redirectlar
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpFactory)

        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exoPlayer ->
                // HLS uchun seek parameters
                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)

                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                exoPlayer.playWhenReady = true
                exoPlayer.volume = 1f

                val uniqueSessionId = UUID.randomUUID().toString()
                mediaSession =
                    MediaSession.Builder(requireContext(), exoPlayer).setId(uniqueSessionId).build()

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        Bugsnag.notify(error)
                        Toast.makeText(requireContext(), "Video Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }

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
                Toast.makeText(requireContext(), "No video tracks available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TrackSelectionDialogBuilder(requireContext(), "Choose Quality", player, C.TRACK_TYPE_VIDEO)
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

        // HLS uchun maxsus HlsMediaSource ishlatish
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setTag(title)
                .build()
            )

        player.setMediaSource(hlsSource)
        player.prepare()
        player.play()
    }


    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val videoUrl = model.seriesResponse!!.urlobj

//            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L
//            Log.d("GGG", "displayVideo:${lastPosition} ")

            val mediaItem =
                MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setTag(args.name)
                    .build()

            player.setMediaItem(mediaItem)

//            if (!model.doNotAsk) {
//                if (lastPosition > 0) {
//                    Log.d("PlayerScreen", "Resuming from last position: $lastPosition")
//                    player.seekTo(lastPosition)
//                } else {
//                    Log.d("PlayerScreen", "Starting from the beginning")
//                }
//            } else {
//                player.seekTo(model.lastPosition)
//            }

            player.prepare()
            player.play()
            if (player.isPlaying) {
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_play_to_pause)
            } else {
                binding.pvPlayer.controller.binding.exoPlayPaused
                    .setImageResource(R.drawable.anim_pause_to_play)
            }
//            if (model.isWatched && model.getWatchedHistoryEntity != null && model.getWatchedHistoryEntity!!.lastPosition > 0 && !model.doNotAsk) {
//                player.pause()
//                val dialog = AlertPlayerDialog(model.getWatchedHistoryEntity!!)
//                dialog.setNoClearListener {
//                    lifecycleScope.launch {
//                        dialog.dismiss()
//                        model.removeHistory(args.id)
//                        withContext(Dispatchers.Main) {
//                            player.seekTo(0)
//                            player.play()
//                        }
//                    }
//                }
//                dialog.setYesContinueListener {
//                    dialog.dismiss()
//                    player.play()
//                }
//                dialog.show(parentFragmentManager, "ConfirmationDialog")
//
//            }
        }
    }



    private fun toggleSidebarRight(show: Boolean) {
        val sidebarWidth = binding.sidebarRight.width.toFloat()

        if (show) {
            binding.sidebarRight.isVisible = true
            binding.sidebarRight.translationX =
                sidebarWidth  // boshlang'ich holati (o'ngda yashirin)
            binding.sidebarRight.animate()
                .translationX(0f) // chapga chiqib keladi
                .setDuration(900)
                .start()

            binding.btnHideMenuRight.isFocusable = true
            binding.btnHideMenuRight.isFocusableInTouchMode = true
            binding.btnHideMenuRight.isEnabled = true

            binding.episodeRv.requestFocus()
        } else {
            binding.sidebarRight.animate()
                .translationX(sidebarWidth) // o'ng tomonga yashirinadi
                .setDuration(900)
                .withEndAction {
                    binding.sidebarRight.isVisible = false
                }.start()

            binding.btnHideMenuRight.isFocusable = false
            binding.btnHideMenuRight.isFocusableInTouchMode = false
            binding.btnHideMenuRight.isEnabled = false
            binding.episodeRv.clearFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()
            lifecycleScope.launch {
//                saveWatchHistory()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (player.currentPosition > 10) {
            lifecycleScope.launch {
//                saveWatchHistory()
            }
        }
        if (::player.isInitialized) {
//            LocalData.isSeries = false
            player.release()
            mediaSession.release()
        }
        _binding = null
    }

    override fun onResume() {
        super.onResume()
    }

}
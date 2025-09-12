package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerControlView
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.ext.android.viewModel
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.currentEpIndex = args.currentEpisode.toInt()-1
        model.getAllEpisodeByPage(args.currentPage, args.seriesMainId)
        binding.pvPlayer.controller.binding.filmTitle.text = args.name
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
                                    "Episode ${model.currentEpIndex+1} is loading..."
                            }

                            is Resource.Success -> {
                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()
                                initializeVideo()
                                displayVideo()
                                binding.pvPlayer.controller.binding.epListContainer.setOnClickListener {
                                    binding.episodeRv.scrollToPosition(model.currentEpIndex)
                                    toggleSidebarRight(true)
                                }
                                binding.btnHideMenuRight.setOnClickListener {
                                    toggleSidebarRight(false)
                                }

                                val episodeAdapter = EpisodePlayerAdapter(
                                    model.currentEpIndex ,
                                    args.image,
                                )
                                episodeAdapter.submitList(episodeList)
                                binding.episodeRv.adapter = episodeAdapter
                                episodeAdapter.setOnEpisodeClick { position, data ->
                                    toggleSidebarRight(false)
                                }

                                binding.pvPlayer.controller.binding.exoNextContainer.setOnClickListener {
                                    if (model.currentEpIndex < episodeList.size - 1) {
                                        lifecycleScope.launch {
//                                            saveWatchHistory()
                                            model.currentEpIndex += 1
//                                            model.doNotAsk = false
                                            model.getCurrentEpisodeVod(
                                                episodeList[model.currentEpIndex].id.toString(),
                                                args.seriesMainId
                                            )
                                            binding.pvPlayer.controller.binding.filmTitle.text = episodeList[model.currentEpIndex].title

                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(), "This is the last episode", Toast.LENGTH_SHORT
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
                                                    episodeList[model.currentEpIndex].id.toString(),
                                                    args.seriesMainId
                                                )
                                                model.lastPosition = 0
                                                binding.pvPlayer.controller.binding.filmTitle.text = episodeList[model.currentEpIndex].title
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(), "This is the first episode", Toast.LENGTH_SHORT
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


    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val videoUrl = model.seriesResponse!!.urlobj

//            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L
//            Log.d("GGG", "displayVideo:${lastPosition} ")

            val mediaItem =
                MediaItem.Builder().setUri(videoUrl).setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setTag(args.name).build()
            val mediaSource =
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
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
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
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

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo() {

        val client = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .ignoreAllSSLErrors().build()
        dataSourceFactory =
            DefaultDataSource.Factory(requireContext(), OkHttpDataSource.Factory(client))


        httpDataSource = DefaultHttpDataSource.Factory()
        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
            .also { player ->
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                val uniqueSessionId = UUID.randomUUID().toString()

                mediaSession =
                    MediaSession.Builder(requireContext(), player).setId(uniqueSessionId).build()
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        Bugsnag.notify(error)
                    }
                })
            }



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
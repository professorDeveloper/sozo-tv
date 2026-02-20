package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.DetailPageBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.screens.profile.NfcDisabledDialog
import com.saikou.sozo_tv.presentation.viewmodel.DetailViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.isBookmarkClicked
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.toDomain
import com.saikou.sozo_tv.utils.visible
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.util.concurrent.TimeUnit


class DetailPage : Fragment(), MovieDetailsAdapter.DetailsInterface {
    private var _binding: DetailPageBinding? = null
    private val binding get() = _binding!!
    private val detailModel: DetailViewModel by activityViewModel()
    private var player: ExoPlayer? = null
    private val preference by lazy { PreferenceManager() }
    private var trailerUrlPlayer: String? = null
    private val detailsAdapter = MovieDetailsAdapter(detailsButtonListener = this)
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            error.cause?.let { cause -> Log.e("Tekshirish", "Cause: ${cause.message}") }
        }

        @SuppressLint("SwitchIntDef")
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_BUFFERING -> {}
                Player.STATE_READY -> binding.replaceImage.visibility = View.GONE
                Player.STATE_ENDED -> {
                    player?.play()
                    player?.seekTo(0)
                }

            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        LocalData.trailer = ""
        LocalData.bookmark = false
        _binding = DetailPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val seasonalTheme = PreferenceManager().getSeasonalTheme()
        binding.seasonalBackground.setTheme(seasonalTheme)
        initializeAdapter()
        initializePlayer()

        detailModel.relationsData.observe(viewLifecycleOwner) {
            detailsAdapter.submitRecommendedMovies(it)
        }
        detailModel.castResponseData.observe(viewLifecycleOwner) {
            detailsAdapter.submitCast(it)
        }
        detailModel.trailerData.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                trailerUrlPlayer = it
                prepareMedia(it)
                detailsAdapter.updateTrailer(it)
            }
        }
        detailModel.isBookmark.observe(viewLifecycleOwner) {
            detailsAdapter.updateBookmark(it)
        }
        detailModel.detailData.observe(viewLifecycleOwner) { details ->
            detailModel.checkBookmark(details.content.id)
            if (preference.isModeAnimeEnabled()) {
                detailModel.loadTrailer(details.content.id)
            } else {
                detailModel.loadTrailer(details.content.id, false, !details.content.isSeries)
            }
            binding.replaceImage.loadImage(details.content.bannerImage)
            val currentList = arrayListOf<DetailCategory>()
            val headerItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_HEADER)
            val sectionItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_SECTION)
            val thirdItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_THIRD)
            val fourItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_FOUR)
            currentList.addAll(listOf(headerItem, sectionItem, thirdItem, fourItem))
            detailsAdapter.submitList(currentList)
            LocalData.setFocusChangedListenerPlayer {
                val intent = Intent(binding.root.context, PlayerActivity::class.java)
                intent.putExtra("model", it.id)
                intent.putExtra("isMovie", !it.isSeries)
                requireActivity().startActivity(intent)
                requireActivity().finish()
            }
            detailModel.isBookmark.observe(viewLifecycleOwner) {
                detailsAdapter.updateBookmark(it)
            }
        }


        detailModel.errorData.observe(viewLifecycleOwner) {
            Toast.makeText(requireActivity(), it, Toast.LENGTH_SHORT).show()
        }
    }

    @UnstableApi
    private fun initializePlayer() {
        val okHttpClient =
            OkHttpClient.Builder().ignoreAllSSLErrors() // ⚠️ Sertifikat tekshiruvini bekor qiladi
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf("User-Agent" to "ExoPlayer"))

        val dataSourceFactory: DataSource.Factory =
            DefaultDataSource.Factory(requireContext(), okHttpDataSourceFactory)

        val mediaSourceFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        player =
            ExoPlayer.Builder(requireContext()).setMediaSourceFactory(mediaSourceFactory).build()
                .apply {
                    setAudioAttributes(
                        androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(), true
                    )
                    addListener(playerListener)
                    volume = 0f
                    playWhenReady = true
                }

        binding.trailerView.player = player
    }

    @OptIn(UnstableApi::class)
    private fun prepareMedia(hlsUrl: String) {
        if (hlsUrl != "data") {
            binding.replaceImage.gone()
        } else {
            binding.replaceImage.visible()
        }
        val mediaItem = MediaItem.Builder().setUri(hlsUrl).build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    private fun initializeAdapter() {
        binding.vgvMovieDetails.apply {
            adapter = detailsAdapter.apply {
                stateRestorationPolicy =
                    RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
        }
        binding.root.requestFocus()

    }


    override fun onCancelButtonClicked() {
        requireActivity().finish()
    }

    override fun onBookMarkClicked(itme: DetailCategory, bookmark: Boolean) {
        if (bookmark) {
            detailModel.removeBookmark(
                itme.content.toDomain()
            )
            LocalData.bookmark = false
            detailsAdapter.updateBookmark(false)
        } else {
            detailModel.addBookmark(
                itme.content.toDomain()
            )
            LocalData.bookmark = true
            detailsAdapter.updateBookmark(true)
        }
    }

    override fun onSoundButtonClicked(isOn: Boolean) {
        player?.volume = if (isOn) 0.5f else 0f
    }

    override fun onPauseButtonClicked(isPlay: Boolean) {
        if (isPlay) player?.play() else player?.pause()
    }

    override fun onWatchButtonClicked(
        item: DetailCategory, id: Int, url: String, title: String, isFree: Boolean
    ) {
        Log.d("GGG", "onWatchButtonClicked:${preference.isModeAnimeEnabled()} ")
        if (preference.isModeAnimeEnabled()) {
            val isAdult = item.content.isAdult
            val canWatchAdult = PreferenceManager().isNsfwEnabled()
            if (isAdult && !canWatchAdult) {
                val dialog = NfcDisabledDialog()
                dialog.setYesContinueListener {
                    dialog.dismiss()
                    val intent = Intent(binding.root.context, ProfileActivity::class.java)
                    requireActivity().startActivity(intent)
                    requireActivity().finish()
                }
                dialog.setOnBackPressedListener {
                    dialog.dismiss()
                }

                dialog.show(childFragmentManager, "dialog")
            } else {
                findNavController().navigate(
                    DetailPageDirections.actionDetailPage2ToEpisodeScreen(
                        isAdult = isAdult,
                        id,
                        malId = item.content.malId,
                        title,
                        isFree,
                    )
                )
            }
        } else {
            findNavController().navigate(
                DetailPageDirections.actionDetailPage2ToMovieEpisodeScreen(
                    title = item.content.title,
                    image = item.content.coverImage.large,
                    tmdbId = item.content.id,
                    isMovie = !item.content.isSeries
                )
            )
        }
    }

    override fun onTrailerButtonClicked(item: DetailCategory) {
        findNavController().navigate(
            DetailPageDirections.actionDetailPage2ToTrailerPlayerScreen(
                LocalData.trailer, item.content.title
            )
        )
    }

    override fun onCastItemClicked(item: Cast) {
        isBookmarkClicked = false
        Log.d("GGG", "onCastItemClicked:${item.id} ")
        Log.d("GGG", "onCastItemClicked:${item.name} ")
        findNavController().navigate(
            DetailPageDirections.actionDetailPage2ToCastDetailScreen(
                item.id
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        LocalData.bookmark = false
        detailModel.cancelTrailerLoading()
        if (player != null) {
            player?.release()
            player = null
        }
    }
}
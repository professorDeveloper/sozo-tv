package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.DetailPageBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.isBookmarkClicked
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.toDomain
import org.koin.androidx.viewmodel.ext.android.activityViewModel


class DetailPage : Fragment(), MovieDetailsAdapter.DetailsInterface {
    private var _binding: DetailPageBinding? = null
    private val binding get() = _binding!!
    private val playViewModel: PlayViewModel by activityViewModel()
    private var player: ExoPlayer? = null
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocalData.trailer = ""
        LocalData.bookmark = false
        isBookmarkClicked = false
        _binding = DetailPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeAdapter()
        initializePlayer()
        playViewModel.relationsData.observe(viewLifecycleOwner) {
            detailsAdapter.submitRecommendedMovies(it)
        }
        playViewModel.castResponseData.observe(viewLifecycleOwner) {
            detailsAdapter.submitCast(it)
        }
        playViewModel.trailerData.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                trailerUrlPlayer = it
                prepareMedia(it)
                detailsAdapter.updateTrailer(it)

            }
        }
        playViewModel.isBookmark.observe(viewLifecycleOwner) {
            detailsAdapter.updateBookmark(it)
        }
        playViewModel.detailData.observe(viewLifecycleOwner) { details ->
            playViewModel.checkBookmark(details.content.id)
            playViewModel.loadTrailer(details.content.title)
            binding.replaceImage.loadImage(details.content.bannerImage)
            val currentList = arrayListOf<DetailCategory>()
            val headerItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_HEADER)
            val sectionItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_SECTION)
            val thirdItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_THIRD)
            val fourItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_FOUR)
            currentList.addAll(listOf(headerItem, sectionItem, thirdItem, fourItem))
            detailsAdapter.submitList(currentList)
            LocalData.setFocusChangedListenerPlayer {
                val intent =
                    Intent(binding.root.context, PlayerActivity::class.java)
                intent.putExtra("model", it.id)
                requireActivity().startActivity(intent)
                requireActivity().finish()
            }
            playViewModel.isBookmark.observe(viewLifecycleOwner) {
                detailsAdapter.updateBookmark(it)
            }
        }


        playViewModel.errorData.observe(viewLifecycleOwner) {
            Toast.makeText(requireActivity(), it, Toast.LENGTH_SHORT).show()
        }
    }

    @UnstableApi
    private fun initializePlayer() {
        val context = requireContext()
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory =
            androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaSourceFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory).build().apply {
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )
                addListener(playerListener)
                volume = 0f
                playWhenReady = true
            }

        binding.trailerView.player = player
    }

    @OptIn(UnstableApi::class)
    private fun prepareMedia(hlsUrl: String) {
        val mediaItem =
            MediaItem.Builder().setUri(hlsUrl).setMimeType(MimeTypes.APPLICATION_MP4) // HLS format
                .build()

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
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
            playViewModel.removeBookmark(
                itme.content.toDomain()
            )
            LocalData.bookmark = false
            detailsAdapter.updateBookmark(false)
        } else {
            playViewModel.addBookmark(
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
        item: DetailCategory,
        id: Int,
        url: String,
        title: String,
        isFree: Boolean
    ) {
    }

    override fun onTrailerButtonClicked(item: DetailCategory) {
        findNavController().navigate(
            DetailPageDirections.actionDetailPage2ToTrailerPlayerScreen(
                LocalData.trailer,
                item.content.title
            )
        )
    }

    override fun onCastItemClicked(item: Cast) {
        Log.d("GGG", "onCastItemClicked:${item.id} ")
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
        playViewModel.cancelTrailerLoading()
        if (player != null) {
            player?.release()
            player = null
        }
    }
}
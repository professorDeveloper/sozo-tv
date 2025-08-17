package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.DetailPageBinding
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.loadImage
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
        _binding = DetailPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeAdapter()
        playViewModel.relationsData.observe(viewLifecycleOwner){
            detailsAdapter.submitRecommendedMovies(it)
        }
        playViewModel.detailData.observe(viewLifecycleOwner) { details ->
            binding.replaceImage.loadImage(details.content.bannerImage)
            val currentList = arrayListOf<DetailCategory>()
            val headerItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_HEADER)
            val sectionItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_SECTION)
            val thirdItem = details.copy(viewType = MovieDetailsAdapter.DETAILS_ITEM_THIRD)
            currentList.addAll(listOf(headerItem, sectionItem, thirdItem))

            detailsAdapter.submitList(currentList)
        }

        playViewModel.errorData.observe(viewLifecycleOwner) {
            Toast.makeText(requireActivity(), it, Toast.LENGTH_SHORT).show()
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

    override fun onBookMarkClicked(itme: DetailCategory) {
        TODO("Not yet implemented")
    }

    override fun onSoundButtonClicked(isOn: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onPauseButtonClicked(isPlay: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onWatchButtonClicked(
        item: DetailCategory,
        id: Int,
        url: String,
        title: String,
        isFree: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun onTrailerButtonClicked(item: DetailCategory) {
        TODO("Not yet implemented")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

    }
}
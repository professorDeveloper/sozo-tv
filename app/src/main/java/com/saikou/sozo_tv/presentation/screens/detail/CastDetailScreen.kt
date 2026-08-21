package com.saikou.sozo_tv.presentation.screens.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.databinding.CastDetailScreenBinding
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.CastDetailViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.characterBookmark
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import com.saikou.sozo_tv.data.model.toDomain
import org.koin.androidx.viewmodel.ext.android.viewModel

class CastDetailScreen : Fragment(), CastDetailAdapter.DetailsInterface {
    private var _binding: CastDetailScreenBinding? = null
    private val binding get() = _binding!!

    private val model: CastDetailViewModel by viewModel()

    private val adapter = CastDetailAdapter(detailsButtonListener = this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CastDetailScreenBinding.inflate(inflater, container, false)
        characterBookmark = false
        return binding.root
    }

    private val args: CastDetailScreenArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.vgvMovieDetails.adapter = adapter
        model.loadDetail(args.castId)
        model.checkBookmark(args.castId)
        // Register the bookmark observer once here, not inside the castDetail observer (which
        // would re-register it on every emission and cause redundant header rebinds).
        model.isBookmark.observe(viewLifecycleOwner) { adapter.updateBookmark(it) }
        model.error.observe(viewLifecycleOwner) {
            // Extension/server providers don't expose character/credit detail. Instead of the
            // old black snackbar, show a clean in-screen placeholder (icon + friendly message).
            binding.vgvMovieDetails.gone()
            binding.castPlaceholder.visible()
            // Defer the focus request until after the layout pass, otherwise requestFocus() can
            // return false on the just-revealed view and leave D-pad focus nowhere.
            binding.castPlaceholder.post { binding.castPlaceholder.requestFocus() }
        }
        model.castDetail.observe(viewLifecycleOwner) {
            // Real detail arrived (non-extension source) — hide the placeholder, show the grid.
            binding.castPlaceholder.gone()
            binding.vgvMovieDetails.visible()
            claimFocusToPlay()
            val headerData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                age = it.age,
                media = it.media,
                gender = it.gender,
                favorites = it.favorites,
                viewType = CastDetailAdapter.DETAILS_ITEM_HEADER
            )
            val sectionData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                gender = it.gender,
                age = it.age,
                favorites = it.favorites,
                media = it.media,
                viewType = CastDetailAdapter.DETAILS_ITEM_SECTION
            )

            val thirdData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                age = it.age,
                media = it.media,
                favorites = it.favorites,
                gender = it.gender,
                viewType = CastDetailAdapter.DETAILS_ITEM_THIRD
            )
            adapter.submitList(listOf(headerData, sectionData, thirdData))
            adapter.submitRecommendedMovies(
                it.media
            )
        }
    }

    override fun onFavoriteButtonClicked(item: CastAdapterModel) {
        if (characterBookmark) {
            model.removeBookmark(
                item.toDomain(args.castId)
            )
            characterBookmark = false
            adapter.updateBookmark(false)
        } else {
            model.addBookmark(
                item.toDomain(args.castId)
            )
            characterBookmark = true
            adapter.updateBookmark(true)
        }
    }

    override fun onCancelButtonClicked() {
        if (LocalData.isBookmarkClicked) {
            requireActivity().finish()
        } else {
            findNavController().popBackStack()
        }
    }


    /**
     * Claims the shared focus-to-play callback for this screen.
     *
     * It lives in LocalData, one slot for the whole process, and Detail and
     * CastDetail both write to it — so whichever ran last used to own it. Going
     * Detail -> cast -> Back left the callback pointing at a fragment whose view
     * was already gone. Re-claimed on every resume, so the screen the user is
     * actually looking at is the one that hears about it.
     */
    private fun claimFocusToPlay() {
        LocalData.setFocusChangedListenerPlayer {
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("model", it.id)
            intent.putExtra("isMovie", !it.isSeries)
            requireActivity().startActivity(intent)
            requireActivity().finishDeferred()
        }
    }

    override fun onResume() {
        super.onResume()
        claimFocusToPlay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        characterBookmark = false
        _binding = null

    }


}
package com.saikou.sozo_tv.presentation.screens.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.kongzue.dialogx.dialogs.WaitDialog
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.HomeScreenBinding
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.HomeViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.humanError
import com.saikou.sozo_tv.utils.LocalData.isAnimeEnabled
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.UiState
import com.saikou.sozo_tv.utils.animationTransaction
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.utils.requestInitialFocus
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen : Fragment() {
    private var _binding: HomeScreenBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by viewModel()
    private val homeAdapter = HomeAdapter()
    private val settingsViewModel: SettingsViewModel by activityViewModel()
    private var initialFocusPlaced = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = HomeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Reset per view: coming back from a detail screen recreates the view with
        // nothing focused, and the grid has to claim focus again.
        initialFocusPlaced = false
        initializeHome()
        LocalData.currentCategory = ""
        observeAniId()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.seasonalTheme.collect { theme ->
                    binding.seasonalBackground.setTheme(theme)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.homeDataState.collect { state ->
                    handleHomeDataState(state)
                }
            }
        }
    }

    private fun handleHomeDataState(state: UiState<List<HomeAdapter.HomeData>>) = when (state) {
        is UiState.Success -> {
            binding.isLoading.gIsLoadingRetry.isGone = true
            binding.isLoading.root.isGone = true
            homeAdapter.submitList(state.data)
            if (!initialFocusPlaced && state.data.isNotEmpty()) {
                initialFocusPlaced = true
                // Not the banner: it is the first row, so focus landed there by default, which
                // parked a white focus ring over the whole hero and stopped the carousel from
                // ever advancing. It stays one press of UP away.
                val firstRow = state.data.indexOfFirst { it !is BannerModel }
                if (firstRow > 0) binding.vgvHome.selectedPosition = firstRow
                binding.vgvHome.requestInitialFocus()
            }
            LocalData.setFocusedGenreClickListener {
                (requireActivity() as MainActivity).navigateToCategory(it)
            }
            LocalData.setViewAllClickListenerf {
                findNavController().navigate(
                    HomeScreenDirections.actionHomeToViewAllScreen(it),
                    animationTransaction().build()
                )
            }
            LocalData.setonClickedlistenerItemBanner {
                if (homeViewModel.preferenceManager.isModeAnimeEnabled()) {
                    if (it.contentItem.mal_id != -1) {
                        WaitDialog.show(requireActivity(), "Loading...")
                        homeViewModel.getMalId(it.contentItem.mal_id)
                    } else {
                        WaitDialog.dismiss(requireActivity())
                        homeViewModel.aniId.postValue(Resource.Idle)
                        val intent = Intent(binding.root.context, PlayerActivity::class.java)
                        intent.putExtra("model", it.contentItem.anilistId)
                        binding.root.context.startActivity(intent)
                    }
                } else {
                    Log.d("GGG", "handleHomeDataState:${it.contentItem.isSeries} ")
                    val intent = Intent(binding.root.context, PlayerActivity::class.java)
                    intent.putExtra("model", it.contentItem.imdb_id)
                    intent.putExtra("isMovie", !it.contentItem.isSeries)
                    binding.root.context.startActivity(intent)
                }
            }
            LocalData.setChannelItemClickListener {
                findNavController().navigate(
                    HomeScreenDirections.actionHomeToLiveTvPlayerScreen(
                        it.title, it.playLink
                    )
                )
            }
            LocalData.setHistoryItemClickListener {
                if (it.isEpisode) {
                    if (isAnimeEnabled) {
                        val intent = Intent(binding.root.context, PlayerActivity::class.java)
                        intent.putExtra("session", it.session)
                        intent.putExtra("page", it.page)
                        intent.putExtra("epIndex", it.epIndex)
                        intent.putExtra("mediaId", it.categoryid)
                        intent.putExtra("image", it.image)
                        intent.putExtra("animeTitle", it.mediaName)
                        intent.putExtra("isHistory", true)
                        intent.putExtra("isSeries", it.isSeries)
                        intent.putExtra("isAnime", it.isAnime)
                        requireContext().startActivity(intent)
                    } else {
                        val intent = Intent(binding.root.context, PlayerActivity::class.java)
                        intent.putExtra("session", it.session)
                        intent.putExtra("page", it.page)
                        intent.putExtra("epIndex", it.epIndex)
                        intent.putExtra("mediaId", it.categoryid)
                        intent.putExtra("imdb", it.imdbID)
                        intent.putExtra("image", it.image)
                        intent.putExtra("animeTitle", it.mediaName)
                        intent.putExtra("isHistory", true)
                        intent.putExtra("isSeries", it.isSeries)
                        intent.putExtra("currentSource", it.currentSourceName)
                        requireContext().startActivity(intent)
                    }
                }
            }
            LocalData.setonClickedListenerItemCategory {

                val intent = Intent(binding.root.context, PlayerActivity::class.java)
                intent.putExtra("model", it.content.id)
                intent.putExtra("isMovie", !it.content.isSeries)
                binding.root.context.startActivity(intent)
            }
        }

        is UiState.Loading -> {
            binding.isLoading.gIsLoadingRetry.isGone = true
            binding.isLoading.pbIsLoading.isVisible = true
            binding.isLoading.root.isVisible = true
        }

        is UiState.Error -> {
            Log.d("GGG", "handleHomeDataState:${state.message} ")
            binding.isLoading.gIsLoadingRetry.isVisible = true
            binding.isLoading.root.isVisible = true
            binding.isLoading.tvIsLoadingError.text =
                requireContext().humanError(state.message)
            binding.isLoading.pbIsLoading.isVisible = false
            binding.isLoading.btnIsLoadingRetry.requestFocus()
            binding.isLoading.btnIsLoadingRetry.setOnClickListener {
                homeViewModel.retry()
            }
        }


        else -> {}
    }

    private fun observeAniId() {
        homeViewModel.aniId.observe(viewLifecycleOwner) {
            when (it) {
                is Resource.Success -> {
                    WaitDialog.dismiss(requireActivity())
                    homeViewModel.aniId.postValue(Resource.Idle)
                    val intent = Intent(binding.root.context, PlayerActivity::class.java)
                    intent.putExtra("model", it.data)
                    binding.root.context.startActivity(intent)
                }

                is Resource.Error -> {
                    WaitDialog.dismiss(requireActivity())
                    homeViewModel.aniId.postValue(Resource.Idle)
                    Toast.makeText(
                        requireContext(),
                        "Couldn't open this title. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {}
            }
        }
    }

    private fun initializeHome() {
        binding.vgvHome.apply {
            adapter = homeAdapter.apply {
                stateRestorationPolicy =
                    RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            // Preserve each row's horizontal scroll/focus column when the outer grid recycles rows.
            setSaveChildrenPolicy(androidx.leanback.widget.BaseGridView.SAVE_ALL_CHILD)
            setItemSpacing(resources.getDimensionPixelSize(R.dimen.home_spacing) * 2)
        }
    }


}
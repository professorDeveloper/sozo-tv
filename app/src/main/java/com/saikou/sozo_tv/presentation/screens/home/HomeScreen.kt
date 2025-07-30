package com.saikou.sozo_tv.presentation.screens.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.kongzue.dialogx.dialogs.WaitDialog
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.HomeScreenBinding
import com.saikou.sozo_tv.presentation.viewmodel.HomeViewModel
import com.saikou.sozo_tv.utils.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen : Fragment() {
    private var _binding: HomeScreenBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by viewModel()
    private val homeAdapter = HomeAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HomeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeHome()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.homeDataState.collect { state ->
                    handleHomeDataState(state)
                }
            }
        }
    }

    private fun handleHomeDataState(state: UiState<List<HomeAdapter.HomeData>>) {
        when (state) {
            is UiState.Success -> {
                binding.isLoading.gIsLoadingRetry.isGone = true
                binding.isLoading.root.isGone = true
                homeAdapter.submitList(state.data)
//                HomeFakeDAta.setFocusedCateogryItem { data, it ->
//                    if (::channelCategory.isInitialized) {
//                        findNavController().navigate(
//                            HomeScreenDirections.actionHomeToLiveTvMainPlayerScreen(
//                                channelCategory[0], it.content
//                            )
//                        )
//                    }
//                }
//                homeViewModel.tvCategory.observe(viewLifecycleOwner) {
//                    channelCategory = it
//                    LocalData.categoryList = it
//                }
//                LocalData.setonClickedListenerItemCategory {
//                    WaitDialog.setMessage("Loading..").show(requireActivity())
//                    lifecycleScope.launch {
//                        delay(300)
//                        homeViewModel.checkMovieSeries(it.content.id, it.content).apply {
//                            WaitDialog.dismiss()
//                            if (this) {
//                                val intent = Intent(
//                                    binding.root.context, PlayerActivity::class.java
//                                )
//                                intent.putExtra("model", it)
//                                intent.putExtra("isSeries", true)
//                                binding.root.context.startActivity(intent)
//
//                            } else {
//                                val intent =
//                                    Intent(binding.root.context, PlayerActivity::class.java)
//                                intent.putExtra("model", it)
//                                intent.putExtra("isSeries", false)
//                                binding.root.context.startActivity(intent)
//
//                            }
//                        }
//                    }
//
//                }
            }

            is UiState.Loading -> {
                binding.isLoading.gIsLoadingRetry.isGone = true
                binding.isLoading.root.isVisible = true
            }

            is UiState.Error -> {
                binding.isLoading.gIsLoadingRetry.isVisible = true
                binding.isLoading.root.isVisible = true
                binding.isLoading.tvIsLoadingError.text = state.message
                binding.isLoading.pbIsLoading.isVisible = false
                binding.isLoading.btnIsLoadingRetry.requestFocus()
                binding.isLoading.btnIsLoadingRetry.setOnClickListener {
                    homeViewModel.loadBanners()
                    homeViewModel.loadCategories()
//                    homeViewModel.loadChannels()
                }
            }


            else -> {}
        }
    }
    private fun initializeHome() {
        binding.vgvHome.apply {
            adapter = homeAdapter.apply {
                stateRestorationPolicy =
                    RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setItemSpacing(resources.getDimension(R.dimen.home_spacing).toInt() * 2)
        }
        binding.root.requestFocus()
    }


}
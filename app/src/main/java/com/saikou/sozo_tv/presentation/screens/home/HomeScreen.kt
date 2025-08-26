package com.saikou.sozo_tv.presentation.screens.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.kongzue.dialogx.dialogs.WaitDialog
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.HomeScreenBinding
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.HomeViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.UiState
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
        LocalData.currentCategory = ""
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
                LocalData.setFocusedGenreClickListener {
                    (requireActivity() as MainActivity).navigateToCategory(it)
                }
                LocalData.setonClickedlistenerItemBanner {
                    WaitDialog.show(requireActivity(), "Loading...")
                    homeViewModel.getMalId(it.contentItem.mal_id)
                }
                homeViewModel.aniId.observe(viewLifecycleOwner) {
                    when (it) {
                        is Resource.Success -> {
                            WaitDialog.dismiss(requireActivity())
                            homeViewModel.aniId.postValue(Resource.Idle)
                            val intent =
                                Intent(binding.root.context, PlayerActivity::class.java)
                            intent.putExtra("model", it.data)
                            binding.root.context.startActivity(intent)
                        }

                        else -> {}
                    }
                }
                LocalData.setonClickedListenerItemCategory {
                    val intent =
                        Intent(binding.root.context, PlayerActivity::class.java)
                    intent.putExtra("model", it.content.id)
                    binding.root.context.startActivity(intent)
                }
            }

            is UiState.Loading -> {
                binding.isLoading.gIsLoadingRetry.isGone = true
                binding.isLoading.root.isVisible = true
            }

            is UiState.Error -> {
                Log.d("GGG", "handleHomeDataState:${state.message} ")
                binding.isLoading.gIsLoadingRetry.isVisible = true
                binding.isLoading.root.isVisible = true
                binding.isLoading.tvIsLoadingError.text = state.message
                binding.isLoading.pbIsLoading.isVisible = false
                binding.isLoading.btnIsLoadingRetry.requestFocus()
                binding.isLoading.btnIsLoadingRetry.setOnClickListener {
                    homeViewModel.loadBanners()
                    homeViewModel.loadCategories()
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
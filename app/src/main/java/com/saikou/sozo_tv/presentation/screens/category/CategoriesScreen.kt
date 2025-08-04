package com.saikou.sozo_tv.presentation.screens.category

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.kongzue.dialogx.dialogs.WaitDialog
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.CategoriesScreenBinding
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.presentation.viewmodel.CategoriesViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.UiState
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class CategoriesScreen : Fragment() {
    private var _binding: CategoriesScreenBinding? = null

    private val binding get() = _binding!!
    private val pageAdapter by lazy { CategoriesPageAdapter(isDetail = false) }
    private val model: CategoriesViewModel by viewModel()
    private var notSet = true


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CategoriesScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topContainer.adapter = pageAdapter
        binding.topContainer.setupGridLayoutForCategories(pageAdapter)

        if (notSet) {
            notSet = false
            model.searchResults = SearchResults(
                true, 1, "Action", null
            )
            binding.isLoadingContainer.gIsLoadingRetry.isGone = true
            binding.isLoadingContainer.root.isVisible = true
            model.loadCategories(model.searchResults)
        }
        pageAdapter.updateTabs(LocalData.genres)
        pageAdapter.setClickDetail {
//
        }

        model.result.observe(viewLifecycleOwner) {
            binding.isLoadingContainer.root.isVisible = false
            model.searchResults.apply {
                results = it?.results ?: arrayListOf()
                currentPage = it?.currentPage ?: 1
                hasNextPage = it?.hasNextPage ?: false
            }
            pageAdapter.updateCategories(it?.results as ArrayList<MainModel>? ?: arrayListOf())
        }

        model.updateFilter.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Error -> {
                    binding.topContainer.isVisible = true
                    pageAdapter.updateCategoriesAll(arrayListOf())
                }

                UiState.Loading -> {
                    binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                    binding.isLoadingContainer.root.isVisible = true
                    binding.topContainer.isVisible = false
                }

                is UiState.Success -> {
                    binding.topContainer.isVisible = true
//                    binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                    binding.isLoadingContainer.root.isVisible = false
                    model.searchResults.apply {
                        results = state.data?.results ?: arrayListOf()
                        currentPage = state.data?.currentPage ?: 1
                        hasNextPage = state.data?.hasNextPage ?: false
                    }
                    pageAdapter.updateCategoriesAll(
                        (state.data?.results ?: arrayListOf()) as ArrayList<MainModel>
                    )
                }

                else -> {}
            }
        }

        model.nextPageResult.observe(viewLifecycleOwner) {
            model.searchResults.apply {
                results = it?.results ?: arrayListOf()
                currentPage = it?.currentPage ?: 1
                hasNextPage = it?.hasNextPage ?: false
            }
            pageAdapter.updateCategories((it?.results ?: arrayListOf()) as ArrayList<MainModel>)
        }

        binding.tabRv.adapter = CategoryTabAdapter().apply {
            submitList(LocalData.genres)
            setFocusedItemListener { categoryTabItem, _ ->
                model.searchResults.currentPage = 1
                model.searchResults.genre = categoryTabItem
                binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                binding.isLoadingContainer.root.isVisible = true
                pageAdapter.updateCategoriesAll(arrayListOf())
                model.loadCategories(model.searchResults)
            }
//            setLastItemClickListener { showFilterDialog() }
        }


        pageAdapter.setCategoriesPageInterface(object :
            CategoriesPageAdapter.CategoriesPageInterface {
            override fun onCategorySelected(category: MainModel, position: Int) {
                if (model.searchResults.hasNextPage &&
                    model.searchResults.results?.isNotEmpty() == true
                ) {

                    lifecycleScope.launch(Dispatchers.IO) {
                            model.loadNextPage(model.searchResults)

                    }
                }
            }
        })
    }
}
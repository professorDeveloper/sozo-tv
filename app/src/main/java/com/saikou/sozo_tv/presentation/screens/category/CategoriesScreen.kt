package com.saikou.sozo_tv.presentation.screens.category

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.CategoriesScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.dialog.FilterDialog
import com.saikou.sozo_tv.presentation.viewmodel.CategoriesViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.UiState
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Local

class CategoriesScreen : Fragment() {
    private var _binding: CategoriesScreenBinding? = null

    private val binding get() = _binding!!
    private val pageAdapter by lazy { CategoriesPageAdapter(isDetail = false) }
    private val model: CategoriesViewModel by viewModel()
    private var notSet = true
    private var selectedSort: String? = null
    private var selectedYear: String? = null
    private var selectedRating: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = CategoriesScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preference = PreferenceManager()
        if (preference.isModeAnimeEnabled()) {
            binding.textView6.text = "Filter Anime"
        } else {
            binding.textView6.text = "Filter Movie"
        }
        binding.topContainer.adapter = pageAdapter
        binding.topContainer.setupGridLayoutForCategories(pageAdapter)
        if (preference.isModeAnimeEnabled()) {
            if (notSet) {
                notSet = false
                model.searchResults = SearchResults(
                    true,
                    1,
                    if (LocalData.currentCategory != "") LocalData.currentCategory else "Action",
                    results = null
                )
                binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                binding.isLoadingContainer.root.isVisible = true
                model.loadCategories(model.searchResults)
            }
        } else {
            if (notSet) {
                notSet = false
                model.searchResults = SearchResults(
                    true,
                    1,
                    if (LocalData.currentCategory != "") LocalData.currentCategory else "Action",
                    results = null
                )
                binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                binding.isLoadingContainer.root.isVisible = true
                model.loadCategoriesMovie(model.searchResults)
            }
        }

        if (preference.isModeAnimeEnabled()) {
            pageAdapter.updateTabs(LocalData.genres)
        } else {
            pageAdapter.updateTabs(LocalData.genreTmdb.map { it.title.toString() } as ArrayList<String>)
        }
        pageAdapter.setClickDetail {
            val intent =
                Intent(binding.root.context, PlayerActivity::class.java)
            intent.putExtra("model", it.id)
            binding.root.context.startActivity(intent)
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


        model.nextPageResult.observe(viewLifecycleOwner) {
            model.searchResults.apply {
                results = it?.results ?: arrayListOf()
                currentPage = it?.currentPage ?: 1
                hasNextPage = it?.hasNextPage ?: false
            }
            pageAdapter.updateCategories((it?.results ?: arrayListOf()) as ArrayList<MainModel>)
        }

        binding.tabRv.adapter =
            CategoryTabAdapter(isFiltered = preference.isModeAnimeEnabled()).apply {
                if (preference.isModeAnimeEnabled()) {
                    submitList(LocalData.genres)
                } else {
                    submitList(LocalData.genreTmdb.map { it.title.toString() } as ArrayList<String>)
                }
                setFocusedItemListener { categoryTabItem, _ ->
                    model.searchResults.currentPage = 1
                    model.searchResults.genre = categoryTabItem
                    binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                    binding.isLoadingContainer.root.isVisible = true
                    pageAdapter.updateCategoriesAll(arrayListOf())
                    if (preference.isModeAnimeEnabled()) {
                        model.loadCategories(model.searchResults)
                    } else {
                        model.loadCategoriesMovie(model.searchResults)
                    }
                }
                if (preference.isModeAnimeEnabled()) {
                    val pos = if (LocalData.currentCategory != "") LocalData.genres.indexOf(
                        LocalData.currentCategory
                    ) + 1 else 1
                    binding.tabRv.scrollToPosition(
                        pos
                    )
                    setSelectedPosition(
                        pos
                    )
                    setLastItemClickListener { showFilterDialog() }
                } else {
                    val pos =
                        if (LocalData.currentCategory != "") LocalData.genreTmdb.indexOf(LocalData.genreTmdb.find { it.title == LocalData.currentCategory }!!) else 0
                    binding.tabRv.scrollToPosition(
                        pos
                    )
                    setSelectedPosition(
                        pos
                    )
                }
            }


        model.updateFilter.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Error -> {
                    binding.isLoadingContainer.root.isVisible = false
                    binding.topContainer.isVisible = true
                    pageAdapter.updateCategoriesAll(arrayListOf())
                    binding.topContainer.gone()
                    binding.placeHolder.root.visible()
                    binding.tabRv.requestFocus()
                    binding.placeHolder.placeholderTxt.text = state.message
                    binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
                }

                UiState.Loading -> {
                    binding.topContainer.isVisible = false
                    binding.isLoadingContainer.root.isVisible = true
                    binding.placeHolder.root.gone()
                }

                is UiState.Success -> {
                    binding.placeHolder.root.gone()
                    binding.isLoadingContainer.root.isVisible = false
                    binding.topContainer.isVisible = true
                    binding.topContainer.requestFocus()
                    model.searchResults.apply {
                        results = state.data.results ?: arrayListOf()
                        currentPage = state.data.currentPage
                        hasNextPage = state.data.hasNextPage
                    }
                    pageAdapter.updateCategoriesAll(
                        state.data.results!! as ArrayList<MainModel>
                    )
                }

                else -> {}
            }
        }


        pageAdapter.setCategoriesPageInterface(object :
            CategoriesPageAdapter.CategoriesPageInterface {
            override fun onCategorySelected(category: MainModel, position: Int) {
                if (model.searchResults.hasNextPage && model.searchResults.results?.isNotEmpty() == true) {

                    lifecycleScope.launch(Dispatchers.IO) {
                        if (preference.isModeAnimeEnabled()) {
                            model.loadNextPage(model.searchResults)
                        } else {
                            model.loadNextPageMovie(model.searchResults)
                        }
                    }
                }
            }
        })
    }

    private fun applyFilters(country: String?, year: String?, rating: String?) {
        model.searchResults.currentPage = 1
        selectedSort = country
        selectedYear = year
        selectedRating = rating
        model.searchResults.apply {
            this.tag = selectedSort ?: ""
            this.year = selectedYear?.toInt() ?: -1
            this.avgScore =
                if (selectedRating != null) if (selectedRating != "") selectedRating?.toInt()
                    ?: -1 else -1 else -1
        }
        model.loadFilter(model.searchResults)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun showFilterDialog() {
        val dialog: FilterDialog =
            FilterDialog.newInstance(selectedYear, selectedSort, selectedRating)
        dialog.onFiltersApplied = { country, year, rating ->
            applyFilters(country, year, rating)
        }
        dialog.show(parentFragmentManager, "FilterDialog")
    }
}
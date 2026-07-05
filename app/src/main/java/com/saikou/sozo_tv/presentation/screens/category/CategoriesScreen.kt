package com.saikou.sozo_tv.presentation.screens.category

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.CategoriesScreenBinding
import com.saikou.sozo_tv.domain.model.CategoryChip
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.dialog.FilterDialog
import com.saikou.sozo_tv.presentation.viewmodel.CategoriesViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.UiState
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class CategoriesScreen : Fragment() {
    private var _binding: CategoriesScreenBinding? = null

    private val binding get() = _binding!!
    private val pageAdapter by lazy { CategoriesPageAdapter(isDetail = false) }
    private val model: CategoriesViewModel by viewModel()
    private val settingViewModel: SettingsViewModel by activityViewModel()
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingViewModel.seasonalTheme.collect { theme ->
                    binding.seasonalBackground.setTheme(theme)
                }
            }
        }
        binding.topContainer.adapter = pageAdapter
        binding.topContainer.setupGridLayoutForCategories(pageAdapter)
        // Initial content + chips are provider-driven and load asynchronously (see the
        // genreChips observer below), so nothing is fetched here anymore. Eagerly searching
        // "Action" returned nothing for server (sv:) providers and left the grid empty.
        pageAdapter.setClickDetail {
            val intent = Intent(binding.root.context, PlayerActivity::class.java)
            intent.putExtra("model", it.id)
            intent.putExtra("isMovie", !it.isSeries)
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

        // Chips + the initial grid are driven by the active provider's real catalog. The
        // fetch is fired once (notSet guard). setupTabs() runs on EVERY chip emission so the
        // tab row + grid are rebuilt whenever the view is recreated: the retained ViewModel's
        // genreChips LiveData redelivers its cached value on re-observe (return-from-backstack),
        // preserving the original "rebuild tabRv every onViewCreated" behavior. Guarding
        // setupTabs itself with notSet would leave tabRv adapterless (dead focus + stuck
        // spinner) on the second onViewCreated of the same Fragment instance.
        model.genreChips.observe(viewLifecycleOwner) { chips ->
            setupTabs(chips ?: emptyList())
        }
        if (notSet) {
            notSet = false
            binding.isLoadingContainer.gIsLoadingRetry.isGone = true
            binding.isLoadingContainer.root.isVisible = true
            model.loadGenreChips()
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

    /**
     * Builds the chip row and fires the content load once chips have resolved. Runs on every
     * genreChips emission (see the observer). [providerChips] come from the active provider's
     * real catalog (engine.genres -> home sections); when empty we fall back to the hardcoded
     * local lists so the default AniList / TMDB experience is preserved.
     */
    @SuppressLint("SetTextI18n")
    private fun setupTabs(providerChips: List<CategoryChip>) {
        val preference = PreferenceManager()
        val isAnime = preference.isModeAnimeEnabled()
        // Screen owns blank-filtering so chip positions line up with finalChips indices.
        val finalChips = providerChips.ifEmpty { buildFallbackChips(isAnime) }
            .filter { it.name.isNotBlank() }

        if (finalChips.isEmpty()) {
            binding.isLoadingContainer.root.isVisible = false
            binding.topContainer.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeholderTxt.text = "No categories available for this source"
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
            return
        }

        // Anime prepends a "Filter" chip at position 0, so real chips start at index 1.
        val dataOffset = if (isAnime) 1 else 0
        val remembered = finalChips.indexOfFirst { it.name == LocalData.currentCategory }
        val selectedDataIndex = if (remembered >= 0) remembered else 0
        val selectedPos = selectedDataIndex + dataOffset

        binding.tabRv.adapter = CategoryTabAdapter(isFiltered = isAnime).apply {
            submitList(ArrayList(finalChips.map { it.name }))
            setFocusedItemListener { name, position ->
                val chip = finalChips.getOrNull(position - dataOffset)
                LocalData.currentCategory = name
                model.searchResults.currentPage = 1
                model.searchResults.genre = name
                model.searchResults.slug = chip?.slug
                binding.placeHolder.root.gone()
                binding.topContainer.visible()
                binding.isLoadingContainer.gIsLoadingRetry.isGone = true
                binding.isLoadingContainer.root.isVisible = true
                pageAdapter.updateCategoriesAll(arrayListOf())
                if (isAnime) model.loadCategories(model.searchResults)
                else model.loadCategoriesMovie(model.searchResults)
            }
            if (isAnime) setLastItemClickListener { showFilterDialog() }
            setSelectedPosition(selectedPos)
        }
        binding.tabRv.scrollToPosition(selectedPos)

        // Initial load for the selected chip, now that we know its real slug.
        val initial = finalChips[selectedDataIndex]
        model.searchResults = SearchResults(
            hasNextPage = true,
            currentPage = 1,
            genre = initial.name,
            results = null,
            slug = initial.slug,
        )
        binding.isLoadingContainer.gIsLoadingRetry.isGone = true
        binding.isLoadingContainer.root.isVisible = true
        if (isAnime) model.loadCategories(model.searchResults)
        else model.loadCategoriesMovie(model.searchResults)

        // Guarantee an initial D-pad focus target: the grid loads async, so the tab row is
        // the first focusable view and must claim focus once it's populated.
        _binding?.tabRv?.post { _binding?.tabRv?.requestFocus() }
    }

    private fun buildFallbackChips(isAnime: Boolean): List<CategoryChip> =
        if (isAnime) LocalData.genres.map { CategoryChip(it, null) }
        else LocalData.genreTmdb.map { CategoryChip(it.title.toString(), null) }

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

    private fun showFilterDialog() {
        val dialog: FilterDialog =
            FilterDialog.newInstance(selectedYear, selectedSort, selectedRating)
        dialog.onFiltersApplied = { country, year, rating ->
            applyFilters(country, year, rating)
        }
        dialog.show(parentFragmentManager, "FilterDialog")
    }
}
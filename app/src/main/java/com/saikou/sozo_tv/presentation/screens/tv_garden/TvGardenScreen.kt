package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.ChannelsAdapter
import com.saikou.sozo_tv.databinding.TvGardenScreenBinding
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.manager.GardenDataManager
import com.saikou.sozo_tv.presentation.activities.LiveTvActivity
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoryTabAdapter
import com.saikou.sozo_tv.presentation.viewmodel.TvGardenViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import com.skydoves.powerspinner.PowerSpinnerView
import com.skydoves.powerspinner.OnSpinnerItemSelectedListener
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class TvGardenScreen : Fragment() {
    private var isAnimeSelected = true

    private var _binding: TvGardenScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var channelsAdapter: ChannelsAdapter
    private lateinit var categoriesAdapter: CategoryTabAdapter
    private val model: TvGardenViewModel by viewModel()
    private var currentSort: String? = null
    private val countryList = ArrayList<Country>()
    private var selectedPosCat = 1
    private var selectedPosCount = 1
    private val categoryList = ArrayList<Category>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = TvGardenScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!model.isOpened) {
            categoriesAdapter = CategoryTabAdapter(isFiltered = true)
            channelsAdapter = ChannelsAdapter() {
                if (it.iptvUrls.isNotEmpty()) {
                    model.isOpened = true
                    val intent = Intent(requireContext(), LiveTvActivity::class.java)
                    intent.putExtra("url", it.iptvUrls[0])
                    intent.putExtra("title", it.name)
                    requireActivity().startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "No stream available", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            binding.tabRv.adapter = categoriesAdapter
            binding.bookmarkRv.adapter = channelsAdapter
            if (model.isCountrySelected) model.loadChannelCountries() else model.loadChannelCategories()
        }
        model.countries.observe(viewLifecycleOwner) { it ->
            when (it) {
                Resource.Loading -> {
                    binding.progressBar.pbIsLoading.visible()
                    binding.progressBar.root.visible()
                    binding.bookmarkRv.gone()
                    binding.tabRv.gone()

                }

                is Resource.Success -> {
                    val countries = it.data
                    binding.progressBar.pbIsLoading.gone()
                    binding.progressBar.root.gone()
                    binding.tabRv.visible()
                    categoriesAdapter.submitList(countries.map { it.name } as ArrayList<String>)
                    countryList.clear()
                    countryList.addAll(countries)
                    if (selectedPosCount != -1) {
                        binding.tabRv.scrollToPosition(selectedPosCount)
                        categoriesAdapter.setSelectedPosition(selectedPosCount)
                        model.loadChannelsByCountry(countryList[selectedPosCount - 1])
                    }
                }

                else -> {

                }
            }
        }
        model.categories.observe(viewLifecycleOwner) { it ->
            when (it) {
                Resource.Loading -> {
                    binding.progressBar.pbIsLoading.visible()
                    binding.progressBar.root.visible()
                    binding.bookmarkRv.gone()
                    binding.tabRv.gone()
                }

                is Resource.Success -> {
                    val categories = it.data
                    binding.tabRv.visible()
                    binding.progressBar.pbIsLoading.gone()
                    binding.progressBar.root.gone()
                    val data =(categories.map { it.name } as ArrayList<String>)
                    data.add("Adlt")
                    categoriesAdapter.submitList(data)
                    categoryList.clear()
                    categoryList.addAll(categories)
                    if (selectedPosCat != -1) {
                        binding.tabRv.scrollToPosition(selectedPosCat)
                        categoriesAdapter.setSelectedPosition(selectedPosCat)
                        model.loadChannelsByCategory(categoryList[selectedPosCat - 1])
                    }
                }

                else -> {

                }
            }

        }
        categoriesAdapter.setLastItemClickListener {
            val dialogGarden = FilterDialogGarden.newInstance(currentSort)
            dialogGarden.show(parentFragmentManager, "FilterDialogGarden")
            dialogGarden.onFiltersApplied = { sort ->
                model.isCountrySelected = sort == "By Country"
                if (model.isCountrySelected) {
                    currentSort = sort
                    model.loadChannelCountries()
                } else {
                    currentSort = sort
                    model.loadChannelCategories()
                }
            }
        }
        categoriesAdapter.setFocusedItemListener { s, i ->
            if (model.isCountrySelected) {
                val findCategory = countryList.find { it.name == s }
                selectedPosCount = i
                model.loadChannelsByCountry(findCategory!!)
            } else {
                selectedPosCat = i
                val findCategory = categoryList.find { it.name == s }
                model.loadChannelsByCategory(findCategory!!)
            }
        }
        model.channels.observe(viewLifecycleOwner) {
            binding.bookmarkRv.visible()
            channelsAdapter.updateChannels(it)
            binding.bookmarkRv.setNumColumns(4)
            binding.bookmarkRv.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }


}

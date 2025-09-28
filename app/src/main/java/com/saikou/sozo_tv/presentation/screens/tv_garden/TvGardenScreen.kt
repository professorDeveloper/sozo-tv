package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.ChannelsAdapter
import com.saikou.sozo_tv.databinding.TvGardenScreenBinding
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.manager.GardenDataManager
import com.saikou.sozo_tv.presentation.screens.category.CategoryTabAdapter
import com.saikou.sozo_tv.presentation.viewmodel.TvGardenViewModel
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
    private var isCountrySelected = true
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TvGardenScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoriesAdapter = CategoryTabAdapter(isFiltered = true)
        binding.tabRv.adapter = categoriesAdapter
        model.loadChannelCountries()
        model.countries.observe(viewLifecycleOwner) { countries ->
            categoriesAdapter.submitList(countries.map { it.name } as ArrayList<String>)
        }
        model.categories.observe(viewLifecycleOwner) { categories ->
            categoriesAdapter.submitList(categories.map { it.name } as ArrayList<String>)
        }

        categoriesAdapter.setLastItemClickListener {
            val dialogGarden =
                FilterDialogGarden.newInstance(if (isCountrySelected) "By Country" else "By Category")
            dialogGarden.show(parentFragmentManager, "FilterDialog")
            dialogGarden.onFiltersApplied = { sort ->
                isCountrySelected = sort == "By Country"
                if (isCountrySelected) {
                    model.loadChannelCountries()
                } else {
                    model.loadChannelCategories()
                }
            }
        }
        categoriesAdapter.setFocusedItemListener { s, i ->

        }


    }

}

package com.saikou.sozo_tv.presentation.screens.my_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ItemTabChipBinding
import com.saikou.sozo_tv.databinding.MyListScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.MyListViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class MyListScreen : Fragment() {

    private var _binding: MyListScreenBinding? = null
    private val binding get() = _binding!!

    private val animeAdapter = CategoriesPageAdapter(isDetail = true)
    private var currentTab: MyListTab = MyListTab.WATCHING

    private val userId by lazy {
        PreferenceManager(requireContext()).getString(AuthPrefKeys.ANILIST_ANI_ID).toInt()
    }

    private val model by viewModel<MyListViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyListScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvContent.adapter = animeAdapter

        setupTabs(binding.tabLayout)

        binding.tabLayout.post {
            val idx = binding.tabLayout.selectedTabPosition.coerceAtLeast(0)
            updateTabStyles(binding.tabLayout, idx)
            currentTab = MyListTab.entries[idx]
            model.loadMyList(currentTab.title, userId)
        }

        model.listData.observe(viewLifecycleOwner) {
            when (it) {
                is Resource.Error -> {
                    binding.isLoading.root.gone()
                    binding.rvContent.gone()
                    binding.emptyState.visible()
                    binding.tvEmptyTitle.text = it.throwable.message
                }

                Resource.Loading -> {
                    binding.isLoading.root.visible()
                    binding.rvContent.gone()
                    binding.emptyState.gone()
                }

                is Resource.Success -> {
                    binding.isLoading.root.gone()
                    val data = it.data
                    if (data.isEmpty()) {
                        binding.rvContent.gone()
                        binding.emptyState.visible()
                    } else {
                        binding.emptyState.gone()
                        binding.rvContent.visible()
                        animeAdapter.updateCategoriesAll(data as ArrayList<MainModel>)
                        animeAdapter.setCategoriesPageInterface(object :
                            CategoriesPageAdapter.CategoriesPageInterface {
                            override fun onCategorySelected(category: MainModel, position: Int) {}
                        })
                    }
                }

                else -> Unit
            }
        }
    }

    private fun setupTabs(tabLayout: TabLayout) {
        tabLayout.removeAllTabs()
        tabLayout.clipToPadding = false
        tabLayout.clipChildren = false
        (tabLayout.getChildAt(0) as? ViewGroup)?.apply {
            clipToPadding = false
            clipChildren = false
        }

        MyListTab.entries.forEachIndexed { index, tab ->
            val newTab = tabLayout.newTab()
            newTab.customView = createTabView(tab.title)
            tabLayout.addTab(newTab, index == 0)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val idx = tab.position
                updateTabStyles(tabLayout, idx)
                currentTab = MyListTab.entries[idx]
                model.loadMyList(currentTab.title, userId)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyles(tabLayout, tabLayout.selectedTabPosition)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                updateTabStyles(tabLayout, tab.position)
            }
        })
    }

    private fun createTabView(title: String): View {
        val chipBinding = ItemTabChipBinding.inflate(layoutInflater)
        chipBinding.tvTab.text = title

        chipBinding.root.isFocusable = true
        chipBinding.root.isFocusableInTouchMode = true

        chipBinding.root.setOnFocusChangeListener { v, hasFocus ->
            val tv = v.findViewById<TextView>(R.id.tvTab)
            val highlight = hasFocus || (tv?.isSelected == true)
            animateTabView(v, highlight)
        }

        return chipBinding.root
    }

    private fun updateTabStyles(tabLayout: TabLayout, selectedIndex: Int) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val root = tab.customView ?: continue
            val tv = root.findViewById<TextView>(R.id.tvTab) ?: continue

            val selected = i == selectedIndex
            tv.isSelected = selected

            animateTabView(root, selected || root.isFocused)
        }
    }

    private fun animateTabView(v: View, highlight: Boolean) {
        v.animate().cancel()
        v.animate()
            .alpha(if (highlight) 1.0f else 0.85f)
            .translationY(if (highlight) -2f else 0f)
            .setDuration(120)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

enum class MyListTab(val title: String) {
    WATCHING("Watching"),
    COMPLETED("Completed"),
    ON_HOLD("On-Hold"),
    DROPPED("Dropped"),
    PLAN_TO_WATCH("Plan to Watch"),
    FAVORITES("Favorites")
}

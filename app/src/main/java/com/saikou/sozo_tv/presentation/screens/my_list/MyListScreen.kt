package com.saikou.sozo_tv.presentation.screens.my_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemTabChipBinding
import com.saikou.sozo_tv.databinding.MyListScreenBinding

class MyListScreen : Fragment() {

    private var _binding: MyListScreenBinding? = null
    private val binding get() = _binding!!

    private var currentTab: MyListTab = MyListTab.WATCHING

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyListScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTabs(binding.tabLayout)
        selectTab(MyListTab.WATCHING)
    }

    private fun setupTabs(tabLayout: TabLayout) {
        tabLayout.removeAllTabs()

        MyListTab.entries.forEachIndexed { index, tab ->
            val newTab = tabLayout.newTab()
            newTab.customView = createTabView(tab.title)
            tabLayout.addTab(newTab, index == 0)
        }


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectTab(MyListTab.entries[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyles(tabLayout, selectedIndex = tabLayout.selectedTabPosition)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun createTabView(title: String): View {
        val chipBinding = ItemTabChipBinding.inflate(layoutInflater)
        chipBinding.tvTab.text = title
        chipBinding.tvTab.setOnFocusChangeListener { v, hasFocus ->
            animateTabView(v, hasFocus || v.isSelected)
        }

        return chipBinding.root
    }

    private fun updateTabStyles(tabLayout: TabLayout, selectedIndex: Int) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val tv = tab.customView?.findViewById<TextView>(R.id.tvTab) ?: continue

            val selected = i == selectedIndex
            tv.isSelected = selected

            animateTabView(tv, selected || tv.isFocused)
        }
    }

    private fun animateTabView(v: View, highlight: Boolean) {
        v.animate()
            .scaleX(if (highlight) 1.08f else 1.0f)
            .scaleY(if (highlight) 1.08f else 1.0f)
            .setDuration(120)
            .start()
    }

    private fun selectTab(tab: MyListTab) {
        currentTab = tab

        val list = when (tab) {
            MyListTab.WATCHING -> { /* TODO */ }
            MyListTab.COMPLETED -> { /* TODO */ }
            MyListTab.ON_HOLD -> { /* TODO */ }
            MyListTab.DROPPED -> { /* TODO */ }
            MyListTab.PLAN_TO_WATCH -> { /* TODO */ }
            MyListTab.FAVORITES -> { /* TODO */ }
        }
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

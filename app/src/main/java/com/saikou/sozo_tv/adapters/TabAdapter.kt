package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemBottombarBinding

class TabAdapter(
    private val onTabSelected: (Boolean) -> Unit // true = anime, false = characters
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private var isAnimeSelected = true

    override fun getItemCount(): Int = 1 // only one header row

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding =
            ItemBottombarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind()
    }

    inner class TabViewHolder(private val binding: ItemBottombarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            updateTabState()

            binding.navAnime.setOnClickListener {
                isAnimeSelected = true
                updateTabState()
                onTabSelected(true)
            }
            binding.navCharacters.setOnClickListener {
                isAnimeSelected = false
                updateTabState()
                onTabSelected(false)
            }
        }

        private fun updateTabState() {
            binding.navAnime.setBackgroundResource(
                if (isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
            )
            binding.navCharacters.setBackgroundResource(
                if (!isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
            )
        }
    }
}

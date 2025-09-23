package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.RecentSearchItemBinding

class RecentSearchAdapter : RecyclerView.Adapter<RecentSearchAdapter.RecentSearchViewHolder>() {

    private var recentSearches = ArrayList<String>()

    private lateinit var onItemClickListener: (query: String) -> Unit
    private lateinit var onRemoveClickListener: (query: String, position: Int) -> Unit

    fun setOnItemClickListener(listener: (query: String) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnRemoveClickListener(listener: (query: String, position: Int) -> Unit) {
        onRemoveClickListener = listener
    }

    inner class RecentSearchViewHolder(private val binding: RecentSearchItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(query: String, position: Int) {
            binding.recentSearchText.text = query

            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.isClickable = true

            binding.root.setOnClickListener {
                if (::onItemClickListener.isInitialized) {
                    onItemClickListener.invoke(query)
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                        if (::onItemClickListener.isInitialized) {
                            onItemClickListener.invoke(query)
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val binding = RecentSearchItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(recentSearches[position], position)
    }

    override fun getItemCount(): Int = recentSearches.size

    fun updateData(newSearches: List<String>) {
        recentSearches.clear()
        recentSearches.addAll(newSearches)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        if (position in 0 until recentSearches.size) {
            recentSearches.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, recentSearches.size)
        }
    }
}

package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.RecentSearchItemBinding

class RecentSearchAdapter : RecyclerView.Adapter<RecentSearchAdapter.RecentSearchViewHolder>() {

    private var searchHistory = mutableListOf<String>()
    private var onItemClickListener: ((String) -> Unit)? = null
    private var onRemoveClickListener: ((String, Int) -> Unit)? = null

    inner class RecentSearchViewHolder(private val binding: RecentSearchItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(query: String, position: Int) {
            binding.searchQueryText.text = query

            binding.root.setOnClickListener {
                onItemClickListener?.invoke(query)
            }

            binding.removeSearchButton.setOnClickListener {
                onRemoveClickListener?.invoke(query, position)
            }

            // Netflix-style focus handling
            binding.root.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val binding = RecentSearchItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(searchHistory[position], position)
    }

    override fun getItemCount(): Int = searchHistory.size

    fun updateData(newSearchHistory: List<String>) {
        searchHistory.clear()
        searchHistory.addAll(newSearchHistory)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        if (position in 0 until searchHistory.size) {
            searchHistory.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, searchHistory.size)
        }
    }

    fun setOnItemClickListener(listener: (String) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnRemoveClickListener(listener: (String, Int) -> Unit) {
        onRemoveClickListener = listener
    }
}

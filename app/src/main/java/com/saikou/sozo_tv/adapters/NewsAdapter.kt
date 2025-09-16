package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.databinding.ItemFeaturedNewsBinding

class NewsAdapter(
    private var items: List<NewsItem> = emptyList()
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(val binding: ItemFeaturedNewsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.newsTitle.text = item.title
            binding.newsDescription.text = item.description
            binding.publishedTime.text = item.time
            binding.actionButton.text = item.action
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemFeaturedNewsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<NewsItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

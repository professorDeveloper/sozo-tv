package com.saikou.sozo_tv.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.databinding.ItemFeaturedNewsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewsAdapter(
    private var items: List<NewsItem> = emptyList()
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(val binding: ItemFeaturedNewsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.newsTitle.text = item.title
            binding.newsDescription.text = item.description
            binding.publishedTime.text = item.timestamp.toTimeAgo() // <-- readable time
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
    fun Long.toTimeAgo(): String {
        if (this <= 0) return "Unknown time"

        val now = System.currentTimeMillis()
        val diff = now - this

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            days < 30 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
            days < 365 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
            days <= 365 * 5 -> "${days / 365} year${if (days / 365 > 1) "s" else ""} ago"
            else -> {
                // Juda eski bo'lsa normal sana formatida chiqaramiz
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(this))
            }
        }
    }



}

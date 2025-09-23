package com.saikou.sozo_tv.adapters

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.NewsPreferences
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.data.model.NewsPriority
import com.saikou.sozo_tv.databinding.ItemFeaturedNewsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewsAdapter(
    private val context: Context, // Added context for NewsPreferences
    private var items: List<NewsItem> = emptyList(),
    private val onItemClick: (NewsItem, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    private val newsPreferences = NewsPreferences(context)

    inner class NewsViewHolder(val binding: ItemFeaturedNewsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NewsItem, position: Int) {
            binding.newsTitle.text = item.title
            binding.newsDescription.text = item.description
            binding.publishedTime.text = item.timestamp.toTimeAgo()
            binding.actionButton.text = item.action

            val isRead = newsPreferences.isRead(item.id)
            updateUnreadState(item.copy(isRead = isRead))

            binding.root.setOnClickListener {
                onItemClick(item, position)
            }
        }

        private fun updateUnreadState(item: NewsItem) {
            if (item.isRead) {
                binding.unreadIndicator.visibility = android.view.View.GONE
                binding.newsTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.root.background = ContextCompat.getDrawable(
                    binding.root.context,
                    R.drawable.tv_news_item_background
                )
                binding.newsTitle.alpha = 0.8f
                binding.newsDescription.alpha = 0.7f
            } else {
                binding.unreadIndicator.visibility = android.view.View.VISIBLE
                binding.newsTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.newsTitle.alpha = 1.0f
                binding.newsDescription.alpha = 1.0f
            }
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
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<NewsItem>) {
        items = newItems.sortedWith(
            compareBy<NewsItem> { newsPreferences.isRead(it.id) }
                .thenByDescending { it.priority.ordinal }
                .thenByDescending { it.timestamp }
        )
        notifyDataSetChanged()
    }

    fun markAsRead(position: Int) {
        if (position in items.indices) {
            val newsItem = items[position]
            newsPreferences.markAsRead(newsItem.id)
            notifyItemChanged(position)
        }
    }

    fun getUnreadCount(): Int {
        return items.count { !newsPreferences.isRead(it.id) }
    }

    fun Long.toTimeAgo(): String {
        if (this <= 0) return "Unknown time"

        val timestampMs = if (this < 10000000000L) {
            this * 1000
        } else {
            this
        }

        val now = System.currentTimeMillis()
        val diff = now - timestampMs

        if (diff < 0) {
            return "just now"
        }

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
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(timestampMs))
            }
        }
    }
}

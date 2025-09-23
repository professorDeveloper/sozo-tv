package com.saikou.sozo_tv.presentation.screens.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.adapters.NewsAdapter
import com.saikou.sozo_tv.data.local.pref.NewsPreferences
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.databinding.NewsPageBinding
import com.saikou.sozo_tv.presentation.viewmodel.NewsViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class NewsPage : Fragment() {

    private var _binding: NewsPageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModel()

    private lateinit var newsPreferences: NewsPreferences
    private lateinit var adapter: NewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = NewsPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        newsPreferences = NewsPreferences(requireContext())
        adapter = NewsAdapter(requireContext()) { newsItem, position ->
            handleNewsItemClick(newsItem, position)
        }

        binding.verticalGridView.adapter = adapter

        lifecycleScope.launchWhenStarted {
            viewModel.news.collectLatest { list ->
                adapter.update(list)
                updateUnreadCount()
            }
        }

        viewModel.loadNews()
    }

    private fun handleNewsItemClick(newsItem: NewsItem, position: Int) {
        if (!newsPreferences.isRead(newsItem.id)) {
            adapter.markAsRead(position)
            updateUnreadCount()
        }

        handleNewsAction(newsItem)
    }

    private fun handleNewsAction(newsItem: NewsItem) {
        // Handle different news actions based on newsItem.action
        when (newsItem.action.lowercase()) {
            "update" -> {
                // Navigate to updates screen
            }
            "watch" -> {
                // Navigate to watch screen
            }
            "download" -> {
                // Handle download action
            }
            // Add more actions as needed
        }
    }

    private fun updateUnreadCount() {
        val unreadCount = adapter.getUnreadCount()
        // Update UI with unread count if you have a badge or counter
        // binding.unreadBadge.text = unreadCount.toString()
        // binding.unreadBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

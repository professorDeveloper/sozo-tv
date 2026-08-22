package com.saikou.sozo_tv.presentation.screens.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saikou.sozo_tv.adapters.NewsAdapter
import com.saikou.sozo_tv.data.local.pref.NewsPreferences
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.utils.humanError
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.databinding.NewsPageBinding
import com.saikou.sozo_tv.presentation.viewmodel.NewsViewModel
import com.saikou.sozo_tv.utils.requestInitialFocus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
        super.onViewCreated(view, savedInstanceState)
        newsPreferences = NewsPreferences(requireContext())
        adapter = NewsAdapter(requireContext()) { newsItem, position ->
            handleNewsItemClick(newsItem, position)
        }

        binding.verticalGridView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.news.collectLatest { state ->
                    when (state) {
                        is Resource.Success -> {
                            adapter.update(state.data)
                            renderState(loading = false, message = null, isEmpty = state.data.isEmpty())
                            updateUnreadCount()
                        }

                        is Resource.Error -> renderState(
                            loading = false,
                            message = requireContext().humanError(state.throwable),
                            isEmpty = true,
                        )

                        Resource.Loading -> renderState(loading = true, message = null, isEmpty = false)
                        Resource.Idle -> Unit
                    }
                }
            }
        }

        viewModel.loadNews()
    }

    private fun renderState(loading: Boolean, message: String?, isEmpty: Boolean) {
        val b = _binding ?: return
        b.newsLoading.isVisible = loading
        b.emptyNews.isVisible = !loading && isEmpty
        b.verticalGridView.isVisible = !loading && !isEmpty
        b.emptyNews.text = message ?: getString(R.string.news_empty)
        if (!loading && !isEmpty) b.verticalGridView.requestInitialFocus()
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

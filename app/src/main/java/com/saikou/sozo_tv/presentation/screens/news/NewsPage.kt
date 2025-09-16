package com.saikou.sozo_tv.presentation.screens.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.adapters.NewsAdapter
import com.saikou.sozo_tv.databinding.NewsPageBinding
import com.saikou.sozo_tv.presentation.viewmodel.NewsViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class NewsPage : Fragment() {

    private var _binding: NewsPageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModel()
    private val adapter = NewsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = NewsPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.verticalGridView.adapter = adapter

        lifecycleScope.launchWhenStarted {
            viewModel.news.collectLatest { list ->
                adapter.update(list)
            }
        }

        viewModel.loadNews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

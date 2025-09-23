package com.saikou.sozo_tv.presentation.screens.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.RecentSearchAdapter
import com.saikou.sozo_tv.adapters.SearchAdapter
import com.saikou.sozo_tv.databinding.SearchScreenBinding
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.utils.SearchHistoryManager
import com.saikou.sozo_tv.utils.applyFocusedStyle
import com.saikou.sozo_tv.utils.hideKeyboard
import com.saikou.sozo_tv.utils.resetStyle
import com.saikou.sozo_tv.utils.showKeyboard
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchScreen : Fragment() {
    private var _binding: SearchScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private lateinit var searchHistoryManager: SearchHistoryManager
    private val model: SearchViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SearchScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchHistoryManager = SearchHistoryManager(requireContext())
        setupRecyclerView()
        setupRecentSearches()
        initializeSearch()
        observeViewModel()
        setupTVFocusHandling()
        binding.searchEdt.requestFocus()
    }

    private fun setupTVFocusHandling() {
        binding.searchEdt.setOnFocusChangeListener { edit, hasFocus ->
            if (hasFocus) {
                edit.showKeyboard()
                edit.applyFocusedStyle()
                showRecentSearchesIfEmpty()
            } else {
                edit.resetStyle()
                // Don't hide recent searches immediately to allow navigation
            }
        }

        binding.vgvRecentSearches.isFocusable = true
        binding.vgvRecentSearches.isFocusableInTouchMode = false
        binding.clearAllHistory.isFocusable = true
        binding.clearAllHistory.isFocusableInTouchMode = false
    }

    private fun setupRecentSearches() {
        recentSearchAdapter = RecentSearchAdapter()

        recentSearchAdapter.setOnItemClickListener { query ->
            binding.searchEdt.setText(query)
            binding.searchEdt.setSelection(query.length)
            model.searchAnime(query.trim())
            searchHistoryManager.addSearchQuery(query.trim())
            hideRecentSearches()
            binding.searchEdt.hideKeyboard()
            binding.searchEdt.requestFocus()
        }

        recentSearchAdapter.setOnRemoveClickListener { query, position ->
            searchHistoryManager.removeSearchQuery(query)
            recentSearchAdapter.removeItem(position)
            updateRecentSearchesVisibility()
            if (recentSearchAdapter.itemCount > 0) {
                binding.vgvRecentSearches.requestFocus()
            } else {
                binding.searchEdt.requestFocus()
            }
        }

        binding.vgvRecentSearches.adapter = recentSearchAdapter

        binding.clearAllHistory.setOnClickListener {
            searchHistoryManager.clearAllHistory()
            recentSearchAdapter.updateData(emptyList())
            hideRecentSearches()
            binding.searchEdt.requestFocus()
        }

        binding.vgvRecentSearches.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && recentSearchAdapter.itemCount > 0) {
                // Ensure first item gets focus when container is focused
                binding.vgvRecentSearches.post {
                    binding.vgvRecentSearches.getChildAt(0)?.requestFocus()
                }
            }
        }

        loadRecentSearches()
    }

    private fun loadRecentSearches() {
        val recentSearches = searchHistoryManager.getSearchHistory()
        recentSearchAdapter.updateData(recentSearches)
    }

    private fun showRecentSearchesIfEmpty() {
        val query = binding.searchEdt.text.toString().trim()
        if (query.isEmpty()) {
            clearSearchResults()
            loadRecentSearches()
            updateRecentSearchesVisibility()
            if (recentSearchAdapter.itemCount > 0) {
                binding.vgvRecentSearches.post {
                    binding.vgvRecentSearches.requestFocus()
                }
            }
        }
    }

    private fun hideRecentSearches() {
        binding.recentSearchesContainer.visibility = View.GONE
    }

    private fun clearSearchResults() {
        binding.vgvSearch.visibility = View.GONE
        binding.placeHolder.root.visibility = View.GONE
        searchAdapter.updateData(emptyList())
    }

    private fun updateRecentSearchesVisibility() {
        val hasRecentSearches = searchHistoryManager.getSearchHistory().isNotEmpty()
        binding.recentSearchesContainer.visibility = if (hasRecentSearches) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        model.searchResults.observe(viewLifecycleOwner) { movies ->
            Log.d("GGG", "observeViewModel:${movies.size} ")
            hideRecentSearches()

            if (movies.isNotEmpty()) {
                Log.d("GGG", "observeViewModel:${movies} ")
                binding.vgvSearch.visibility = View.VISIBLE
                searchAdapter.updateData(movies)
                updateRecyclerViewHeight(movies.size)
                binding.placeHolder.root.visibility = View.GONE
            } else {
                binding.vgvSearch.visibility = View.GONE
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = "Result not found"
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
            }
        }
        lifecycleScope.launch {
            model.errorData.observe(viewLifecycleOwner) {
                hideRecentSearches()
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = it
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter()
        searchAdapter.setOnItemClickListener {
            Log.d("GGG", "setupRecyclerView:${it} ")
            val intent = Intent(requireActivity(), PlayerActivity::class.java)
            intent.putExtra("model", it.id)
            requireActivity().startActivity(intent)
        }

        binding.vgvSearch.adapter = searchAdapter
    }

    private fun updateRecyclerViewHeight(itemCount: Int) {
        val itemHeight = resources.getDimensionPixelSize(R.dimen.item_height)
        val itemMargin = resources.getDimensionPixelSize(R.dimen.item_margin)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.rv_max_height)

        val newHeight = if (itemCount > 0) {
            (itemHeight + itemMargin) * itemCount
        } else {
            0
        }

        binding.vgvSearch.layoutParams = binding.vgvSearch.layoutParams.apply {
            height = newHeight.coerceAtMost(maxHeight)
        }
    }

    private fun initializeSearch() {
        binding.searchEdt.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.isEmpty() && hasFocus()) {
                        showRecentSearchesIfEmpty()
                    } else {
                        hideRecentSearches()
                        if (query.isNotEmpty()) {
                            clearSearchResults()
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId in listOf(
                        EditorInfo.IME_ACTION_GO,
                        EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_SEND,
                        EditorInfo.IME_ACTION_NEXT,
                        EditorInfo.IME_ACTION_DONE
                    )
                ) {
                    val query = text.toString()
                    if (query.isNotEmpty()) {
                        model.searchAnime(query.trim())
                        searchHistoryManager.addSearchQuery(query.trim())
                        hideKeyboard()
                        hideRecentSearches()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
}

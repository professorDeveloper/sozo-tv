package com.saikou.sozo_tv.presentation.screens.search

import android.annotation.SuppressLint
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
import com.google.android.material.internal.ViewUtils.hideKeyboard
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.RecentSearchAdapter
import com.saikou.sozo_tv.adapters.SearchAdapter
import com.saikou.sozo_tv.databinding.SearchScreenBinding
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.utils.SearchHistoryManager
import com.saikou.sozo_tv.utils.CustomTVKeyboard
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
    private var isInitialLoad = true

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
        setupCustomKeyboard()
        initializeSearch()
        observeViewModel()
        setupTVFocusHandling()
        showInitialRecentSearches()
        if (searchHistoryManager.getSearchHistory().isEmpty()) {
            binding.searchEdt.requestFocus()
        }
    }

    private fun setupCustomKeyboard() {
        binding.customKeyboard.setOnKeyClickListener { key ->
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            val newText = StringBuilder(currentText).insert(cursorPosition, key).toString()
            binding.searchEdt.setText(newText)
            binding.searchEdt.setSelection(cursorPosition + 1)
        }

        binding.customKeyboard.setOnBackspaceClickListener {
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            if (cursorPosition > 0) {
                val newText = StringBuilder(currentText).deleteCharAt(cursorPosition - 1).toString()
                binding.searchEdt.setText(newText)
                binding.searchEdt.setSelection(cursorPosition - 1)
            }
        }

        binding.customKeyboard.setOnClearClickListener {
            binding.searchEdt.setText("")
            binding.searchEdt.setSelection(0)
            showInitialRecentSearches()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setupTVFocusHandling() {
        binding.searchEdt.setOnFocusChangeListener { edit, hasFocus ->
            if (hasFocus) {
                hideKeyboard(edit)
                edit.applyFocusedStyle()
            } else {
                edit.resetStyle()
            }
        }

        binding.vgvRecentSearches.isFocusable = true
        binding.vgvRecentSearches.isFocusableInTouchMode = false
        binding.clearAllHistory.isFocusable = true
        binding.clearAllHistory.isFocusableInTouchMode = false

        binding.vgvSearch.isFocusable = true
        binding.vgvSearch.isFocusableInTouchMode = false
    }

    private fun setupRecentSearches() {
        recentSearchAdapter = RecentSearchAdapter()

        recentSearchAdapter.setOnItemClickListener { query ->
            binding.searchEdt.setText(query)
            binding.searchEdt.setSelection(query.length)
            performSearch(query.trim())
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

    private fun showInitialRecentSearches() {
        if (binding.searchEdt.text.toString().trim().isEmpty()) {
            clearSearchResults()
            loadRecentSearches()
            updateRecentSearchesVisibility()
            binding.recommendationsTitle.text = "Your Search Recommendations"
            binding.recommendationsTitle.visibility = View.VISIBLE
            isInitialLoad = false
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
            Log.d("SearchScreen", "Search results: ${movies.size}")
            hideRecentSearches()

            if (movies.isNotEmpty()) {
                binding.vgvSearch.visibility = View.VISIBLE
                searchAdapter.updateData(movies)
                binding.placeHolder.root.visibility = View.GONE
                binding.vgvSearch.post {
                    binding.vgvSearch.requestFocus()
                }
            } else {
                binding.vgvSearch.visibility = View.GONE
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = "No results found"
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
            }
        }

        lifecycleScope.launch {
            model.errorData.observe(viewLifecycleOwner) { errorMessage ->
                hideRecentSearches()
                binding.vgvSearch.visibility = View.GONE
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = errorMessage
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter()
        searchAdapter.setOnItemClickListener { searchModel ->
            Log.d("SearchScreen", "Item clicked: ${searchModel.title}")
            val intent = Intent(requireActivity(), PlayerActivity::class.java)
            intent.putExtra("model", searchModel.id)
            requireActivity().startActivity(intent)
        }

        binding.vgvSearch.adapter = searchAdapter
    }

    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            model.searchAnime(query.trim())
            searchAdapter.setQueryText(query.trim())
            searchHistoryManager.addSearchQuery(query.trim())
            hideRecentSearches()
            binding.recommendationsTitle.visibility = View.VISIBLE
            binding.recommendationsTitle.text = "Search Results for \"$query\""
        }
    }

    private fun initializeSearch() {
        binding.searchEdt.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.isEmpty()) {
                        showInitialRecentSearches()
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
                    performSearch(query)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

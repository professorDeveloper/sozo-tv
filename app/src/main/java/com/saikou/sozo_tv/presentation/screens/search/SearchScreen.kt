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
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SearchAdapter
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.SearchScreenBinding
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.utils.SearchHistoryManager
import com.saikou.sozo_tv.utils.CustomTVKeyboard
import com.saikou.sozo_tv.utils.applyFocusedStyle
import com.saikou.sozo_tv.utils.hideKeyboard
import com.saikou.sozo_tv.utils.resetStyle
import com.saikou.sozo_tv.utils.showKeyboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchScreen : Fragment() {
    private var _binding: SearchScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var searchHistoryManager: SearchHistoryManager
    private val model: SearchViewModel by viewModel()
    private var searchJob: Job? = null
    private var lastSearchQuery = ""
    val preference = PreferenceManager()

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
        setupCustomKeyboard()
        initializeSearch()
        observeViewModel()
        setupTVFocusHandling()
        showInitialState()
        preventSystemKeyboard()
        binding.searchEdt.requestFocus()
    }

    private fun preventSystemKeyboard() {
        binding.searchEdt.apply {
            showSoftInputOnFocus = false
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    view.applyFocusedStyle()
                } else {
                    view.resetStyle()
                }
            }
            setOnClickListener {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(it.windowToken, 0)
                it.requestFocus()
            }
        }
    }

    private fun setupCustomKeyboard() {
        binding.customKeyboard.setOnKeyClickListener { key ->
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            val newText = StringBuilder(currentText).insert(cursorPosition, key).toString()
            binding.searchEdt.setText(newText)
            binding.searchEdt.setSelection(cursorPosition + 1)
            if (newText.trim().isNotEmpty()) {
                scheduleSearch(newText.trim())
            }
        }

        binding.customKeyboard.setOnBackspaceClickListener {
            val currentText = binding.searchEdt.text.toString()
            val cursorPosition = binding.searchEdt.selectionStart
            if (cursorPosition > 0) {
                val newText = StringBuilder(currentText).deleteCharAt(cursorPosition - 1).toString()
                binding.searchEdt.setText(newText)
                binding.searchEdt.setSelection(cursorPosition - 1)
                if (newText.trim().isEmpty()) {
                    cancelPendingSearch()
                    showInitialState()
                } else {
                    scheduleSearch(newText.trim())
                }
            }
        }

        binding.customKeyboard.setOnClearClickListener {
            binding.searchEdt.setText("")
            binding.searchEdt.setSelection(0)
            cancelPendingSearch()
            showInitialState()
        }
    }

    private fun setupTVFocusHandling() {
        binding.vgvSearch.isFocusable = true
        binding.vgvSearch.isFocusableInTouchMode = false
    }

    private fun showInitialState() {
        if (binding.searchEdt.text.toString().trim().isEmpty()) {
            clearSearchResults()
            binding.recommendationsTitle.text = "Your Search Recommendations"
            binding.recommendationsTitle.visibility = View.VISIBLE
        }
    }

    private fun clearSearchResults() {
        binding.vgvSearch.visibility = View.GONE
        binding.placeHolder.root.visibility = View.GONE
        searchAdapter.updateData(emptyList())
    }

    private fun observeViewModel() {
        model.searchResults.observe(viewLifecycleOwner) { movies ->
            Log.d("SearchScreen", "Search results: ${movies.size}")

            if (movies.isNotEmpty()) {
                binding.vgvSearch.visibility = View.VISIBLE
                searchAdapter.updateData(movies)
                binding.placeHolder.root.visibility = View.GONE
                binding.recommendationsTitle.visibility = View.VISIBLE
                binding.recommendationsTitle.text = "Search Results for \"${binding.searchEdt.text.toString().trim()}\""
            } else {
                binding.vgvSearch.visibility = View.GONE
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = "No results found"
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_place_holder_search)
            }
        }

        lifecycleScope.launch {
            model.errorData.observe(viewLifecycleOwner) { errorMessage ->
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

    private fun performSearchImmediate(query: String) {
        if (query.isNotEmpty()) {
            if (preference.isModeAnimeEnabled()){
                model.searchAnime(query.trim())
            }else {
                model.searchMovie(query.trim())
            }
            searchAdapter.setQueryText(query.trim())
            searchHistoryManager.addSearchQuery(query.trim())
            binding.recommendationsTitle.visibility = View.VISIBLE
            binding.recommendationsTitle.text = "Search Results for \"$query\""
        }
    }

    private fun performSearch(query: String) {
        if (query.trim().length >= 2) {
            scheduleSearch(query.trim())
        }
    }

    private fun scheduleSearch(query: String) {
        searchJob?.cancel()

        if (query != lastSearchQuery && query.length >= 2) {
            searchJob = lifecycleScope.launch {
                delay(800)
                performSearchImmediate(query)
                lastSearchQuery = query
            }
        }
    }

    private fun cancelPendingSearch() {
        searchJob?.cancel()
        searchJob = null
        lastSearchQuery = ""
    }

    private fun initializeSearch() {
        binding.searchEdt.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.isEmpty()) {
                        cancelPendingSearch()
                        showInitialState()
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
                    performSearchImmediate(query)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelPendingSearch()
        _binding = null
    }
}

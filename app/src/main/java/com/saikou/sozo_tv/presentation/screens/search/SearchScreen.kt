package com.saikou.sozo_tv.presentation.screens.search

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.kongzue.dialogx.dialogs.WaitDialog
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SearchAdapter
import com.saikou.sozo_tv.databinding.SearchScreenBinding
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.utils.applyFocusedStyle
import com.saikou.sozo_tv.utils.hideKeyboard
import com.saikou.sozo_tv.utils.resetStyle
import com.saikou.sozo_tv.utils.showKeyboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchScreen : Fragment() {
    private var _binding: SearchScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter
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
        setupRecyclerView()
        initializeSearch()
        observeViewModel()
        binding.searchEdt.requestFocus()
        binding.searchEdt.setOnFocusChangeListener { edit, hasFocus ->
            if (hasFocus) {
                edit.showKeyboard()
                edit.applyFocusedStyle()
            } else {
                edit.resetStyle()
            }
        }

    }


    private fun observeViewModel() {
        model.searchResults.observe(viewLifecycleOwner) { movies ->
            Log.d("GGG", "observeViewModel:${movies.size} ")
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
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.placeHolder.placeholderTxt.text = it
                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
            }
        }

    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter()
        searchAdapter.setOnItemClickListener {


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
                        hideKeyboard()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
}


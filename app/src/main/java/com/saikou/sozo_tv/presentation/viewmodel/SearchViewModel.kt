package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repo: SearchRepository,
) : ViewModel() {
    private val _searchResults = MutableLiveData<List<SearchModel>>()
    val searchResults get() = _searchResults
    var lastQuery = ""
    val errorData = MutableLiveData<String>()
    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> get() = _loading

    fun searchAnime(query: String) {
        if (lastQuery != query) {
            viewModelScope.launch {
                _loading.value = true
                val result = repo.searchAnime(query)
                result.onSuccess {
                    if (it.isNotEmpty()) {
                        _loading.value = false
                        _searchResults.value = it
                        lastQuery = query
                    } else {
                        _loading.value = false
                        _searchResults.value = arrayListOf()
                        lastQuery = query
                    }
                }.onFailure {
                    errorData.value = it.message
                    Log.d("GG", "search:${it.message} ")
                    _searchResults.value = emptyList()
                }
            }
        }
    }

    /**
     * "All sources" search. Kept separate from [searchAnime]/[searchMovie]
     * rather than folded in behind a flag, because the screen has to be able to
     * re-run the SAME query in the other mode — sharing `lastQuery` de-dup with
     * them would swallow that second run as a no-op.
     */
    fun searchAllSources(query: String) {
        if (lastGlobalQuery == query) return
        viewModelScope.launch {
            _loading.value = true
            repo.searchAllSources(query)
                .onSuccess {
                    _loading.value = false
                    _searchResults.value = it
                    lastGlobalQuery = query
                    // Let a later single-source search of the same text still run.
                    lastQuery = ""
                }
                .onFailure {
                    _loading.value = false
                    errorData.value = it.message
                    _searchResults.value = emptyList()
                }
        }
    }

    private var lastGlobalQuery = ""

    fun searchMovie(query: String) {
        if (lastQuery != query) {
            viewModelScope.launch {
                _loading.value = true
                val result = repo.searchMovie(query)
                result.onSuccess {
                    if (it.isNotEmpty()) {
                        _loading.value = false
                        _searchResults.value = it
                        lastQuery = query
                    } else {
                        _loading.value = false
                        _searchResults.value = arrayListOf()
                        lastQuery = query
                    }
                }.onFailure {
                    errorData.value = it.message
                    Log.d("GG", "search:${it.message} ")
                    _searchResults.value = emptyList()
                }
            }
        }
    }


}
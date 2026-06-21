package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.remote.anilist.AniListRepository
import com.saikou.sozo_tv.data.remote.anilist.toSearchModel
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repo: SearchRepository,
    private val aniListRepo: AniListRepository,
) : ViewModel() {
    private val _searchResults = MutableLiveData<List<SearchModel>>()
    val searchResults get() = _searchResults
    var lastQuery = ""
    val errorData = MutableLiveData<String>()
    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> get() = _loading

    /** Catalog search via AniList GraphQL (the search screen's primary source). */
    fun searchAniList(query: String) {
        if (lastQuery == query) return
        viewModelScope.launch {
            _loading.value = true
            aniListRepo.searchAnime(query)
                .onSuccess { media ->
                    _loading.value = false
                    _searchResults.value = media.map { it.toSearchModel() }
                    lastQuery = query
                }
                .onFailure {
                    _loading.value = false
                    errorData.value = it.message
                    Log.d("GG", "anilist search:${it.message} ")
                    _searchResults.value = emptyList()
                }
        }
    }

    fun isAniListLoggedIn(): Boolean = aniListRepo.isLoggedIn()

    /** Add/update [mediaId] on the user's AniList list with [status] (MediaListStatus value). */
    fun manageOnAniList(
        mediaId: Int,
        status: String,
        progress: Int?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(aniListRepo.saveListEntry(mediaId, status, progress))
        }
    }

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
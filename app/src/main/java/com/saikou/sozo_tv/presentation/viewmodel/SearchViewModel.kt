package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.data.extensions.SearchLegStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
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
     * "All sources" search.
     *
     * Kept separate from [searchAnime]/[searchMovie] rather than folded in
     * behind a flag, because the screen has to be able to re-run the SAME query
     * in the other mode — sharing `lastQuery` de-dup with them would swallow
     * that second run as a no-op.
     *
     * Results are published as each source answers instead of after all of them
     * do. The previous version awaited the whole fan-out, so one dead mirror
     * held the screen blank for the full timeout while eight sources had already
     * replied — which is what made this feel broken rather than slow.
     */
    fun searchAllSources(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return

        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch {
            _loading.value = true
            _searchProgress.value = SearchProgress()
            val merged = LinkedHashMap<String, SearchModel>()
            var answered = 0
            var withResults = 0

            try {
                repo.searchAllSources(q)
                    .onCompletion { _loading.value = false }
                    .collect { leg ->
                        answered++
                        if (leg.status == SearchLegStatus.OK) withResults++
                        for (item in leg.items) {
                            merged.putIfAbsent("${item.id}", item)
                        }
                        _searchResults.value = merged.values.toList()
                        _searchProgress.value = SearchProgress(
                            answered = answered,
                            sourcesWithResults = withResults,
                            lastSource = leg.providerName,
                        )
                    }
                lastGlobalQuery = q
                lastQuery = ""
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _loading.value = false
                errorData.value = t.message
            }
        }
    }

    private var globalSearchJob: Job? = null

    private val _searchProgress = MutableStateFlow(SearchProgress())
    val searchProgress: StateFlow<SearchProgress> get() = _searchProgress

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

data class SearchProgress(
    val answered: Int = 0,
    val sourcesWithResults: Int = 0,
    val lastSource: String? = null,
)

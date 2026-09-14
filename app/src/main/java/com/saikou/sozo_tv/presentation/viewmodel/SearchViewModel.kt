package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.extensions.SearchLegStatus
import com.saikou.sozo_tv.domain.model.SearchModel
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.utils.SearchRelevance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: SearchRepository) : ViewModel() {
    private val _searchResults = MutableLiveData<List<SearchModel>>()
    val searchResults get() = _searchResults
    val errorData = MutableLiveData<String>()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> get() = _loading
    private val _searchProgress = MutableStateFlow(SearchProgress())
    val searchProgress: StateFlow<SearchProgress> get() = _searchProgress

    private var searchJob: Job? = null
    private var generation = 0L

    // One owner across all search modes. Switching scope also invalidates old work.
    private fun search(query: String, block: suspend (String) -> Unit) {
        val request = ++generation
        searchJob?.cancel()
        errorData.value = null
        _searchResults.value = emptyList()
        _searchProgress.value = SearchProgress()
        val q = query.trim()
        _loading.value = q.isNotEmpty()
        if (q.isEmpty()) return
        searchJob = viewModelScope.launch {
            try {
                block(q)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (request == generation) errorData.value = t.message
            } finally {
                // A cancelled request must not hide the new request's spinner.
                if (request == generation) _loading.value = false
            }
        }
    }

    private suspend fun publishSingle(result: Result<List<SearchModel>>, query: String) {
        currentCoroutineContext().ensureActive()
        val items = result.getOrThrow()
        _searchResults.value = if (SearchRelevance.looksUnsearched(items, query) { it.title }) {
            emptyList()
        } else {
            SearchRelevance.rank(items, query) { it.title }
        }
    }

    fun searchAnime(query: String) = search(query) { publishSingle(repo.searchAnime(it), it) }

    fun searchMovie(query: String) = search(query) { publishSingle(repo.searchMovie(it), it) }

    /** Incremental results from every installed source, ranked as each source answers. */
    fun searchAllSources(query: String) = search(query) { q ->
        val merged = LinkedHashMap<Int?, SearchModel>()
        var answered = 0
        var withResults = 0
        repo.searchAllSources(q).collect { leg ->
            currentCoroutineContext().ensureActive()
            answered++
            val items = if (SearchRelevance.looksUnsearched(leg.items, q) { it.title }) {
                emptyList()
            } else {
                leg.items
            }
            if (leg.status == SearchLegStatus.OK && items.isNotEmpty()) withResults++
            for (item in items) merged.putIfAbsent(item.id, item)
            _searchResults.value = SearchRelevance.rank(merged.values.toList(), q) { it.title }
            _searchProgress.value = SearchProgress(answered, withResults, leg.providerName)
        }
    }
}

data class SearchProgress(
    val answered: Int = 0,
    val sourcesWithResults: Int = 0,
    val lastSource: String? = null,
)

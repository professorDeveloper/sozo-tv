package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.CategoryChip
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.utils.UiState
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: CategoriesRepository) : ViewModel() {
    val result: MutableLiveData<SearchResults?> = MutableLiveData()
    val nextPageResult: MutableLiveData<SearchResults?> = MutableLiveData()
    val updateFilter: MutableLiveData<UiState<SearchResults>?> = MutableLiveData()
    val genreChips: MutableLiveData<List<CategoryChip>> = MutableLiveData()
    lateinit var searchResults: SearchResults

    /**
     * Loads the filter-row chips from the active provider's real catalog
     * (genres -> home sections -> empty for the local fallback). Posts to [genreChips].
     */
    fun loadGenreChips() {
        viewModelScope.launch {
            val res = repo.loadGenres()
            genreChips.postValue(res.getOrNull().orEmpty())
        }
    }

    fun loadCategories(r: SearchResults) {
        viewModelScope.launch {
            val catResult = repo.loadAnimeByGenre(r)
            // A failure used to be logged and nothing else, so the chip's spinner — which the
            // screen shows and never hides on its own — ran forever. updateFilter already has
            // an error rendering; route it there.
            val results = catResult.getOrNull()?.results
            when {
                catResult.isFailure -> updateFilter.postValue(
                    UiState.Error(catResult.exceptionOrNull()?.message ?: "Could not load this category.")
                )

                results.isNullOrEmpty() -> updateFilter.postValue(
                    UiState.Error("Nothing under \"${r.genre}\" on this source.")
                )

                else -> result.postValue(catResult.getOrNull())
            }
        }
    }

    fun loadNextPage(r: SearchResults) {
        val data = r.copy(currentPage = r.currentPage + 1)
        viewModelScope.launch {
            val catResult = repo.loadAnimeByGenre(data)
            if (catResult.isSuccess) {
                nextPageResult.postValue(catResult.getOrNull())
            }
        }
    }


    fun loadCategoriesMovie(r: SearchResults) {
        viewModelScope.launch {
            val catResult = repo.loadMovieByGenre(r)
            // A failure used to be logged and nothing else, so the chip's spinner — which the
            // screen shows and never hides on its own — ran forever. updateFilter already has
            // an error rendering; route it there.
            val results = catResult.getOrNull()?.results
            when {
                catResult.isFailure -> updateFilter.postValue(
                    UiState.Error(catResult.exceptionOrNull()?.message ?: "Could not load this category.")
                )

                results.isNullOrEmpty() -> updateFilter.postValue(
                    UiState.Error("Nothing under \"${r.genre}\" on this source.")
                )

                else -> result.postValue(catResult.getOrNull())
            }
        }
    }

    fun loadNextPageMovie(r: SearchResults) {
        val data = r.copy(currentPage = r.currentPage + 1)
        viewModelScope.launch {
            val catResult = repo.loadMovieByGenre(data)
            if (catResult.isSuccess) {
                nextPageResult.postValue(catResult.getOrNull())
            }
        }
    }


    fun loadFilter(searchResults: SearchResults) {
        val data = searchResults.copy(currentPage = searchResults.currentPage)
        updateFilter.postValue(UiState.Loading)
        viewModelScope.launch {
            updateFilter.postValue(UiState.Loading)
            val catResult = repo.loadAnimeByGenre(data)
            if (catResult.isSuccess) {
                updateFilter.postValue(UiState.Success(catResult.getOrNull()!!))
            } else {
                updateFilter.postValue(
                    UiState.Error(
                        catResult.exceptionOrNull()?.message ?: "Expected Error !"
                    )
                )
            }
        }
    }
}
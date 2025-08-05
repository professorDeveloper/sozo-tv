package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.utils.UiState
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: CategoriesRepository) : ViewModel() {
    val result: MutableLiveData<SearchResults?> = MutableLiveData()
    val nextPageResult: MutableLiveData<SearchResults?> = MutableLiveData()
    val updateFilter: MutableLiveData<UiState<SearchResults>?> = MutableLiveData()
    lateinit var searchResults: SearchResults
    // UI-da ko‘rsatish uchun ViewModel'ga LiveData qo‘shing


    fun loadCategories(r: SearchResults) {
        viewModelScope.launch {
            val catResult = repo.loadAnimeByGenre(r)
            if (catResult.isSuccess) {
                Log.d("TAG", "loadSearch: ${catResult.getOrNull()!!.results}")
                result.postValue(catResult.getOrNull())
            } else {
                Log.d("TAG", "loadSearch: ${catResult.exceptionOrNull()!!.message}")
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
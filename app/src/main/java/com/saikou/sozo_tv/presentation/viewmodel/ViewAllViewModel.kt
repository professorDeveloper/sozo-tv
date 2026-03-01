package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.RowId
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.ViewAllRepository
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class ViewAllViewModel(private val repo: ViewAllRepository) : ViewModel() {

    private val _state = MutableLiveData<Resource<List<MainModel>>>(Resource.Idle)
    val state: LiveData<Resource<List<MainModel>>> = _state

    private val _loadMoreState = MutableLiveData<Resource<List<MainModel>>>(Resource.Idle)
    val loadMoreState: LiveData<Resource<List<MainModel>>> = _loadMoreState

    private val allItems = mutableListOf<MainModel>()
    private var currentPage = 1
    private var totalPages = 1
    private var currentRowId: RowId? = null
    val hasMore get() = currentPage <= totalPages

    fun init(rowId: RowId) {
        if (currentRowId == rowId) return
        currentRowId = rowId
        currentPage = 1
        allItems.clear()
        loadNextPage()
    }

    fun loadNextPage() {
        val rowId = currentRowId ?: return
        if (!hasMore) return
        if (_loadMoreState.value is Resource.Loading || _state.value is Resource.Loading) return

        viewModelScope.launch {
            val isFirstPage = currentPage == 1

            if (isFirstPage) _state.value = Resource.Loading
            else _loadMoreState.value = Resource.Loading

            repo.loadMore(rowId, currentPage)
                .onSuccess { paginated ->
                    totalPages = paginated.totalPages
                    currentPage = paginated.page + 1
                    allItems.addAll(paginated.list)

                    if (isFirstPage) {
                        _state.value = Resource.Success(allItems.toList())
                    } else {
                        _loadMoreState.value = Resource.Success(paginated.list) // faqat yangi itemlar
                    }
                }
                .onFailure { error ->
                    if (isFirstPage) _state.value = Resource.Error(error)
                    else _loadMoreState.value = Resource.Error(error)
                }
        }
    }
}
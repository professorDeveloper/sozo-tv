package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.repository.HomeRepository
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.utils.UiState
import com.saikou.sozo_tv.utils.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class HomeViewModel(private val repo: HomeRepository) : ViewModel() {
    private val _bannersState = MutableStateFlow<UiState<BannerModel>>(UiState.Idle)
    val bannersState: StateFlow<UiState<BannerModel>> get() = _bannersState
    private val _categoriesState = MutableStateFlow<UiState<List<Category>>>(UiState.Idle)
    val categoriesState: StateFlow<UiState<List<Category>>> get() = _categoriesState


    val homeDataState: StateFlow<UiState<List<HomeAdapter.HomeData>>> = combine(
        bannersState, categoriesState
    ) { bannerState, categoryState ->
        when {
            bannerState is UiState.Loading || categoryState is UiState.Loading -> {
                UiState.Loading
            }

            bannerState is UiState.Error -> {
                UiState.Error(bannerState.message)
            }

            categoryState is UiState.Error -> {
                UiState.Error(categoryState.message)
            }


            bannerState is UiState.Success && categoryState is UiState.Success -> {
                val homeDataList = mutableListOf<HomeAdapter.HomeData>()
                homeDataList.add(bannerState.data)
                homeDataList.addAll(categoryState.data)
                UiState.Success(homeDataList)
            }


            else -> UiState.Idle
        }
    }.stateIn(
        viewModelScope, SharingStarted.Lazily, UiState.Idle
    )

    init {
        loadBanners()
        loadCategories()
    }

    fun loadBanners() {
        viewModelScope.launch {
            _bannersState.value = UiState.Loading
            val result = repo.getTopBannerAnime()
            _bannersState.value = if (result.isSuccess) {
                UiState.Success(result.getOrNull()!!.toDomain())
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _categoriesState.value = UiState.Loading
            val result = repo.loadCategories()
            _categoriesState.value = when {
                result.isSuccess -> UiState.Success(result.getOrNull()!!)
                result.isFailure -> UiState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )

                else -> UiState.Idle
            }
        }
    }

}
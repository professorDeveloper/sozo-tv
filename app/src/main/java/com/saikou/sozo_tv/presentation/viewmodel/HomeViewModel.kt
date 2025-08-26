package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.repository.HomeRepository
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.utils.Resource
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

    val genresState = MutableStateFlow<UiState<CategoryGenre>>(UiState.Idle)

    val homeDataState: StateFlow<UiState<List<HomeAdapter.HomeData>>> = combine(
        bannersState, categoriesState, genresState
    ) { bannerState, categoryState, genresState ->
        when {
            bannerState is UiState.Loading || categoryState is UiState.Loading || genresState is UiState.Loading -> {
                UiState.Loading
            }

            bannerState is UiState.Error -> {
                Log.d("GGG", "bannerState: ${bannerState.message}")
                UiState.Error(bannerState.message)
            }

            categoryState is UiState.Error -> {
                Log.d("GGG", "categoryState: ${categoryState.message}")
                UiState.Error(categoryState.message)
            }

            genresState is UiState.Error -> {
                Log.d("GGG", "genresState: ${genresState.message}")
                UiState.Error(genresState.message)
            }

            bannerState is UiState.Success && categoryState is UiState.Success && genresState is UiState.Success -> {
                val homeDataList = mutableListOf<HomeAdapter.HomeData>()
                homeDataList.add(bannerState.data)
                homeDataList.add(genresState.data)
                homeDataList.addAll(categoryState.data)
                UiState.Success(homeDataList)
            }


            else -> UiState.Idle
        }
    }.stateIn(
        viewModelScope, SharingStarted.Lazily, UiState.Idle
    )

    val aniId = MutableLiveData<Resource<Int>>(Resource.Idle)

    fun getMalId(id: Int) {
        viewModelScope.launch {
            val result = repo.convertMalId(id)
            if (result.isSuccess) {
                aniId.value = Resource.Success(result.getOrNull()!!)
            }
        }
    }

    init {
        loadBanners()
        loadCategories()
        loadGenres()
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

    fun loadGenres() {
        viewModelScope.launch {
            genresState.value = UiState.Loading
            val result = repo.loadGenres()
            genresState.value = when {
                result.isSuccess -> UiState.Success(result.getOrNull()!!.toDomain())
                result.isFailure -> {
                    Log.d("GGG", "loadGenres:${result.exceptionOrNull()?.message} ")
                    UiState.Error(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }

                else -> {
                    UiState.Idle
                }
            }
        }
    }
}
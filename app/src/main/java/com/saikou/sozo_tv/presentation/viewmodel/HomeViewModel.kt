package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.jikan.JikanBannerResponse
import com.saikou.sozo_tv.domain.model.BannerModel
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



    init {
        loadBanners()
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
}
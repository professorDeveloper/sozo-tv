package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import kotlinx.coroutines.launch

class CastDetailViewModel(private val repo: DetailRepository) : ViewModel() {
    val castDetail = MutableLiveData<CastDetailModel>()

    val error = MutableLiveData<String>()
    fun loadDetail(id: Int) {
        viewModelScope.launch {
            val result = repo.characterDetail(id)
            if (result.isSuccess) {
                castDetail.postValue(result.getOrNull()!!)
            } else {
                error.postValue(result.exceptionOrNull()?.message)
            }
        }
    }
}
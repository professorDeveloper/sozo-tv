package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.repository.DetailRepositoryImpl
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import kotlinx.coroutines.launch

class PlayViewModel(private val repo: DetailRepository) : ViewModel() {
     val detailData = MutableLiveData<DetailCategory>()
     val errorData = MutableLiveData<String>()
    fun loadAnimeById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadAnimeDetail(id)
            if (result.isSuccess) {
                detailData.postValue(DetailCategory(
                    content = result.getOrNull()!!
                ))
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }
}
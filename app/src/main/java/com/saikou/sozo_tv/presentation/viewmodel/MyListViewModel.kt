package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.MyListRepository
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class MyListViewModel(private val repo: MyListRepository) : ViewModel() {
    val listData = MutableLiveData<Resource<List<MainModel>>>(Resource.Idle)

    fun loadMyList(listType: String, userId: Int) {
        viewModelScope.launch {
            listData.postValue(Resource.Loading)
            val result = repo.getMyList(userId, listType)
            if (result.isSuccess) {
                val data = result.getOrNull() ?: emptyList()
                listData.postValue(Resource.Success(data))
            } else {
                listData.postValue(
                    Resource.Error(
                        result.exceptionOrNull() ?: Exception("Unknown Error")
                    )
                )
            }
        }
    }

}
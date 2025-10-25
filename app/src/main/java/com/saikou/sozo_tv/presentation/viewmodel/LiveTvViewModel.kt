package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.dao.ChannelDao
import com.saikou.sozo_tv.data.local.entity.ChannelsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveTvViewModel(private val dao: ChannelDao) : ViewModel() {
    val isBookmark = MutableLiveData<Boolean>()
    fun checkBookmark(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dao.getAllBookmarks()
            if (result.isNotEmpty()) {
                isBookmark.postValue(result.any { it.id == id })
            } else {
                isBookmark.postValue(false)
            }
        }
    }

    fun addBookmark(movie: ChannelsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertBookmark(movie)
            isBookmark.postValue(true)
        }
    }

    fun removeBookmark(movie: ChannelsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeBookmark(movie)
            isBookmark.postValue(false)
        }
    }
}
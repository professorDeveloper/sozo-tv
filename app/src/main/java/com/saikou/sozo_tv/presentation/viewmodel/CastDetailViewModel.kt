package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Local

class CastDetailViewModel(
    private val repo: DetailRepository,
    private val bookmarkRepo: CharacterBookmarkRepository
) : ViewModel() {
    val castDetail = MutableLiveData<CastDetailModel>()
    val error = MutableLiveData<String>()
    fun loadDetail(id: Int) {
        viewModelScope.launch {
            val result =
                if (LocalData.isAnimeEnabled) repo.characterDetail(id) else repo.creditDetail(id)
            if (result.isSuccess) {
                castDetail.postValue(result.getOrNull()!!)
            } else {
                Log.d("GGG", "loadDetail:${result.exceptionOrNull()?.message} ")
                error.postValue(result.exceptionOrNull()?.message)
            }
        }
    }

    val isBookmark = MutableLiveData<Boolean>()
    fun checkBookmark(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = bookmarkRepo.getAllBookmarks()
            if (result.isNotEmpty()) {
                isBookmark.postValue(result.any { it.id == id })
            } else {
                isBookmark.postValue(false)
            }
        }
    }

    fun addBookmark(movie: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepo.addBookmark(movie)
        }
    }

    fun removeBookmark(movie: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepo.removeBookmark(movie)
        }
    }
}
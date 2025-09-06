package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import kotlinx.coroutines.launch

class BookmarkViewModel(val bookmarkRepository: MovieBookmarkRepository,val characterRepo: CharacterBookmarkRepository) : ViewModel() {
    val bookmarkData = MutableLiveData<List<AnimeBookmark>>()
    fun getAllBookmarks() {
        viewModelScope.launch {
            bookmarkData.postValue(bookmarkRepository.getAllBookmarks())
        }
    }
}
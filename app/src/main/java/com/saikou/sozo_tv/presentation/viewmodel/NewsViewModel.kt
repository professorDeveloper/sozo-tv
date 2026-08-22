package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.services.FirebaseService
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewsViewModel(
    private val repository: FirebaseService
) : ViewModel() {

    /**
     * The list used to start empty and stay empty until the fetch returned, so
     * the screen said "no news" while the news was still on its way, and a
     * throwing fetch took the app down with it.
     */
    private val _news = MutableStateFlow<Resource<List<NewsItem>>>(Resource.Idle)
    val news: StateFlow<Resource<List<NewsItem>>> = _news

    fun loadNews() {
        _news.value = Resource.Loading
        viewModelScope.launch {
            _news.value = try {
                Resource.Success(repository.getNews())
            } catch (t: Throwable) {
                Resource.Error(t)
            }
        }
    }
}

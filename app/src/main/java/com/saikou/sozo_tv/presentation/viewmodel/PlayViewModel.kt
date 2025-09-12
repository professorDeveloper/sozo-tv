package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.remote.DubsMp4Parser
import com.saikou.sozo_tv.data.remote.LiveChartTrailer
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayViewModel(private val repo: DetailRepository,private val bookmarkRepo: MovieBookmarkRepository) : ViewModel() {
    val detailData = MutableLiveData<DetailCategory>()
    val relationsData = MutableLiveData<List<MainModel>>()
    val errorData = MutableLiveData<String>()
    val castResponseData = MutableLiveData<List<Cast>>()
    val trailerData = MutableLiveData<String>()

    val isBookmark = MutableLiveData<Boolean>()
    fun checkBookmark(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = bookmarkRepo.getAllBookmarks()
            if (result.isNotEmpty()) {
                isBookmark.postValue(result.any { it.id == id })
            }else {
                isBookmark.postValue(false)
            }
        }
    }

    fun addBookmark(movie: AnimeBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepo.addBookmark(movie)
        }
    }

    fun removeBookmark(movie: AnimeBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepo.removeBookmark(movie)
        }
    }
    fun loadRelations(id: Int) {
        viewModelScope.launch {
            val result = repo.loadAnimeRelations(id)
            if (result.isSuccess) {
                if (result.getOrNull()!!.isNotEmpty() && result.getOrNull()!!.size > 5) {
                    relationsData.postValue(result.getOrNull()!!)
                } else {
                    val resultRandom = repo.loadRandomAnime()
                    if (resultRandom.isSuccess) {
                        relationsData.postValue(resultRandom.getOrNull()!!)
                    } else {
                        errorData.postValue(resultRandom.exceptionOrNull()?.message)
                    }
                }
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }
    private var trailerJob: Job? = null

    fun loadCast(id: Int) {
        viewModelScope.launch {
            val result = repo.loadCast(id)
            if (result.isSuccess) {
                castResponseData.postValue(result.getOrNull()!!)
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }

    fun loadTrailer(name: String) {
        trailerJob?.cancel()
        trailerJob = viewModelScope.launch {
            val liveChartTrailer = LiveChartTrailer()
            val link = liveChartTrailer.searchAndGetTrailer(name)
            val ytList = liveChartTrailer.getTrailerByDetail(link.mediaLink)
            Log.d("GGG", "loadTrailer:${ytList} ")
            if (ytList.isEmpty()) {
                trailerData.postValue("")
            } else {
                val parser = DubsMp4Parser()
                parser.parseYt(ytList[0].mediaLink).let {
                    trailerData.postValue(it)
                }
            }
        }
    }

    fun cancelTrailerLoading() {
        trailerJob?.cancel()
    }

    fun loadAnimeById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadAnimeDetail(id)
            if (result.isSuccess) {
                detailData.postValue(
                    DetailCategory(
                        content = result.getOrNull()!!
                    )
                )
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }
}
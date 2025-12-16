package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repo: DetailRepository,
    private val bookmarkRepo: MovieBookmarkRepository,
) : ViewModel() {

    val detailData = MutableLiveData<DetailCategory>()
    val relationsData = MutableLiveData<List<MainModel>>()
    val castResponseData = MutableLiveData<List<Cast>>()
    val trailerData = MutableLiveData<String>()
    val isBookmark = MutableLiveData<Boolean>()
    val errorData = MutableLiveData<String>()

    private var trailerJob: Job? = null

    fun cancelTrailerLoading() {
        trailerJob?.cancel()
        trailerJob = null
    }

    fun checkBookmark(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = bookmarkRepo.getAllBookmarks()
            isBookmark.postValue(result.any { it.id == id })
        }
    }

    fun addBookmark(movie: AnimeBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            // preserve your existing isAnime behavior
            if (LocalData.isAnimeEnabled) bookmarkRepo.addBookmark(movie)
            else bookmarkRepo.addBookmark(movie.copy(isAnime = false))
            isBookmark.postValue(true)
        }
    }

    fun removeBookmark(movie: AnimeBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepo.removeBookmark(movie)
            isBookmark.postValue(false)
        }
    }

    fun loadAnimeById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadAnimeDetail(id)
            if (result.isSuccess) detailData.postValue(DetailCategory(content = result.getOrNull()!!))
            else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadMovieById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadMovieDetail(id)
            if (result.isSuccess) {
                detailData.postValue(
                    DetailCategory(content = result.getOrNull()!!.copy(episodes = 1))
                )
            } else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadSeriesById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadSeriesDetail(id)
            if (result.isSuccess) detailData.postValue(DetailCategory(content = result.getOrNull()!!))
            else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadRelations(id: Int) {
        viewModelScope.launch {
            val result = repo.loadAnimeRelations(id)
            if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                if (list.size > 5) relationsData.postValue(list)
                else {
                    val random = repo.loadRandomAnime()
                    if (random.isSuccess) relationsData.postValue(random.getOrNull().orEmpty())
                    else errorData.postValue(random.exceptionOrNull()?.message)
                }
            } else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadRelationsMovieOrSeries(id: Int, isMovie: Boolean) {
        viewModelScope.launch {
            val result = repo.loadMovieOrSeriesRelations(id, isMovie)
            if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                if (list.size > 5) relationsData.postValue(list)
            } else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadCast(id: Int) {
        viewModelScope.launch {
            val result = repo.loadCast(id)
            if (result.isSuccess) castResponseData.postValue(result.getOrNull().orEmpty())
            else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    fun loadCastSeriesOrMovie(id: Int, isMovie: Boolean) {
        viewModelScope.launch {
            val result = repo.loadCastMovieSeries(id, isMovie)
            if (result.isSuccess) castResponseData.postValue(result.getOrNull().orEmpty())
            else errorData.postValue(result.exceptionOrNull()?.message)
        }
    }

    /**
     * Your old loadTrailer() was basically a stub.
     * Keep it here so Detail owns trailer metadata, not Player.
     */
    fun loadTrailer(tmdbId: Int, isAnime: Boolean = true, isMovie: Boolean = false) {
        trailerJob?.cancel()
        trailerJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: implement real trailer fetch.
                // trailerData.postValue(url)
            } catch (e: Exception) {
                errorData.postValue(e.message)
            }
        }
    }
}

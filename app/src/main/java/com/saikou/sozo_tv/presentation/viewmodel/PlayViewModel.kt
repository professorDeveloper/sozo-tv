package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.EpisodeInfoEntity
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.data.remote.DubsMp4Parser
import com.saikou.sozo_tv.data.remote.LiveChartTrailer
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayViewModel(
    private val repo: DetailRepository,
    private val bookmarkRepo: MovieBookmarkRepository,
    private val watchHistoryRepository: WatchHistoryRepository,

    ) : ViewModel() {
    var doNotAsk: Boolean = false
    var lastPosition: Long = 0

    var currentEpIndex = -1
    val currentSelectedVideoOptionIndex = 0
    val detailData = MutableLiveData<DetailCategory>()
    val relationsData = MutableLiveData<List<MainModel>>()
    val errorData = MutableLiveData<String>()
    val castResponseData = MutableLiveData<List<Cast>>()
    val trailerData = MutableLiveData<String>()
    val isBookmark = MutableLiveData<Boolean>()
    var isWatched = false
    var epListFromLocal = ArrayList<EpisodeInfoEntity>()
    var getWatchedHistoryEntity: WatchHistoryEntity? = null

    val animePahe = AnimePahe()
    val currentEpisodeData = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    var seriesResponse: VodMovieResponse? = null
    val allEpisodeData = MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    fun getAllEpisodeByPage(page: Int, mediaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            allEpisodeData.postValue(Resource.Loading)
            animePahe.loadEpisodes(id = mediaId, curPage = page)?.let {

                allEpisodeData.postValue(Resource.Success(it))
            }
        }
    }

    suspend fun isWatched(session: String): Boolean {
        return watchHistoryRepository.isWatched(session)
    }

    suspend fun addHistory(history: WatchHistoryEntity) {
        watchHistoryRepository.addHistory(history)
    }

    suspend fun getWatchedEntity(id: String): WatchHistoryEntity? {
        return watchHistoryRepository.getWatchHistoryByVideoUrl(id)
    }

    suspend fun removeHistory(videoUrl: String) {
        watchHistoryRepository.removeHistory(videoUrl)
    }

    suspend fun updateHistory(history: WatchHistoryEntity) {
//        getWatchedHistoryEntity = history
        watchHistoryRepository.addHistory(history)
    }

    fun getCurrentEpisodeVod(episodeId: String, mediaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            currentEpisodeData.postValue(Resource.Loading)
            isWatched = isWatched(episodeId.toString())
            if (isWatched) {
                getWatchedHistoryEntity = getWatchedEntity(episodeId.toString())
//                epListFromLocal = getWatchedHistoryEntity!!.epList

            }
            animePahe.getEpisodeVideo(epId = episodeId, id = mediaId).let {
                animePahe.extractVideo(it[currentSelectedVideoOptionIndex].kwikUrl).let {
                    Log.d("GGG", "getCurrentEpisodeVod: ")

                    seriesResponse = VodMovieResponse(
                        authInfo = "",
                        subtitleList = "",
                        urlobj = it

                    )
                    currentEpisodeData.postValue(
                        Resource.Success(
                            VodMovieResponse(
                                authInfo = "",
                                subtitleList = "",
                                urlobj = it

                            )
                        )
                    )

                }
            }

        }
    }


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

    suspend fun getAllWatchHistory(): List<WatchHistoryEntity> {
        return watchHistoryRepository.getAllHistory()
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
//        trailerJob?.cancel()
//        trailerJob = viewModelScope.launch {
//            val liveChartTrailer = LiveChartTrailer()
//            val link = liveChartTrailer.searchAndGetTrailer(name)
//            val ytList = liveChartTrailer.getTrailerByDetail(link.mediaLink)
//            Log.d("GGG", "loadTrailer:${ytList} ")
//            if (ytList.isEmpty()) {
//                trailerData.postValue("")
//            } else {
//                val parser = DubsMp4Parser()
//                parser.parseYt(ytList[0].mediaLink).let {
//                    trailerData.postValue(it)
//                }
//            }
//        }
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

    fun clearAllHistory() {
        viewModelScope.launch {
            watchHistoryRepository.clearAllHistory()
        }
    }
}
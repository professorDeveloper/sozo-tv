package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.local.entity.AnimeBookmark
import com.saikou.sozo_tv.data.local.entity.EpisodeInfoEntity
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.data.remote.IMDBScraping
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.movie.PlayImdb
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.cleanImdbUrl
import com.saikou.sozo_tv.utils.extractImdbVideoId
import com.saikou.sozo_tv.utils.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayViewModel(
    private val repo: DetailRepository,
    private val bookmarkRepo: MovieBookmarkRepository,
    private val watchHistoryRepository: WatchHistoryRepository,

    ) : ViewModel() {
    val timeStamps = MutableLiveData<List<AniSkip.Stamp>?>()
    private val timeStampsMap: MutableMap<Int, List<AniSkip.Stamp>?> = mutableMapOf()
    var doNotAsk: Boolean = false
    var lastPosition: Long = 0
    var qualityProgress: Long = -1
    var seasons = mapOf<Int, Int>()
    var currentEpIndex = -1
    val videoOptionsData = MutableLiveData<List<VideoOption>>()
    val videoOptions = ArrayList<VideoOption>()
    var currentSelectedVideoOptionIndex = 0
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
    val playImdb = PlayImdb()
    val currentEpisodeData = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    val currentQualityEpisode = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
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


    fun getAllEpisodeByImdb(imdbId: String, tmdbId: Int, season: Int) {
        viewModelScope.launch {
            allEpisodeData.postValue(Resource.Loading)
            try {
                allEpisodeData.value = Resource.Loading

                val listData = ArrayList<Data>()

                playImdb.getEpisodes(imdbId).let { pairData ->
                    val list = pairData
                    val seasonCounts = list.groupingBy { it.season }.eachCount()
                    if (seasons.isEmpty()) seasons = seasonCounts
                    val backdrops = playImdb.getDetails(season, tmdbId)
                    val episodes = list.filter { it.season == season }
                    episodes.forEachIndexed { index, episode ->
                        listData.add(
                            episode.toDomain().copy(
                                episode2 = episode.episode,
                                episode = index + 1,
                                title = episode.title, // 1 dan tartib
                                snapshot = backdrops[index].originalUrl,
                                season = episode.season
                            )
                        )
                    }

                    allEpisodeData.value = Resource.Success(
                        EpisodeData(1, listData, 1, 1, "", -1, null, -1, 1)
                    )

                }
            } catch (e: Exception) {
                allEpisodeData.postValue(Resource.Error(e))
            }
        }
    }

    suspend fun loadTimeStamps(
        malId: Int?, episodeNum: Int?, duration: Long, useProxyForTimeStamps: Boolean
    ) {
        malId ?: return
        episodeNum ?: return
        if (timeStampsMap.containsKey(episodeNum)) return timeStamps.postValue(timeStampsMap[episodeNum])
        val result = AniSkip.getResult(malId, episodeNum, duration, useProxyForTimeStamps)
        timeStampsMap[episodeNum] = result
        timeStamps.postValue(result)
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


    fun updateQualityByIndex() {
        if (videoOptions.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                animePahe.extractVideo(videoOptions[currentSelectedVideoOptionIndex].kwikUrl).let {
                    Log.d("GGG", "getCurrentEpisodeVod: ")

                    seriesResponse = VodMovieResponse(
                        authInfo = "", subtitleList = "", urlobj = it

                    )
                    currentQualityEpisode.postValue(
                        Resource.Success(
                            VodMovieResponse(
                                authInfo = "", subtitleList = "", urlobj = it

                            )
                        )
                    )

                }
            }
        }
    }

    fun getCurrentEpisodeVodByImdb(
        imdbId: String, episodeId: String, iframe: String, isMovie: Boolean
    ) {
        try {
            viewModelScope.launch(Dispatchers.IO) {
                if (!isMovie) {
                    isWatched = isWatched(iframe.toString())
                    if (isWatched) {
                        getWatchedHistoryEntity = getWatchedEntity(episodeId.toString())
                        currentSelectedVideoOptionIndex =
                            getWatchedHistoryEntity?.currentQualityIndex ?: 0
                    }

                    currentEpisodeData.postValue(Resource.Loading)
                    playImdb.extractSeriesIframe( iframe)?.let {
                        println(it)
                        playImdb.convertRcptProctor( it).let {
                            println(it)
                            playImdb.extractDirectM3u8( it).let { m3u8Link ->
                                println(m3u8Link)
                                seriesResponse = VodMovieResponse(
                                    authInfo = "", subtitleList = "", urlobj = m3u8Link

                                )
                                currentEpisodeData.postValue(
                                    Resource.Success(
                                        VodMovieResponse(
                                            authInfo = "", subtitleList = "", urlobj = it

                                        )
                                    )
                                )

                            }
                        }
                    }
                } else {
                    isWatched = isWatched(iframe.toString())
                    if (isWatched) {
                        getWatchedHistoryEntity = getWatchedEntity(episodeId.toString())
                        currentSelectedVideoOptionIndex =
                            getWatchedHistoryEntity?.currentQualityIndex ?: 0
                    }

                    currentEpisodeData.postValue(Resource.Loading)
                    playImdb.convertRcptProctor( iframe).let {
                        println(it)
                        playImdb.extractDirectM3u8( it).let { m3u8Link ->
                            println(m3u8Link)
                            seriesResponse = VodMovieResponse(
                                authInfo = "", subtitleList = "", urlobj = m3u8Link

                            )
                            currentEpisodeData.postValue(
                                Resource.Success(
                                    VodMovieResponse(
                                        authInfo = "", subtitleList = "", urlobj = it

                                    )
                                )
                            )

                        }
                    }
                }

            }
        } catch (e: Exception) {
            currentEpisodeData.postValue(Resource.Error(e))
        }
    }

    fun getCurrentEpisodeVodAnime(episodeId: String, mediaId: String, isAnime: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            currentEpisodeData.postValue(Resource.Loading)
            isWatched = isWatched(episodeId.toString())
            if (isWatched) {
                getWatchedHistoryEntity = getWatchedEntity(episodeId.toString())
//                epListFromLocal = getWatchedHistoryEntity!!.epList
                currentSelectedVideoOptionIndex = getWatchedHistoryEntity?.currentQualityIndex ?: 0

            }
            animePahe.getEpisodeVideo(epId = episodeId, id = mediaId).let {
                videoOptionsData.postValue(it)
                videoOptions.clear()
                videoOptions.addAll(it)
                animePahe.extractVideo(it[currentSelectedVideoOptionIndex].kwikUrl).let {
                    Log.d("GGG", "getCurrentEpisodeVod: ")

                    seriesResponse = VodMovieResponse(
                        authInfo = "", subtitleList = "", urlobj = it

                    )
                    currentEpisodeData.postValue(
                        Resource.Success(
                            VodMovieResponse(
                                authInfo = "", subtitleList = "", urlobj = it

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
            if (LocalData.isAnimeEnabled) {
                bookmarkRepo.addBookmark(movie)
            } else {
                bookmarkRepo.addBookmark(movie.copy(isAnime = false))
            }
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

    fun loadRelationsMovieOrSeries(id: Int, isMovie: Boolean) {
        viewModelScope.launch {
            val result = repo.loadMovieOrSeriesRelations(id, isMovie)
            if (result.isSuccess) {
                if (result.getOrNull()!!.isNotEmpty() && result.getOrNull()!!.size > 5) {
                    relationsData.postValue(result.getOrNull()!!)
                }
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
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

    fun loadTrailer(name: String, isAnime: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isAnime) {
                    val trailer = IMDBScraping()
                    val list = trailer.searchMovie(name)
                    val trailerUrll = trailer.getTrailer(list)
                    val trailerMasterUrl =
                        trailer.getTrailerLink(trailerUrll.first.extractImdbVideoId().toString())
                    Log.d("GGG", "loadTrailer:original Link: ${trailerMasterUrl.cleanImdbUrl()} ")
                    trailerData.postValue(trailerMasterUrl.cleanImdbUrl())
                }
            } catch (e: Exception) {
                errorData.postValue(e.message)
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

    fun loadMovieById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadMovieDetail(id)
            if (result.isSuccess) {

                detailData.postValue(
                    DetailCategory(
                        content = result.getOrNull()!!.copy(episodes = 1)
                    )
                )
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }

    fun loadSeriesById(id: Int) {
        viewModelScope.launch {
            val result = repo.loadSeriesDetail(id)
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

    fun loadCastSeriesOrMovie(id: Int, movie: Boolean) {
        viewModelScope.launch {
            val result = repo.loadCastMovieSeries(id, isMovie = movie)
            if (result.isSuccess) {
                castResponseData.postValue(result.getOrNull()!!)
            } else {
                errorData.postValue(result.exceptionOrNull()?.message)
            }
        }
    }
}
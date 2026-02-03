package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SubtitleItem
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.extractor.Extractor
import com.saikou.sozo_tv.parser.extractor.VixSrcExtractor
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.movie.PlayImdb
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.MOVIE_SOURCE
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayMovieViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
) : ViewModel() {

    var doNotAsk: Boolean = false
    var lastPosition: Long = 0
    var qualityProgress: Long = -1

    var currentEpIndex: Int = -1
    var currentSubEpIndex: Int = 0

    val videoOptionsData = MutableLiveData<List<VideoOption>>()
    var currentSelectedVideoOptionIndex: Int = 0

    var isWatched: Boolean = false
    val isWatchedLiveData = MutableLiveData<Boolean>()
    var getWatchedHistoryEntity: WatchHistoryEntity? = null

    private val playImdb = PlayImdb()
    private val currentExtractor by lazy {
        Extractor.getCurrentExtractor(
            PreferenceManager(MyApp.context).getString(
                MOVIE_SOURCE
            )
        )!!
    }

    val currentEpisodeData = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    val currentQualityEpisode = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    var seriesResponse: VodMovieResponse? = null

    val allEpisodeData = MutableLiveData<Resource<EpisodeData>>(Resource.Idle)

    /*This also working always by tmdb id + season | ep */
    private suspend fun getAllSubtitleList(
        isMovie: Boolean,
        tmdbId: Int,
        season: Int,
        ep: Int,
    ): ArrayList<SubtitleItem> = withContext(Dispatchers.IO) {
        if (isMovie) playImdb.getSubtitleListForMovie(tmdbId)
        else playImdb.getSubTitleList(tmdbId, season, ep)
    }

    /* We don`t need change this because always this working i mean we don`t need extractor in this function )*/
    fun getAllEpisodeByImdb(
        imdbId: String,
        tmdbId: Int,
        season: Int,
        isMovie: Boolean,
        img: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            allEpisodeData.postValue(Resource.Loading)
            runCatching {
                val list = playImdb.getEpisodes(imdbId)

                if (!isMovie) {
                    val backdrops = playImdb.getDetails(season, tmdbId)
                    val episodes = list.filter { it.season == season }

                    val listData = ArrayList<Data>(episodes.size)
                    episodes.forEachIndexed { index, episode ->
                        val snapshot = backdrops.getOrNull(index)?.originalUrl ?: LocalData.anime404
                        listData.add(
                            episode.toDomain().copy(
                                episode2 = episode.episode,
                                episode = index + 1,
                                title = episode.title,
                                snapshot = snapshot,
                                season = episode.season
                            )
                        )
                    }
                    EpisodeData(1, listData, 1, 1, "", -1, null, -1, 1)
                } else {
                    val listData = ArrayList<Data>(list.size)
                    list.forEachIndexed { index, episode ->
                        listData.add(
                            episode.toDomain().copy(
                                episode2 = episode.episode,
                                episode = index + 1,
                                snapshot = img,
                                season = 1
                            )
                        )
                    }
                    EpisodeData(1, listData, 1, 1, "", -1, null, -1, 1)
                }
            }.onSuccess { data ->
                allEpisodeData.postValue(Resource.Success(data))
            }.onFailure { e ->
                allEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    /* Specially function is this ik my code is not clean but i try to make clean */
    fun getCurrentEpisodeVodByImdb(
        imdbId: String,
        episodeId: String,
        iframe: String,
        isMovie: Boolean,
        season: Int,
        episode: Int,
        tmdbId: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            currentEpisodeData.postValue(Resource.Loading)
            runCatching {
                isWatched = watchHistoryRepository.isWatched(iframe)
                if (isWatched) {
                    getWatchedHistoryEntity =
                        watchHistoryRepository.getWatchHistoryByVideoUrl(episodeId)
                    currentSelectedVideoOptionIndex =
                        getWatchedHistoryEntity?.currentQualityIndex ?: 0
                } else {
                    currentSelectedVideoOptionIndex = 0
                    getWatchedHistoryEntity = null
                }
                val movie = Video.Type.Movie(tmdbId.toString())
                val series = Video.Type.Episode(
                    tvShow = Video.Type.TvShow(tmdbId.toString()),
                    season = Video.Type.Season(season),
                    number = episode
                )

                try {
                    val server = if (isMovie) currentExtractor.server(movie) else currentExtractor.server(series)

                    val subtitles = getAllSubtitleList(isMovie, tmdbId, season, episode)
                    val vodSubs = subtitles.map { it.toDomain() }
                    currentExtractor.extract(server.src).let { video ->
                        VodMovieResponse(
                            authInfo = "",
                            subtitleList = vodSubs,
                            urlobj = video.source,
                            header = video.headers,
                            type = video.type

                        )
                    }

                }catch (e:Exception) {
                    throw Exception("Failed to extract video: ${e.message}")
                }

            }.onSuccess { data ->
                seriesResponse = data
                currentEpisodeData.postValue(Resource.Success(data))
                currentQualityEpisode.postValue(Resource.Success(data))
            }.onFailure { e ->
                currentEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    fun updateQualityByIndex() {
        val vod = seriesResponse ?: return
        currentQualityEpisode.postValue(Resource.Success(vod))
    }


    suspend fun addHistory(history: WatchHistoryEntity) {
        watchHistoryRepository.addHistory(history)
    }

    suspend fun removeHistory(videoUrl: String) {
        watchHistoryRepository.removeHistory(videoUrl)
    }

    suspend fun updateHistory(history: WatchHistoryEntity) {
        watchHistoryRepository.addHistory(history)
    }

    suspend fun getAllWatchHistory(): List<WatchHistoryEntity> {
        return watchHistoryRepository.getAllHistory()
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { watchHistoryRepository.clearAllHistory() }
        }
    }

    private fun asException(t: Throwable): Exception = (t as? Exception) ?: Exception(t)
}

package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.data.model.SubtitleItem
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.movie.PlayImdb
import com.saikou.sozo_tv.parser.sources.AnimeSources
import com.saikou.sozo_tv.parser.sources.SourceManager
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val SOURCE_HIANIME = "hianime"
    }

    val timeStamps = MutableLiveData<List<AniSkip.Stamp>?>()
    private val timeStampsMap: MutableMap<Int, List<AniSkip.Stamp>?> = mutableMapOf()

    var doNotAsk: Boolean = false
    var lastPosition: Long = 0
    var qualityProgress: Long = -1

    var seasons: Map<Int, Int> = emptyMap()
    var currentEpIndex: Int = -1
    var currentSubEpIndex: Int = 0

    val videoOptionsData = MutableLiveData<List<VideoOption>>()
    val videoOptions = ArrayList<VideoOption>()
    var currentSelectedVideoOptionIndex: Int = 0

    var isWatched: Boolean = false
    val isWatchedLiveData = MutableLiveData<Boolean>()
    var getWatchedHistoryEntity: WatchHistoryEntity? = null

    var parser = AnimeSources.getCurrent()
    val playImdb = PlayImdb()

    val currentEpisodeData = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    val currentQualityEpisode = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    var seriesResponse: VodMovieResponse? = null

    val allEpisodeData = MutableLiveData<Resource<EpisodeData>>(Resource.Idle)

    private var activeAnimeSourceKey: String? = null

    fun getAllEpisodeByPage(
        page: Int,
        mediaId: String,
        showResponse: ShowResponse,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            allEpisodeData.postValue(Resource.Loading)
            runCatching {
                parser.loadEpisodes(id = mediaId, page = page, showResponse)
            }.onSuccess { data ->
                if (data != null) {
                    allEpisodeData.postValue(Resource.Success(data))
                } else {
                    allEpisodeData.postValue(Resource.Error(IllegalStateException("No episodes returned")))
                }
            }.onFailure { e ->
                allEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    private suspend fun getAllSubtitleList(
        isMovie: Boolean,
        tmdbId: Int,
        season: Int,
        ep: Int,
    ): ArrayList<SubtitleItem> = withContext(Dispatchers.IO) {
        if (isMovie) playImdb.getSubtitleListForMovie(tmdbId)
        else playImdb.getSubTitleList(tmdbId, season, ep)
    }


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
                    val seasonCounts = list.groupingBy { it.season }.eachCount()
                    if (seasons.isEmpty()) seasons = seasonCounts

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

    fun loadTimeStamps(
        malId: Int?,
        episodeNum: Int?,
        duration: Long,
        useProxyForTimeStamps: Boolean,
    ) {
        if (malId == null || episodeNum == null) return

        if (timeStampsMap.containsKey(episodeNum)) {
            timeStamps.postValue(timeStampsMap[episodeNum])
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                AniSkip.getResult(malId, episodeNum, duration, useProxyForTimeStamps)
            }.onSuccess { result ->
                timeStampsMap[episodeNum] = result
                timeStamps.postValue(result)
            }.onFailure { e ->
                Log.w(TAG, "loadTimeStamps failed: ${e.message}", e)
                timeStampsMap[episodeNum] = null
                timeStamps.postValue(null)
            }
        }
    }

    fun loadWatched(session: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getWatchedHistoryEntity = watchHistoryRepository.getWatchHistoryById(session)
                watchHistoryRepository.isWatched(session)
            }.onSuccess { watched ->
                isWatchedLiveData.postValue(watched)
            }.onFailure {
                isWatchedLiveData.postValue(false)
            }
        }
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

    fun updateQualityByIndex() {
        if (videoOptions.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            currentQualityEpisode.postValue(Resource.Loading)
            runCatching {
                val sourceKey = activeAnimeSourceKey ?: SourceManager().getCurrentSourceKey()
                val idx = currentSelectedVideoOptionIndex.coerceIn(0, videoOptions.lastIndex)
                buildVodFromOption(videoOptions[idx], sourceKey)
            }.onSuccess { vod ->
                seriesResponse = vod
                currentEpisodeData.postValue(Resource.Success(vod))
                currentQualityEpisode.postValue(Resource.Success(vod))
            }.onFailure { e ->
                currentQualityEpisode.postValue(Resource.Error(asException(e)))
            }
        }
    }

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

                val m3u8Link = if (isMovie) playImdb.invokeVidSrcXyz(imdbId)
                else playImdb.invokeVidSrcXyz(imdbId, season, episode)

                val subtitles = getAllSubtitleList(isMovie, tmdbId, season, episode)
                val vodSubs = subtitles.map { it.toDomain() }

                VodMovieResponse(
                    authInfo = "",
                    subtitleList = vodSubs,
                    urlobj = m3u8Link,
                    header = mapOf(),
                )
            }.onSuccess { data ->
                seriesResponse = data
                currentEpisodeData.postValue(Resource.Success(data))
                currentQualityEpisode.postValue(Resource.Success(data))
            }.onFailure { e ->
                currentEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    fun getCurrentEpisodeVodAnime(
        episodeId: String,
        mediaId: String,
        isHistory: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            currentEpisodeData.postValue(Resource.Loading)
            runCatching {
                isWatched = watchHistoryRepository.isWatched(episodeId)
                if (isWatched) {
                    getWatchedHistoryEntity =
                        watchHistoryRepository.getWatchHistoryByVideoUrl(episodeId)
                    currentSelectedVideoOptionIndex =
                        getWatchedHistoryEntity?.currentQualityIndex ?: 0
                } else {
                    currentSelectedVideoOptionIndex = 0
                    getWatchedHistoryEntity = null
                }

                val sourceKey = if (!isHistory) SourceManager().getCurrentSourceKey()
                else getWatchedHistoryEntity?.source ?: SourceManager().getCurrentSourceKey()

                activeAnimeSourceKey = sourceKey
                parser = AnimeSources.getSourceById(sourceKey)

                val options = parser.getEpisodeVideo(epId = episodeId, id = mediaId)
                if (options.isEmpty()) throw IllegalStateException("No video options found")

                videoOptionsData.postValue(options)
                videoOptions.clear()
                videoOptions.addAll(options)

                currentSelectedVideoOptionIndex =
                    currentSelectedVideoOptionIndex.coerceIn(0, options.lastIndex)

                buildVodFromOption(options[currentSelectedVideoOptionIndex], sourceKey)
            }.onSuccess { vod ->
                seriesResponse = vod
                currentEpisodeData.postValue(Resource.Success(vod))
                currentQualityEpisode.postValue(Resource.Success(vod))
            }.onFailure { e ->
                currentEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    private suspend fun buildVodFromOption(
        option: VideoOption, sourceKey: String
    ): VodMovieResponse {
        return if (sourceKey == SOURCE_HIANIME) {
            VodMovieResponse(
                authInfo = "",
                subtitleList = option.tracks.map { SubTitle(it.file, it.label ?: "") },
                urlobj = option.videoUrl,
                header = option.headers
            )
        } else {
            val extractedUrl = parser.extractVideo(option.videoUrl)
            Log.d(TAG, "buildVodFromOption: $extractedUrl | ${option.videoUrl}")
            VodMovieResponse(
                authInfo = "",
                subtitleList = arrayListOf(),
                urlobj = extractedUrl,
                header = mapOf("User-Agent" to AnimePahe.USER_AGENT, "Referer" to option.videoUrl)
            )
        }
    }

    private fun asException(t: Throwable): Exception = (t as? Exception) ?: Exception(t)
}

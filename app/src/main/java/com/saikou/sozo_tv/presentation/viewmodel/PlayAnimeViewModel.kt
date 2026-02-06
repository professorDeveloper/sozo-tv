package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.parser.sources.AnimeSources
import com.saikou.sozo_tv.parser.sources.SourceManager
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayAnimeViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "PlayAnimeViewModel"
        private const val SOURCE_HIANIME = "hianime"
        private const val SOURCE_ANIMEWORLD = "animeworld"
    }

    val timeStamps = MutableLiveData<List<AniSkip.Stamp>?>()
    private val timeStampsMap: MutableMap<Int, List<AniSkip.Stamp>?> = mutableMapOf()
    var doNotAsk: Boolean = false
    var lastPosition: Long = 0

    var currentEpIndex: Int = -1
    var currentSubEpIndex: Int = 0

    val videoOptionsData = MutableLiveData<List<VideoOption>>()
    val videoOptions = ArrayList<VideoOption>()
    var currentSelectedVideoOptionIndex: Int = 0

    var isWatched: Boolean = false
    val isWatchedLiveData = MutableLiveData<Boolean>()
    var getWatchedHistoryEntity: WatchHistoryEntity? = null

    var parser = AnimeSources.getCurrent()
    private var activeAnimeSourceKey: String? = null

    val currentEpisodeData = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    val currentQualityEpisode = MutableLiveData<Resource<VodMovieResponse>>(Resource.Idle)
    var seriesResponse: VodMovieResponse? = null

    val allEpisodeData = MutableLiveData<Resource<EpisodeData>>(Resource.Idle)

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
                subtitleList = option.tracks.map {
                    if (!it.file.contains("thumbnail")) SubTitle(
                        it.file, it.label ?: ""
                    ) else null
                }.filterNotNull(),
                urlobj = option.videoUrl,
                header = option.headers,
                type = option.mimeTypes,
                thumbnail = option.tracks.find { it.file.contains("thumbnail") }?.file ?: ""
            )
        }else if (sourceKey == "AnimeSaturn") {
            VodMovieResponse(
                authInfo = "",
                subtitleList = arrayListOf(),
                urlobj = option.videoUrl,
                header = option.headers,
                type = MimeTypes.APPLICATION_MP4,
            )
        }
        else if (sourceKey == SOURCE_ANIMEWORLD) {
            val headers = linkedMapOf(
                "User-Agent" to "Mozilla/5.0",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9,uz-UZ;q=0.8,uz;q=0.7",
                "Connection" to "keep-alive",
                "Upgrade-Insecure-Requests" to "1"
            )

            VodMovieResponse(
                authInfo = "",
                subtitleList = arrayListOf(),
                urlobj = option.videoUrl,
                header = headers,
                type = MimeTypes.APPLICATION_MP4,
            )
        } else {
            val extractedUrl = parser.extractVideo(option.videoUrl)
            Log.d(TAG, "buildVodFromOption: $extractedUrl | ${option.videoUrl}")
            VodMovieResponse(
                authInfo = "", subtitleList = arrayListOf(), urlobj = extractedUrl, header = mapOf(
                    "User-Agent" to AnimePahe.USER_AGENT, "Referer" to option.videoUrl
                )
            )
        }
    }

    private fun asException(t: Throwable): Exception = (t as? Exception) ?: Exception(t)
}

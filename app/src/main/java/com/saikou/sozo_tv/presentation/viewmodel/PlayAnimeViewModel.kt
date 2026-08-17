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
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
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
    private val historySync: WatchHistorySyncRepository,
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
        // Tombstone first: once the Room row is gone there is nothing left to
        // describe the delete, and the next sync would download it straight back.
        watchHistoryRepository.getWatchHistoryById(videoUrl)?.let { historySync.rememberDeleted(it) }
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
            runCatching {
                historySync.rememberClearedAll()
                watchHistoryRepository.clearAllHistory()
                // Push immediately so the phone does not keep showing a list
                // the user just emptied here.
                historySync.sync()
            }
        }
    }

    /**
     * Pull other devices' progress and push this box's.
     *
     * Fire-and-forget on purpose: history is a convenience, and a screen must
     * never wait on the network to render the copy it already has.
     */
    fun syncHistory() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { historySync.sync() } }
    }

    /** Awaitable form, for a screen that wants to redraw once the pull lands. */
    suspend fun syncHistoryNow(): Boolean =
        runCatching { historySync.sync() }.getOrDefault(false)

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
        episodeNum: Int?
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

                val options = parser.getEpisodeVideo(
                    id = mediaId,
                    epId = episodeId,
                    epNum = episodeNum ?: -1
                )
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
        return when (sourceKey) {
            // Aniyomi/CloudStream: the VideoOption already carries the playable url,
            // headers, mime type and subtitle tracks (no extraction needed).
            "extension" -> {
                var url = option.videoUrl
                var headers = option.headers
                var mime = option.mimeTypes.ifEmpty { MimeTypes.APPLICATION_M3U8 }

                // Some sources resolve to an HTML page, not a stream — ExoPlayer can't parse a
                // page. Sniff the real .m3u8/.mp4 out of a headless WebView (the page's own JS
                // builds the signed url) and play THAT with the captured headers.
                //
                // Two ways in, and the ORDER matters. The server-set `useWebViewSniff` flag is
                // authoritative: it is how a backend declares "this source needs a browser"
                // without the app knowing which site it is, so adding another such provider
                // never ships an app update. `needsExtraction` stays only as the legacy
                // heuristic for sources that predate the flag (it sniffs the URL for
                // .html/.php/-style paths and cannot see an extensionless /embed/<token>).
                if (option.useWebViewSniff ||
                    com.saikou.sozo_tv.engine.player.WebViewStreamExtractor.needsExtraction(url)
                ) {
                    val sniffed = com.saikou.sozo_tv.engine.player.WebViewStreamExtractor.extract(
                        context = com.saikou.sozo_tv.app.MyApp.context,
                        pageUrl = url,
                        pageHeaders = option.headers,
                        timeoutMs = sniffTimeoutMs(option.sniff),
                    )
                    if (sniffed != null) {
                        url = sniffed.url
                        headers = option.headers + sniffed.headers
                        mime = if (sniffed.playType == "hls") MimeTypes.APPLICATION_M3U8
                        else MimeTypes.VIDEO_MP4
                    }
                }

                VodMovieResponse(
                    authInfo = "",
                    subtitleList = option.tracks.map { SubTitle(it.file, it.label ?: "") },
                    urlobj = url,
                    header = headers,
                    type = mime,
                    // VTT seek-preview sprite url (forwarded from the extension's resolveMedia).
                    thumbnail = option.thumbnail ?: "",
                    useLocalProxy = option.useLocalProxy,
                    localProxyJson = option.localProxy,
                    requestTransformJson = option.requestTransform,
                )
            }

            SOURCE_HIANIME -> {
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
            }

            "anime_lok" -> {
                Log.d(TAG, "buildVodFromOption:AnimeLok ${option.videoUrl}")
                Log.d(TAG, "buildVodFromOption:AnimeLok ${option.tracks.find { it.file.contains("jpg") }?.file ?: ""}")
                VodMovieResponse(
                    authInfo = "",
                    subtitleList = arrayListOf(),
                    urlobj = option.videoUrl,
                    header = option.headers,
                    type = MimeTypes.APPLICATION_M3U8,
                    thumbnail = option.tracks.find { it.file.contains("thumbnail") }?.file ?: "",
                    language = "hin"
                )
            }

            "AnimeSaturn" -> {
                VodMovieResponse(
                    authInfo = "",
                    subtitleList = arrayListOf(),
                    urlobj = option.videoUrl,
                    header = option.headers,
                    type = MimeTypes.APPLICATION_MP4,
                )
            }

            SOURCE_ANIMEWORLD -> {
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
            }

            else -> {
                val extractedUrl = parser.extractVideo(option.videoUrl)
                Log.d(TAG, "buildVodFromOption: $extractedUrl | ${option.videoUrl}")
                VodMovieResponse(
                    authInfo = "",
                    subtitleList = arrayListOf(),
                    urlobj = extractedUrl.source,
                    header = extractedUrl.headers,
                    type = extractedUrl.type,
                )
            }
        }
    }

    /**
     * `sniff.timeoutMs` from the server's directive, clamped.
     *
     * Clamped rather than trusted: this value decides how long playback sits on a
     * spinner, and a bad/hostile payload must not be able to hang the player. The
     * default matches WebViewStreamExtractor's own.
     */
    private fun sniffTimeoutMs(sniffJson: String?): Long {
        val fallback = 20_000L
        if (sniffJson.isNullOrBlank()) return fallback
        return runCatching {
            val v = org.json.JSONObject(sniffJson).optLong("timeoutMs", fallback)
            v.coerceIn(5_000L, 45_000L)
        }.getOrDefault(fallback)
    }

    private fun asException(t: Throwable): Exception = (t as? Exception) ?: Exception(t)
}

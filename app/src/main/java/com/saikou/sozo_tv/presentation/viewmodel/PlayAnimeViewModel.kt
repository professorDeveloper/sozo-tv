package com.saikou.sozo_tv.presentation.viewmodel

import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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

    private var episodeJob: Job? = null
    private var qualityJob: Job? = null
    private var stampsJob: Job? = null
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

    val episodeProgress = MutableLiveData<Map<String, Pair<Long, Long>>>(emptyMap())

    fun loadEpisodeProgress(sessions: List<String>) {
        if (sessions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val wanted = sessions.toHashSet()
                watchHistoryRepository.getAllHistory()
                    .filter { it.session in wanted && it.totalDuration > 0 && it.lastPosition > 0 }
                    .associate { it.session to (it.lastPosition to it.totalDuration) }
            }.onSuccess { episodeProgress.postValue(it) }
        }
    }

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

    fun stampsFor(episodeNum: Int?): List<AniSkip.Stamp>? {
        if (episodeNum == null) return null
        return timeStampsMap[episodeNum]
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

        stampsJob?.cancel()
        stampsJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun persistHistory(history: WatchHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            runCatching { watchHistoryRepository.addHistory(history) }
                .onFailure { Log.e(TAG, "persistHistory failed: ${it.message}", it) }
        }
    }

    suspend fun removeHistory(videoUrl: String) {
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
                historySync.sync()
            }
        }
    }

    fun syncHistory() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { historySync.sync() } }
    }

    suspend fun syncHistoryNow(): Boolean =
        runCatching { historySync.sync() }.getOrDefault(false)

    fun updateQualityByIndex() {
        if (videoOptions.isEmpty()) return

        qualityJob?.cancel()
        qualityJob = viewModelScope.launch(Dispatchers.IO) {
            currentQualityEpisode.postValue(Resource.Loading)
            runCatching {
                val sourceKey = activeAnimeSourceKey ?: SourceManager().getCurrentSourceKey()
                val idx = currentSelectedVideoOptionIndex.coerceIn(0, videoOptions.lastIndex)
                buildVodFromOption(videoOptions[idx], sourceKey)
            }.onSuccess { vod ->
                ensureActive()
                seriesResponse = vod
                currentQualityEpisode.postValue(Resource.Success(vod))
            }.onFailure { e ->
                ensureActive()
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
        episodeJob?.cancel()
        qualityJob?.cancel()
        episodeJob = viewModelScope.launch(Dispatchers.IO) {
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
                ensureActive()
                seriesResponse = vod
                currentEpisodeData.postValue(Resource.Success(vod))
            }.onFailure { e ->
                ensureActive()
                currentEpisodeData.postValue(Resource.Error(asException(e)))
            }
        }
    }

    private suspend fun buildVodFromOption(
        option: VideoOption, sourceKey: String
    ): VodMovieResponse {
        return when (sourceKey) {
            "extension" -> {
                var url = option.videoUrl
                var headers = option.headers
                var mime = option.mimeTypes.ifEmpty { MimeTypes.APPLICATION_M3U8 }

                if (option.useWebViewSniff ||
                    com.saikou.sozo_tv.engine.player.WebViewStreamExtractor.needsExtraction(url)
                ) {
                    val directive = parseSniff(option.sniff)
                    val sniffed = com.saikou.sozo_tv.engine.player.WebViewStreamExtractor.extract(
                        context = com.saikou.sozo_tv.app.MyApp.context,
                        pageUrl = url,
                        pageHeaders = option.headers + directive.headers,
                        timeoutMs = directive.timeoutMs,
                        patterns = directive.patterns,
                        blockHosts = directive.blockHosts,
                    )
                    if (sniffed != null) {
                        url = sniffed.url
                        headers = option.headers + sniffed.headers
                        url = applyRewrite(directive.rewrite, url, headers)
                        mime = if (sniffed.playType == "hls") MimeTypes.APPLICATION_M3U8
                        else MimeTypes.VIDEO_MP4
                    }
                }

                VodMovieResponse(
                    authInfo = "",
                    subtitleList = option.tracks.map { SubTitle(it.file, it.label ?: "", headers = it.headers) },
                    urlobj = url,
                    header = headers,
                    type = mime,
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
                            it.file, it.label ?: "", headers = it.headers
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
                    "User-Agent" to SOZO_USER_AGENT,
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

    private data class SniffDirective(
        val timeoutMs: Long,
        val patterns: List<String>,
        val blockHosts: List<String>,
        val headers: Map<String, String>,
        val rewrite: UrlRewrite? = null,
    )

    private data class UrlRewrite(
        val pattern: String,
        val replace: String,
        val verify: Boolean,
    )

    private fun parseSniff(sniffJson: String?): SniffDirective {
        val fallback = SniffDirective(20_000L, emptyList(), emptyList(), emptyMap())
        if (sniffJson.isNullOrBlank()) return fallback
        return runCatching {
            val o = org.json.JSONObject(sniffJson)
            SniffDirective(
                timeoutMs = o.optLong("timeoutMs", fallback.timeoutMs).coerceIn(5_000L, 45_000L),
                patterns = o.optJSONArray("patterns").strings(),
                blockHosts = o.optJSONArray("blockHosts").strings(),
                headers = o.optJSONObject("headers")?.let { h ->
                    h.keys().asSequence().mapNotNull { k ->
                        h.optString(k).takeIf { v -> v.isNotEmpty() }?.let { v -> k to v }
                    }.toMap()
                } ?: emptyMap(),
                rewrite = o.optJSONObject("rewrite")?.let { r ->
                    val pattern = r.optString("pattern")
                    val replace = r.optString("replace")
                    if (pattern.isEmpty()) null
                    else UrlRewrite(pattern, replace, r.optBoolean("verify", true))
                },
            )
        }.getOrDefault(fallback)
    }

    private suspend fun applyRewrite(
        rule: UrlRewrite?,
        url: String,
        headers: Map<String, String>,
    ): String = withContext(Dispatchers.IO) {
        if (rule == null) return@withContext url
        val candidate = runCatching {
            val re = Regex(rule.pattern)
            if (!re.containsMatchIn(url)) null else re.replaceFirst(url, rule.replace)
        }.getOrNull()
        if (candidate.isNullOrEmpty() || candidate == url) return@withContext url
        if (!rule.verify) return@withContext candidate

        runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val b = okhttp3.Request.Builder().url(candidate)
            headers.forEach { (k, v) -> b.header(k, v) }
            client.newCall(b.build()).execute().use { res ->
                val head = res.body?.source()?.peek()?.readUtf8Line().orEmpty()
                if (res.isSuccessful && head.trimStart().startsWith("#EXTM3U")) candidate else url
            }
        }.getOrDefault(url)
    }

    private fun org.json.JSONArray?.strings(): List<String> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotEmpty() }
        }
    }

    private fun asException(t: Throwable): Exception = (t as? Exception) ?: Exception(t)
}

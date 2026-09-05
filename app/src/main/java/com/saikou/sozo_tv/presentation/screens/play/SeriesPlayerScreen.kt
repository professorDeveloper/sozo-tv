package com.saikou.sozo_tv.presentation.screens.play

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.TimeBar
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodePlayerAdapter
import com.saikou.sozo_tv.adapters.SettingRow
import com.saikou.sozo_tv.adapters.VideoServersAdapter
import com.saikou.sozo_tv.components.SkipIntroView
import com.saikou.sozo_tv.components.UpNextOverlayView
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.data.remote.remote.RemoteCommand
import com.saikou.sozo_tv.data.repository.AnilistTracker
import com.saikou.sozo_tv.data.repository.MalTracker
import com.saikou.sozo_tv.data.repository.RemoteControlManager
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.domain.player.AutoAdvance
import com.saikou.sozo_tv.domain.player.NativeQualities
import com.saikou.sozo_tv.domain.player.NativeTracks
import com.saikou.sozo_tv.domain.player.PlayerErrorPolicy
import com.saikou.sozo_tv.domain.player.SeasonNumber
import com.saikou.sozo_tv.domain.player.SubtitleChoice
import com.saikou.sozo_tv.domain.player.VideoOptionGroups
import com.saikou.sozo_tv.engine.player.LocalHlsProxy
import com.saikou.sozo_tv.engine.player.StreamHttp
import com.saikou.sozo_tv.engine.player.SubtitleSideloader
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.screens.play.dialog.PlayerSettingsPopup
import com.saikou.sozo_tv.presentation.screens.play.dialog.SubtitleChooserDialog
import com.saikou.sozo_tv.presentation.viewmodel.PlayAnimeViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.VttSpriteThumbnailLoader
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

@OptIn(UnstableApi::class)
class SeriesPlayerScreen : Fragment() {

    private val remote: RemoteControlManager by inject()
    private val extensionEngine: ExtensionEngine by inject()
    private val anilistTracker: AnilistTracker by inject()
    private val malTracker: MalTracker by inject()
    private val model by viewModel<PlayAnimeViewModel>()
    private val args by navArgs<SeriesPlayerScreenArgs>()

    private var _binding: SeriesPlayerScreenBinding? = null
    private val binding get() = _binding!!
    private var _controls: ContentControllerTvSeriesBinding? = null
    private val controls get() = _controls!!

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var mediaSession: MediaSession? = null
    private lateinit var http: StreamHttp
    private lateinit var sideloader: SubtitleSideloader
    private val prefs by lazy { PreferenceManager(requireContext()) }
    private val hlsProxy by lazy { LocalHlsProxy(http.proxyClient) }

    private val episodeList = arrayListOf<Data>()
    private var episodeAdapter: EpisodePlayerAdapter? = null
    private var currentVod: VodMovieResponse? = null
    private var initialOpen = true
    private var switchingEpisode = false
    private var loadJob: Job? = null
    private var handledEpisode: Resource<VodMovieResponse>? = null
    private var handledQuality: Resource<VodMovieResponse>? = null
    private var episodeRequested = false
    private var pendingResume: WatchHistoryEntity? = null
    private var openDialogs = 0
    private val dialogOpen get() = openDialogs > 0
    private var lastFocusedControl: View? = null
    private var thumbnailsAvailable = false
    private var loadingJob: Job? = null
    private var toastJob: Job? = null
    private var bufferingRunnable: Runnable? = null
    private var bufferingWanted = false

    private var selectedNativeQuality: NativeQualities.Variant? = null
    private var selectedAudio: NativeTracks.Option? = null
    private var selectedText: NativeTracks.Option? = null
    private var subtitlesEnabled = true
    private var extractorSubtitles: List<SubTitle> = emptyList()
    private var onlineSubtitles: List<SubTitle> = emptyList()
    private var extractorSubtitleLabel: String? = null
    private var subtitleOffsetMs = 0L
    private var subtitleOffsetJob: Job? = null
    private var subtitleFailure: String? = null

    private var playbackSpeed = 1.0f
    private var currentResizeIdx = 0
    private var autoSwitches = 0
    private var errorRetry: (() -> Unit)? = null

    private var upNextShown = false
    private var upNextCancelled = false
    private val trackersReported = HashSet<Int>()
    private val tickHandler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    private val skipHandler = Handler(Looper.getMainLooper())
    private var skipIntroView: SkipIntroView? = null
    private var skipIntroBound = false

    private var thumbListenerAttached = false
    private var thumbLoader: VttSpriteThumbnailLoader? = null
    private var thumbLoadJob: Job? = null
    private var thumbFetchJob: Job? = null
    private var currentVttUrl = ""

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SeriesPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        http = StreamHttp(requireContext())
        sideloader = SubtitleSideloader(requireContext().cacheDir) { http.subtitleClient }
        _controls = ContentControllerTvSeriesBinding.bind(
            binding.pvPlayer.controller.findViewById(R.id.cl_exo_controller_tv)
        )
        binding.pvPlayer.controllerAutoShow = false
        binding.pvPlayer.controller.addVisibilityListener { visibility ->
            val shown = visibility == View.VISIBLE
            binding.pvPlayer.subtitleView?.setBottomPaddingFraction(
                if (shown) SUBTITLE_PADDING_CONTROLS else SUBTITLE_PADDING_DEFAULT
            )
            if (!shown) showHint(null)
        }

        restorePrefs()
        wireControls()
        wireHints()
        wireErrorOverlay()
        installBackHandler()
        parentFragmentManager.registerFragmentLifecycleCallbacks(dialogWatcher, false)
        bindObservers()
        observeRemote()

        if (model.currentEpIndex < 0) model.currentEpIndex = args.currentIndex
        controls.filmTitle.text = args.name
        updateEpisodeMeta()

        if (model.allEpisodeData.value !is Resource.Success) {
            showLoading(getString(R.string.part_are_episodes_loading, args.currentPage))
            model.getAllEpisodeByPage(
                args.currentPage, args.seriesMainId, ShowResponse(args.name, args.id, args.image)
            )
        }
    }

    private fun restorePrefs() {
        playbackSpeed = prefs.getPlaybackSpeed().takeIf { it in SPEEDS } ?: 1.0f
        currentResizeIdx = prefs.getResizeModeIndex().coerceIn(0, resizeModes.lastIndex)
        binding.pvPlayer.resizeMode = resizeModes[currentResizeIdx]
        subtitlesEnabled = prefs.isSubtitlesEnabled()
        extractorSubtitleLabel = prefs.getSubtitleLabel()
    }

    private fun wireControls() {
        controls.frameBackButton.setOnClickListener { navigateBack() }
        controls.exoPlayPauseContainer.setOnClickListener { togglePlayPause() }
        controls.exoNextTenContainer.setOnClickListener { seekBy(10_000) }
        controls.exoPrevTenContainer.setOnClickListener { seekBy(-10_000) }
        controls.exoNextContainer.setOnClickListener { openNextEpisode() }
        controls.exoPrevContainer.setOnClickListener { openPreviousEpisode() }
        controls.epListContainer.setOnClickListener {
            model.loadEpisodeProgress(episodeList.mapNotNull { ep -> ep.session })
            toggleSidebarRight(true)
        }
        binding.btnHideMenuRight.setOnClickListener { toggleSidebarRight(false) }
        binding.loadingSwitchSource.setOnClickListener { showServerDialog() }
        controls.exoSettings.setOnClickListener { showSettingsMenu() }
        controls.exoSubtidtle.setOnClickListener { onSubtitleButton() }
        controls.exoAudio.setOnClickListener { showAudioDialog() }
        controls.exoServer.setOnClickListener { showServerDialog() }
        controls.exoQuality.setOnClickListener { showQualityDialog() }
        updateSubtitleIcon(subtitlesEnabled)
        updatePlayPauseIcon(false)

        binding.pvPlayer.onNextEpisode = { openNextEpisode() }
        binding.pvPlayer.onPreviousEpisode = { openPreviousEpisode() }
        timeBar()?.setKeyTimeIncrement(10_000)
    }

    private fun wireHints() {
        val hints = listOf(
            controls.frameBackButton to R.string.player_hint_back,
            controls.epListContainer to R.string.player_hint_episodes,
            controls.exoPrevContainer to R.string.player_hint_prev_episode,
            controls.exoPrevTenContainer to R.string.player_hint_rewind,
            controls.exoNextTenContainer to R.string.player_hint_forward,
            controls.exoNextContainer to R.string.player_hint_next_episode,
            controls.exoServer to R.string.player_server_title,
            controls.exoQuality to R.string.player_quality_title,
            controls.exoSubtidtle to R.string.player_hint_subtitles,
            controls.exoAudio to R.string.player_hint_audio,
            controls.exoSettings to R.string.player_hint_settings,
        )
        hints.forEach { (view, res) ->
            view.setOnFocusChangeListener { _, has ->
                if (has) lastFocusedControl = view
                showHint(if (has) res else null)
            }
        }
        timeBar()?.setOnFocusChangeListener { _, has ->
            showHint(if (has) R.string.player_hint_seek else null)
        }
        controls.exoPlayPauseContainer.setOnFocusChangeListener { _, has ->
            if (has) {
                lastFocusedControl = controls.exoPlayPauseContainer
                showPlayPauseHint()
            } else {
                showHint(null)
            }
        }
    }

    private fun showHint(@StringRes res: Int?) {
        val c = _controls ?: return
        if (res == null) {
            c.controlHint.isVisible = false
            return
        }
        c.controlHint.setText(res)
        c.controlHint.isVisible = true
    }

    private fun showPlayPauseHint() {
        showHint(
            if (player?.isPlaying == true) R.string.player_hint_pause else R.string.player_hint_play
        )
    }

    private fun wireErrorOverlay() {
        val e = binding.playerError
        e.errorRetry.setOnClickListener {
            val retry = errorRetry
            hideError()
            retry?.invoke()
        }
        e.errorSwitchSource.setOnClickListener {
            hideError()
            val servers = VideoOptionGroups.servers(model.videoOptionsData.value.orEmpty())
            if (servers.size > 1) showServerDialog() else showQualityDialog()
        }
        e.errorBack.setOnClickListener { navigateBack() }
    }

    private fun installBackHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.sidebarRight.isVisible -> toggleSidebarRight(false)
                        binding.upNextOverlay.isShowing -> {
                            binding.upNextOverlay.dismiss()
                            upNextCancelled = true
                        }

                        else -> navigateBack()
                    }
                }
            })
    }

    private val dialogWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) openDialogs++
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            if (f !is DialogFragment) return
            openDialogs = (openDialogs - 1).coerceAtLeast(0)
            if (openDialogs == 0) restoreControlFocus()
        }
    }

    private fun restoreControlFocus() {
        val b = _binding ?: return
        if (!isAdded) return
        if (b.loadingLayout.isVisible) {
            if (b.loadingSwitchSource.isVisible) b.loadingSwitchSource.requestFocus()
            return
        }
        if (b.playerError.root.isVisible) {
            b.playerError.errorRetry.requestFocus()
            return
        }
        if (b.sidebarRight.isVisible) {
            b.episodeRv.requestFocus()
            return
        }
        b.pvPlayer.showController()
        b.pvPlayer.post {
            if (_binding == null) return@post
            val target = lastFocusedControl?.takeIf { it.isShown }
                ?: controls.exoPlayPauseContainer
            target.requestFocus()
        }
    }

    private fun bindObservers() {
        model.allEpisodeData.observe(viewLifecycleOwner) { res ->
            when (res) {
                Resource.Loading ->
                    showLoading(getString(R.string.part_are_episodes_loading, args.currentPage))

                is Resource.Success -> {
                    episodeList.clear()
                    episodeList.addAll(res.data.data ?: listOf())
                    binding.textView9.text = resources.getQuantityString(
                        R.plurals.part_episode_count,
                        episodeList.size,
                        args.currentPage,
                        episodeList.size,
                    )
                    episodeAdapter = EpisodePlayerAdapter(model.currentEpIndex, args.image).also {
                        it.submitList(episodeList)
                        it.setOnEpisodeClick { position, _ ->
                            toggleSidebarRight(false)
                            openEpisode(position)
                        }
                    }
                    binding.episodeRv.adapter = episodeAdapter
                    updateEpisodeMeta()
                    model.loadEpisodeProgress(episodeList.mapNotNull { it.session })
                    if (!episodeRequested) {
                        episodeRequested = true
                        model.loadWatched(args.id)
                    }
                }

                is Resource.Error -> showError(
                    getString(R.string.player_error_load_episode),
                    canSwitch = false,
                ) {
                    model.getAllEpisodeByPage(
                        args.currentPage, args.seriesMainId,
                        ShowResponse(args.name, args.id, args.image),
                    )
                }

                else -> Unit
            }
        }

        model.isWatchedLiveData.observe(viewLifecycleOwner) { history ->
            if (model.currentEpisodeData.value is Resource.Success) return@observe
            model.getCurrentEpisodeVodAnime(
                args.id, args.seriesMainId, history,
                episodeNum = episodeList.getOrNull(model.currentEpIndex)?.episode ?: 0,
            )
        }

        model.videoOptionsData.observe(viewLifecycleOwner) { options ->
            val list = options.orEmpty()
            controls.exoServer.isVisible = VideoOptionGroups.servers(list).size > 1
            controls.exoQuality.isVisible = list.isNotEmpty()
        }

        model.timeStamps.observe(viewLifecycleOwner) { stamps ->
            if (_controls == null) return@observe
            val starts = stamps
                ?.map { (it.interval.startTime * 1000).toLong() }
                ?.filter { it > 0 }
                ?.toLongArray()
            if (starts == null || starts.isEmpty()) {
                binding.pvPlayer.controller.setExtraAdGroupMarkers(null, null)
            } else {
                binding.pvPlayer.controller.setExtraAdGroupMarkers(
                    starts, BooleanArray(starts.size)
                )
            }
        }

        model.episodeProgress.observe(viewLifecycleOwner) { progress ->
            episodeAdapter?.setProgress(progress)
        }

        model.currentEpisodeData.observe(viewLifecycleOwner) { res ->
            when (res) {
                Resource.Loading -> {
                    hideError()
                    showLoading(getString(R.string.episode_is_loading, currentEpisodeNumber()))
                }

                is Resource.Success -> {
                    if (res === handledEpisode) return@observe
                    handledEpisode = res
                    startPlayback(res.data)
                }

                is Resource.Error -> {
                    hideLoading()
                    val servers = VideoOptionGroups.servers(model.videoOptionsData.value.orEmpty())
                    showError(
                        getString(R.string.player_error_load_episode),
                        canSwitch = servers.size > 1,
                        titleRes = R.string.player_error_title_load,
                    ) { requestEpisode(model.currentEpIndex) }
                }

                else -> Unit
            }
        }

        model.currentQualityEpisode.observe(viewLifecycleOwner) { res ->
            when (res) {
                Resource.Loading -> showToast(switchingLabel())

                is Resource.Success -> {
                    if (res === handledQuality) return@observe
                    handledQuality = res
                    playQualityVideo(res.data)
                }

                is Resource.Error -> {
                    hideToast()
                    val servers = VideoOptionGroups.servers(model.videoOptionsData.value.orEmpty())
                    showError(
                        getString(R.string.quality_switch_failed),
                        canSwitch = servers.size > 1,
                    ) { restartCurrent() }
                }

                else -> Unit
            }
        }
    }

    private fun switchingLabel(): String {
        val options = model.videoOptionsData.value.orEmpty()
        val option = options.getOrNull(model.currentSelectedVideoOptionIndex)
        val label = option?.let { opt ->
            VideoOptionGroups.resolutionOf(opt)?.let { "${it}p" } ?: opt.fansub.trim()
        }.orEmpty()
        return if (label.isBlank()) getString(R.string.quality_is_loading)
        else getString(R.string.player_switching_quality, label)
    }

    private fun showLoading(text: String) {
        val b = _binding ?: return
        loadingJob?.cancel()
        b.loadingLayout.animate().cancel()
        b.pvPlayer.hideController()
        b.loadingPoster.loadImage(args.image)
        b.loadingText.text = text
        b.loadingSwitchSource.isVisible = false
        b.loadingLayout.alpha = 1f
        b.loadingLayout.visible()
        loadingJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LOADING_SLOW_MS)
            val v = _binding ?: return@launch
            v.loadingText.setText(R.string.player_loading_finding)
            delay(LOADING_STUCK_MS - LOADING_SLOW_MS)
            val w = _binding ?: return@launch
            w.loadingText.setText(R.string.player_loading_slow)
            if (VideoOptionGroups.servers(model.videoOptionsData.value.orEmpty()).size > 1) {
                w.loadingSwitchSource.isVisible = true
                w.loadingSwitchSource.requestFocus()
            }
        }
    }

    private fun hideLoading() {
        val b = _binding ?: return
        loadingJob?.cancel()
        loadingJob = null
        if (!b.loadingLayout.isVisible) return
        b.loadingLayout.animate().alpha(0f).setDuration(200)
            .withEndAction {
                val v = _binding ?: return@withEndAction
                v.loadingLayout.gone()
                v.loadingLayout.alpha = 1f
            }.start()
    }

    private fun showToast(text: String) {
        val b = _binding ?: return
        toastJob?.cancel()
        b.playerToast.text = text
        b.playerToast.alpha = 0f
        b.playerToast.isVisible = true
        b.playerToast.animate().alpha(1f).setDuration(150).start()
        toastJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(TOAST_MS)
            hideToast()
        }
    }

    private fun hideToast() {
        val b = _binding ?: return
        toastJob?.cancel()
        toastJob = null
        if (!b.playerToast.isVisible) return
        b.playerToast.animate().alpha(0f).setDuration(150)
            .withEndAction { _binding?.playerToast?.isVisible = false }.start()
    }

    private fun currentEpisodeNumber(): Int =
        episodeList.getOrNull(model.currentEpIndex)?.episode?.takeIf { it > 0 }
            ?: (model.currentEpIndex + 1)

    private fun requestEpisode(index: Int) {
        val ep = episodeList.getOrNull(index) ?: return
        model.getCurrentEpisodeVodAnime(
            ep.session.toString(), args.seriesMainId, episodeNum = ep.episode ?: 0
        )
    }

    private fun openEpisode(index: Int) {
        if (index == model.currentEpIndex) return
        if (index !in episodeList.indices) {
            showToast(
                getString(
                    if (index < 0) R.string.this_is_the_first_episode
                    else R.string.this_is_the_last_episode
                )
            )
            return
        }
        persistProgress()
        model.loadEpisodeProgress(episodeList.mapNotNull { it.session })
        switchingEpisode = true
        loadJob?.cancel()
        stopProgressTracking()
        binding.upNextOverlay.dismiss()
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        showBuffering(false)
        model.currentEpIndex = index
        episodeAdapter?.selectEpisode(index)
        updateEpisodeMeta()
        requestEpisode(index)
    }

    private fun openNextEpisode() = openEpisode(model.currentEpIndex + 1)

    private fun openPreviousEpisode() = openEpisode(model.currentEpIndex - 1)

    private fun startPlayback(vod: VodMovieResponse) {
        val p = ensurePlayer()
        currentVod = vod
        http.headers = vod.header

        hideError()
        binding.pvPlayer.visible()
        binding.upNextOverlay.dismiss()
        upNextShown = false
        upNextCancelled = false
        switchingEpisode = false
        stopProgressTracking()
        skipIntroView?.resetSkippedTimestamps()
        skipIntroBound = false
        selectedNativeQuality = null
        selectedAudio = null
        selectedText = null
        autoSwitches = 0
        onlineSubtitles = emptyList()

        adoptSubtitleList(vod.subtitleList.orEmpty())
        setupOrUpdatePreviewThumbnails(vod.thumbnail, vod.header)
        applySubtitleStyle()
        controls.filmTitle.text = args.name
        updateEpisodeMeta()
        episodeAdapter?.selectEpisode(model.currentEpIndex)

        val history = model.getWatchedHistoryEntity
        val saved = history?.lastPosition ?: 0L
        val askResume = initialOpen && model.isWatched && saved > 0
        val startAt = if (initialOpen) saved.coerceAtLeast(0L)
        else AutoAdvance.resumePosition(saved, history?.totalDuration ?: 0L)
        initialOpen = false

        p.stop()
        p.clearMediaItems()
        loadSource(vod, startAt, autoPlay = !askResume) {
            if (askResume && history != null) offerResume(history)
        }
        controls.exoPlayPauseContainer.requestFocus()
    }

    private fun playQualityVideo(vod: VodMovieResponse) {
        val p = ensurePlayer()
        currentVod = vod
        http.headers = vod.header
        val resume = p.currentPosition.coerceAtLeast(0L)
        stopProgressTracking()
        adoptSubtitleList(vod.subtitleList.orEmpty())
        selectedNativeQuality = null
        selectedAudio = null
        selectedText = null
        p.stop()
        p.clearMediaItems()
        loadSource(vod, resume, autoPlay = true)
    }

    private fun restartCurrent() {
        val vod = currentVod ?: return requestEpisode(model.currentEpIndex)
        val p = ensurePlayer()
        val resume = p.currentPosition.coerceAtLeast(0L)
        p.stop()
        p.clearMediaItems()
        loadSource(vod, resume, autoPlay = true)
    }

    private fun loadSource(
        vod: VodMovieResponse,
        startAt: Long,
        autoPlay: Boolean,
        then: () -> Unit = {},
    ) {
        val url = effectiveStreamUrl(vod)
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val source = withContext(Dispatchers.IO) { buildPlaybackSource(url, vod.type) }
            val p = player ?: return@launch
            if (_binding == null) return@launch
            p.setMediaSource(source)
            applyNativeQuality()
            setTextRendererEnabled(subtitlesEnabled)
            selectedText?.let { applyTrack(C.TRACK_TYPE_TEXT, it) }
            if (playbackSpeed != 1.0f) p.setPlaybackSpeed(playbackSpeed)
            p.prepare()
            if (startAt > 0) p.seekTo(startAt)
            p.playWhenReady = autoPlay
            reportSubtitleFailure()
            then()
        }
    }

    private fun offerResume(history: WatchHistoryEntity) {
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            pendingResume = history
            return
        }
        pendingResume = null
        val dialog = AlertPlayerDialog(history)
        dialog.setNoClearListener {
            dialog.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                model.removeHistory(args.id)
                player?.seekTo(0)
                player?.play()
            }
        }
        dialog.setYesContinueListener {
            dialog.dismiss()
            player?.play()
        }
        dialog.show(parentFragmentManager, "ConfirmationDialog")
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val ctx = requireContext()
        val renderers = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            .setEnableAudioFloatOutput(false)
        val selector = DefaultTrackSelector(ctx).apply {
            setParameters(
                buildUponParameters().setPreferredAudioLanguages(*preferredAudioLanguages())
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000)
            .build()
        val p = ExoPlayer.Builder(ctx, renderers)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .build()
        p.setWakeMode(C.WAKE_MODE_NETWORK)
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )
        p.addListener(playerListener)
        mediaSession = MediaSession.Builder(ctx, p).build()
        binding.pvPlayer.player = p
        trackSelector = selector
        player = p
        return p
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            if (_binding == null) return
            val audio = NativeTracks.audio(tracks)
            controls.exoAudio.isVisible = audio.size > 1
            if (selectedAudio != null && audio.none { it.label == selectedAudio?.label }) {
                selectedAudio = null
            }
            if (selectedAudio == null) {
                val want = prefs.getAudioLanguage()
                if (!want.isNullOrBlank()) {
                    audio.firstOrNull { it.language.equals(want, ignoreCase = true) }?.let {
                        selectedAudio = it
                        applyTrack(C.TRACK_TYPE_AUDIO, it)
                    }
                }
            }
            if (subtitlesEnabled && selectedText == null) {
                NativeTracks.text(tracks)
                    .firstOrNull { it.id == SubtitleSideloader.TRACK_ID && !isPlaying(it) }
                    ?.let { applyTrack(C.TRACK_TYPE_TEXT, it) }
            }
            updateEpisodeMeta()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (_binding != null) updateEpisodeMeta()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val b = _binding ?: return
            updatePlayPauseIcon(isPlaying)
            if (controls.exoPlayPauseContainer.isFocused) showPlayPauseHint()
            b.pvPlayer.controllerShowTimeoutMs = if (isPlaying) CONTROLS_TIMEOUT_MS else 0
            if (isPlaying) {
                startProgressTracking()
            } else {
                stopProgressTracking()
                player?.let { updateRemaining(it.currentPosition, it.duration) }
                val busy = dialogOpen || b.upNextOverlay.isShowing ||
                    b.playerError.root.isVisible || b.sidebarRight.isVisible ||
                    b.loadingLayout.isVisible
                if (!busy && player?.playWhenReady == false) b.pvPlayer.showController()
            }
        }

        override fun onRenderedFirstFrame() {
            hideLoading()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            player?.let { updateRemaining(it.currentPosition, it.duration) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PLAYER_ERR", "code=${error.errorCodeName}", error)
            showBuffering(false)
            Bugsnag.notify(
                Exception(
                    "Playback ${error.errorCodeName}: ${currentVod?.urlobj} | " +
                        "${model.parser.name} | ${error.message}"
                )
            )
            handlePlaybackError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_binding == null) return
            when (playbackState) {
                Player.STATE_READY -> {
                    showBuffering(false)
                    hideLoading()
                    bindSkipIntroOnce()
                    player?.let { updateRemaining(it.currentPosition, it.duration) }
                }

                Player.STATE_BUFFERING -> showBuffering(true)

                Player.STATE_ENDED -> {
                    showBuffering(false)
                    onPlaybackEnded()
                }

                else -> Unit
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        _controls?.exoPlayPaused?.setImageResource(
            if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
        )
    }

    private fun onPlaybackEnded() {
        stopProgressTracking()
        if (switchingEpisode) return
        val p = player ?: return
        if (p.duration <= 0) return
        if (binding.upNextOverlay.isShowing) return
        when {
            model.currentEpIndex >= episodeList.size - 1 -> showEndCard()
            !upNextCancelled -> openNextEpisode()
            else -> binding.pvPlayer.showController()
        }
    }

    private fun bindSkipIntroOnce() {
        if (skipIntroBound) return
        val p = player ?: return
        skipIntroBound = true
        skipIntroView?.detach()
        skipIntroView = SkipIntroView(
            binding.pvPlayer, p, model, skipHandler, args.idMal,
            episodeList.getOrNull(model.currentEpIndex)?.episode ?: 0, p.duration / 1000,
        ).also { view ->
            runCatching { view.initialize() }.onFailure {
                Log.w("SeriesPlayerScreen", "SkipIntro init failed: ${it.message}")
            }
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val options = model.videoOptionsData.value.orEmpty()
        when (val decision = PlayerErrorPolicy.decide(
            error.errorCode, model.currentSelectedVideoOptionIndex, options.size, autoSwitches,
        )) {
            is PlayerErrorPolicy.Decision.SwitchSource -> {
                autoSwitches++
                model.currentSelectedVideoOptionIndex = decision.toIndex
                showToast(getString(R.string.player_switching_source))
                model.updateQualityByIndex()
            }

            is PlayerErrorPolicy.Decision.Explain -> {
                val message = when (decision.kind) {
                    PlayerErrorPolicy.Kind.CODEC -> R.string.player_error_codec
                    PlayerErrorPolicy.Kind.SOURCE -> R.string.player_error_source
                    PlayerErrorPolicy.Kind.NETWORK -> R.string.player_error_network
                    PlayerErrorPolicy.Kind.GENERIC -> R.string.player_error_generic
                }
                showError(getString(message), decision.canSwitch) { restartCurrent() }
            }
        }
    }

    private fun showEndCard() {
        val b = _binding ?: return
        errorRetry = {
            player?.seekTo(0)
            player?.play()
        }
        b.pvPlayer.hideController()
        b.upNextOverlay.dismiss()
        val e = b.playerError
        e.errorTitle.setText(R.string.player_end_title)
        e.errorMessage.setText(R.string.player_end_message)
        e.errorRetry.setText(R.string.player_end_replay)
        e.errorSwitchSource.isVisible = false
        e.errorRetry.nextFocusRightId = R.id.error_back
        e.errorBack.nextFocusLeftId = R.id.error_retry
        e.root.isVisible = true
        e.errorRetry.post { e.errorRetry.requestFocus() }
    }

    private fun showError(
        message: String,
        canSwitch: Boolean,
        titleRes: Int = R.string.player_error_title,
        retry: () -> Unit,
    ) {
        val b = _binding ?: return
        errorRetry = retry
        hideLoading()
        b.pvPlayer.hideController()
        b.upNextOverlay.dismiss()
        val e = b.playerError
        e.errorRetry.setText(R.string.player_error_retry)
        e.errorTitle.setText(titleRes)
        e.errorMessage.text = message
        e.errorSwitchSource.isVisible = canSwitch
        e.errorRetry.nextFocusRightId =
            if (canSwitch) R.id.error_switch_source else R.id.error_back
        e.errorBack.nextFocusLeftId =
            if (canSwitch) R.id.error_switch_source else R.id.error_retry
        e.root.isVisible = true
        e.errorRetry.post { e.errorRetry.requestFocus() }
    }

    private fun hideError() {
        val b = _binding ?: return
        if (!b.playerError.root.isVisible) return
        b.playerError.root.isVisible = false
        errorRetry = null
    }

    private fun showBuffering(show: Boolean) {
        val b = _binding ?: return
        bufferingRunnable?.let { tickHandler.removeCallbacks(it) }
        bufferingRunnable = null
        if (!show) {
            if (!bufferingWanted) return
            bufferingWanted = false
            b.bufferingIndicator.animate().cancel()
            b.bufferingIndicator.animate().alpha(0f).setDuration(120)
                .withEndAction { _binding?.bufferingIndicator?.isVisible = false }.start()
            return
        }
        if (bufferingWanted || b.loadingLayout.isVisible) return
        bufferingWanted = true
        val runnable = Runnable {
            val v = _binding ?: return@Runnable
            if (v.loadingLayout.isVisible || !bufferingWanted) return@Runnable
            v.bufferingIndicator.animate().cancel()
            v.bufferingIndicator.alpha = 0f
            v.bufferingIndicator.isVisible = true
            v.bufferingIndicator.animate().alpha(1f).setDuration(150).start()
        }
        bufferingRunnable = runnable
        tickHandler.postDelayed(runnable, BUFFERING_DELAY_MS)
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        var target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        if (duration > 0) target = target.coerceAtMost(duration)
        p.seekTo(target)
    }

    private fun timeBar(): TrailerPlayerScreen.ExtendedTimeBar? =
        binding.pvPlayer.controller.findViewById(androidx.media3.ui.R.id.exo_progress)

    private fun updateEpisodeMeta() {
        val c = _controls ?: return
        val number = currentEpisodeNumber()
        val height = player?.videoFormat?.height ?: 0
        c.episodeMeta.text = if (height > 0) {
            getString(R.string.player_episode_meta_quality, number, height)
        } else {
            getString(R.string.player_episode_meta, number)
        }
        c.episodeMeta.isVisible = true
    }

    private fun updateRemaining(positionMs: Long, durationMs: Long) {
        val c = _controls ?: return
        if (durationMs <= 0) {
            c.exoRemaining.isVisible = false
            return
        }
        val remaining = (durationMs - positionMs).coerceAtLeast(0L) / 1000
        val h = remaining / 3600
        val m = (remaining / 60) % 60
        val s = remaining % 60
        c.exoRemaining.text = if (h > 0) {
            String.format(Locale.ROOT, "−%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "−%02d:%02d", m, s)
        }
        c.exoRemaining.isVisible = true
    }

    private fun stopProgressTracking() {
        tickRunnable?.let { tickHandler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        val runnable = object : Runnable {
            override fun run() {
                val p = player
                if (p == null || _binding == null) return
                val position = p.currentPosition
                val duration = p.duration
                updateRemaining(position, duration)
                if (p.isPlaying && !switchingEpisode) {
                    if (AutoAdvance.shouldStart(position, duration, upNextShown || upNextCancelled)) {
                        showUpNext()
                    }
                    if (duration > 0 && position >= duration * TRACKER_WATCHED_FRACTION) {
                        reportToTrackers()
                    }
                }
                tickHandler.postDelayed(this, 1000)
            }
        }
        tickRunnable = runnable
        tickHandler.post(runnable)
    }

    private fun showUpNext() {
        if (dialogOpen || binding.playerError.root.isVisible) return
        val next = model.currentEpIndex + 1
        val ep = episodeList.getOrNull(next) ?: return
        upNextShown = true
        binding.pvPlayer.hideController()
        val number = ep.episode?.takeIf { it > 0 } ?: (next + 1)
        binding.upNextOverlay.show(
            UpNextOverlayView.Spec(
                title = args.name,
                episodeLabel = getString(R.string.player_up_next_episode, number),
                thumbnailUrl = ep.snapshot ?: args.image,
                seconds = AutoAdvance.COUNTDOWN_SECONDS,
            ),
            onPlayNow = { openEpisode(next) },
            onCancel = { upNextCancelled = true },
        )
    }

    private fun reportToTrackers() {
        val episodeNumber = model.currentEpIndex + 1
        if (episodeNumber <= 0 || !trackersReported.add(episodeNumber)) return
        val contentId = args.seriesMainId
        if (contentId.isBlank()) {
            trackersReported.remove(episodeNumber)
            return
        }
        val provider = extensionEngine.getActiveProvider().orEmpty()
        anilistTracker.reportEpisodeAsync(
            provider = provider, contentId = contentId, title = args.name,
            episodeNumber = episodeNumber,
        )
        malTracker.reportEpisodeAsync(
            provider = provider, contentId = contentId, title = args.name,
            episodeNumber = episodeNumber,
        )
    }

    private fun observeRemote() {
        remote.start()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { remote.commands.collect(::handleRemote) }
                launch { reportStateWhileVisible() }
            }
        }
    }

    private fun handleRemote(command: RemoteCommand) {
        val p = player ?: return
        when (command.type) {
            "play" -> p.play()
            "pause" -> p.pause()
            "playpause" -> togglePlayPause()
            "stop" -> navigateBack()
            "seek" -> command.positionMs?.let { p.seekTo(it.coerceAtLeast(0)) }
            "seekBy" -> command.deltaMs?.let { seekBy(it) }
            "volume" -> command.value?.let { p.volume = it.toFloat().coerceIn(0f, 1f) }
            "next" -> openNextEpisode()
            "prev" -> openPreviousEpisode()
        }
    }

    private suspend fun reportStateWhileVisible() {
        while (true) {
            val p = player
            if (p != null && _binding != null) {
                remote.report(
                    screen = "player",
                    title = args.name,
                    episode = episodeList.getOrNull(model.currentEpIndex)?.episode?.toString(),
                    playing = p.isPlaying,
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = p.duration.takeIf { it > 0 },
                )
            }
            delay(2_000)
        }
    }

    private fun navigateBack() {
        if (!isAdded) return
        if (LocalData.isHistoryItemClicked) {
            startActivity(Intent(context, ProfileActivity::class.java))
            activity?.finishDeferred()
        } else {
            runCatching { findNavController().navigateUp() }.onFailure { it.printStackTrace() }
        }
    }

    private class Setting(
        val label: String,
        val value: String,
        val options: List<SettingRow>,
        val apply: (Int) -> Unit,
    )

    private fun buildSettings(): List<Setting> {
        val out = mutableListOf<Setting>()
        val options = model.videoOptionsData.value.orEmpty()
        val p = player

        val servers = VideoOptionGroups.servers(options)
        if (servers.size > 1) {
            out += Setting(
                getString(R.string.player_server_title),
                VideoOptionGroups.serverOf(options, model.currentSelectedVideoOptionIndex),
                emptyList(),
            ) { showServerDialog() }
        }

        val variants = p?.let { NativeQualities.of(it.currentTracks) } ?: emptyList()
        if (variants.isNotEmpty()) {
            val rows = buildList {
                add(
                    SettingRow(
                        getString(R.string.quality_auto), getString(R.string.quality_auto_hint),
                        ticked = selectedNativeQuality == null,
                    )
                )
                variants.forEach {
                    add(
                        SettingRow(
                            "${it.height}p", NativeQualities.bitrateLabel(it.bitrate),
                            ticked = selectedNativeQuality == it,
                        )
                    )
                }
            }
            out += Setting(
                getString(R.string.player_quality_title),
                selectedNativeQuality?.let { "${it.height}p" } ?: getString(R.string.quality_auto),
                rows,
            ) { i ->
                selectedNativeQuality = if (i == 0) null else variants.getOrNull(i - 1)
                applyNativeQuality()
            }
        } else if (options.isNotEmpty()) {
            out += Setting(getString(R.string.player_quality_title), "", emptyList()) {
                showQualityDialog()
            }
        }

        val audio = p?.let { NativeTracks.audio(it.currentTracks) } ?: emptyList()
        if (audio.isNotEmpty()) {
            val playing = audio.firstOrNull { isPlaying(it) }
            out += Setting(
                getString(R.string.player_audio_title),
                (selectedAudio ?: playing)?.label.orEmpty(),
                audio.map {
                    SettingRow(it.label, it.detail, ticked = it == (selectedAudio ?: playing))
                },
            ) { i ->
                selectedAudio = audio.getOrNull(i)
                rememberAudioChoice(selectedAudio)
                applyTrack(C.TRACK_TYPE_AUDIO, selectedAudio)
            }
        }

        if (extractorSubtitles.isNotEmpty()) {
            val chosen = extractorSubtitles.getOrNull(model.currentSubEpIndex)
                ?.takeIf { subtitlesEnabled }
            out += Setting(
                getString(R.string.player_subtitle_title),
                chosen?.label ?: getString(R.string.off),
                emptyList(),
            ) { onSubtitleButton() }
        } else {
            val embedded = embeddedTextTracks()
            if (embedded.isNotEmpty()) {
                val rows = buildList {
                    add(SettingRow(getString(R.string.off), ticked = selectedText == null))
                    embedded.forEach {
                        add(SettingRow(it.label, it.detail, ticked = it == selectedText))
                    }
                }
                out += Setting(
                    getString(R.string.player_subtitle_title),
                    selectedText?.label ?: getString(R.string.off),
                    rows,
                ) { i -> applySubtitleChoice(if (i == 0) null else embedded.getOrNull(i - 1)) }
            } else if (args.name.isNotBlank()) {
                out += Setting(
                    getString(R.string.player_subtitle_title),
                    getString(R.string.off),
                    emptyList(),
                ) { onSubtitleButton() }
            }
        }

        out += Setting(
            getString(R.string.player_speed_title),
            speedLabel(playbackSpeed),
            SPEEDS.map { SettingRow(speedLabel(it), ticked = it == playbackSpeed) },
        ) { i -> setPlaybackSpeed(SPEEDS.getOrNull(i) ?: 1.0f) }

        out += Setting(
            getString(R.string.player_resize_title),
            resizeLabels.getOrElse(currentResizeIdx) { "" },
            resizeLabels.mapIndexed { i, label ->
                SettingRow(label, resizeHints[i], ticked = i == currentResizeIdx)
            },
        ) { i -> setResizeMode(i) }
        return out
    }

    private fun speedLabel(speed: Float): String =
        if (speed == 1.0f) getString(R.string.player_speed_normal) else "${speed}x"

    private fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        prefs.setPlaybackSpeed(speed)
        player?.setPlaybackSpeed(speed)
    }

    private fun setResizeMode(index: Int) {
        currentResizeIdx = index.coerceIn(0, resizeModes.lastIndex)
        prefs.setResizeModeIndex(currentResizeIdx)
        binding.pvPlayer.resizeMode = resizeModes[currentResizeIdx]
    }

    private val resizeLabels
        get() = listOf(
            getString(R.string.player_resize_fit),
            getString(R.string.player_resize_zoom),
            getString(R.string.player_resize_stretch),
        )

    private val resizeHints
        get() = listOf(
            getString(R.string.player_resize_fit_hint),
            getString(R.string.player_resize_zoom_hint),
            getString(R.string.player_resize_stretch_hint),
        )

    private fun showSettingsMenu() {
        var settings = buildSettings()
        PlayerSettingsPopup().apply {
            rootRows = {
                settings = buildSettings()
                settings.map { SettingRow(it.label, it.value) }
            }
            optionsFor = { i -> settings.getOrNull(i)?.options.orEmpty() }
            onOption = { row, option -> settings.getOrNull(row)?.apply?.invoke(option) }
            onImmediate = { i -> settings.getOrNull(i)?.apply?.invoke(0) }
        }.show(parentFragmentManager, "PlayerSettingsPopup")
    }

    private fun showServerDialog() {
        val options = model.videoOptionsData.value.orEmpty()
        val servers = VideoOptionGroups.servers(options)
        if (servers.size < 2) return
        val current = VideoOptionGroups.serverOf(options, model.currentSelectedVideoOptionIndex)
        val rows = servers.map { name ->
            VideoServersAdapter.ServerRow(
                name = name,
                qualities = VideoOptionGroups.indicesFor(options, name)
                    .mapNotNull { VideoOptionGroups.resolutionOf(options[it]) }
                    .distinct()
                    .sortedDescending()
                    .joinToString(" · ") { "${it}p" },
            )
        }
        VideoServerDialog(rows, servers.indexOf(current).coerceAtLeast(0)).apply {
            setOnServerPicked { picked ->
                val target = VideoOptionGroups.switchTo(
                    options, model.currentSelectedVideoOptionIndex, picked,
                )
                if (target != model.currentSelectedVideoOptionIndex) {
                    model.currentSelectedVideoOptionIndex = target
                    model.updateQualityByIndex()
                }
            }
        }.show(parentFragmentManager, "VideoServerDialog")
    }

    private fun showQualityDialog() {
        val options = model.videoOptionsData.value.orEmpty()
        val variants = player?.let { NativeQualities.of(it.currentTracks) } ?: emptyList()
        if (variants.isNotEmpty()) {
            showNativeQualityDialog(variants)
            return
        }
        if (options.isEmpty()) return
        val currentServer =
            VideoOptionGroups.serverOf(options, model.currentSelectedVideoOptionIndex)
        val indices = VideoOptionGroups.indicesFor(options, currentServer)
            .ifEmpty { options.indices.toList() }
        val subset = indices.map { options[it] }
        val selected = indices.indexOf(model.currentSelectedVideoOptionIndex).coerceAtLeast(0)

        VideoQualityDialog(subset, selected).apply {
            setYesContinueListener { _, i ->
                val target = indices.getOrNull(i) ?: return@setYesContinueListener
                if (target != model.currentSelectedVideoOptionIndex) {
                    model.currentSelectedVideoOptionIndex = target
                    model.updateQualityByIndex()
                }
            }
        }.show(parentFragmentManager, "VideoQualityDialog")
    }

    private fun showNativeQualityDialog(variants: List<NativeQualities.Variant>) {
        val rows = buildList {
            add(
                VideoServersAdapter.ServerRow(
                    getString(R.string.quality_auto), getString(R.string.quality_auto_hint)
                )
            )
            variants.forEach {
                add(
                    VideoServersAdapter.ServerRow(
                        "${it.height}p", NativeQualities.bitrateLabel(it.bitrate)
                    )
                )
            }
        }
        val selected = selectedNativeQuality?.let { variants.indexOf(it) + 1 }?.coerceAtLeast(0) ?: 0
        VideoServerDialog(
            rows, selected,
            titleRes = R.string.player_quality_title,
            subtitleRes = R.string.player_quality_subtitle,
        ).apply {
            setOnRowPicked { index ->
                selectedNativeQuality = if (index == 0) null else variants.getOrNull(index - 1)
                applyNativeQuality()
            }
        }.show(parentFragmentManager, "NativeQualityDialog")
    }

    private fun showAudioDialog() {
        val p = player ?: return
        val options = NativeTracks.audio(p.currentTracks)
        if (options.isEmpty()) return
        val rows = options.map { VideoServersAdapter.ServerRow(it.label, it.detail) }
        val current = selectedAudio?.let { chosen ->
            options.indexOfFirst { it.label == chosen.label && it.detail == chosen.detail }
        } ?: options.indexOfFirst { isPlaying(it) }
        VideoServerDialog(
            rows, current.coerceAtLeast(0),
            titleRes = R.string.player_audio_title,
            subtitleRes = R.string.player_audio_subtitle,
        ).apply {
            setOnRowPicked { index ->
                selectedAudio = options.getOrNull(index)
                rememberAudioChoice(selectedAudio)
                applyTrack(C.TRACK_TYPE_AUDIO, selectedAudio)
            }
        }.show(parentFragmentManager, "AudioTrackDialog")
    }

    private fun isPlaying(option: NativeTracks.Option): Boolean =
        runCatching { option.group.isTrackSelected(option.index) }.getOrDefault(false)

    private fun preferredAudioLanguages(): Array<String> {
        val out = LinkedHashSet<String>()
        prefs.getAudioLanguage()?.takeIf { it.isNotBlank() }?.let { out += it.lowercase() }
        val locale = resources.configuration.locales.takeIf { !it.isEmpty }?.get(0)
        locale?.isO3Language?.takeIf { it.isNotBlank() }?.let { out += it.lowercase() }
        locale?.language?.takeIf { it.isNotBlank() }?.let { out += it.lowercase() }
        out += listOf("jpn", "eng")
        return out.toTypedArray()
    }

    private fun rememberAudioChoice(option: NativeTracks.Option?) {
        val code = option?.language?.takeIf { it.isNotBlank() } ?: return
        prefs.setAudioLanguage(code)
    }

    private fun applyTrack(type: Int, option: NativeTracks.Option?) {
        val selector = trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, false)
                .apply { option?.let { addOverride(NativeTracks.overrideFor(it)) } }
        )
    }

    private fun applyNativeQuality() {
        val selector = trackSelector ?: return
        val variant = selectedNativeQuality
        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .apply { variant?.let { addOverride(NativeQualities.overrideFor(it)) } }
        )
    }

    private fun setTextRendererEnabled(enabled: Boolean) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .apply { if (enabled) clearOverridesOfType(C.TRACK_TYPE_TEXT) }
            .build()
    }

    private fun setSubtitlesEnabled(enabled: Boolean) {
        subtitlesEnabled = enabled
        prefs.setSubtitlesEnabled(enabled)
        updateSubtitleIcon(enabled)
    }

    private fun updateSubtitleIcon(enabled: Boolean) {
        val c = _controls ?: return
        c.exoSubtitlee.alpha = if (enabled) 1f else 0.45f
        _binding?.pvPlayer?.subtitleView?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun adoptSubtitleList(list: List<SubTitle>) {
        extractorSubtitles = list + onlineSubtitles
        if (extractorSubtitles.isEmpty()) return
        if (!subtitlesEnabled) {
            model.currentSubEpIndex = -1
            return
        }
        val match =
            SubtitleChoice.indexFor(extractorSubtitles.map { it.label }, extractorSubtitleLabel)
        model.currentSubEpIndex = when {
            match >= 0 -> match
            model.currentSubEpIndex in extractorSubtitles.indices -> model.currentSubEpIndex
            else -> 0
        }
    }

    private fun embeddedTextTracks(): List<NativeTracks.Option> {
        val p = player ?: return emptyList()
        return NativeTracks.text(p.currentTracks).filter { it.id != SubtitleSideloader.TRACK_ID }
    }

    private fun showEmbeddedSubtitleDialog() {
        val options = embeddedTextTracks()
        if (options.isEmpty()) return
        val rows = buildList {
            add(
                VideoServersAdapter.ServerRow(
                    getString(R.string.off), getString(R.string.player_subtitle_off_hint)
                )
            )
            options.forEach { add(VideoServersAdapter.ServerRow(it.label, it.detail)) }
        }
        val current = selectedText
            ?.let { chosen -> options.indexOfFirst { it.label == chosen.label } + 1 }
            ?.coerceAtLeast(0) ?: 0
        VideoServerDialog(
            rows, current,
            titleRes = R.string.player_subtitle_title,
            subtitleRes = R.string.player_subtitle_subtitle,
        ).apply {
            setOnRowPicked { index ->
                applySubtitleChoice(if (index == 0) null else options.getOrNull(index - 1))
            }
        }.show(parentFragmentManager, "EmbeddedSubtitleDialog")
    }

    private fun applySubtitleChoice(option: NativeTracks.Option?) {
        selectedText = option
        setSubtitlesEnabled(option != null)
        val selector = trackSelector
        if (option == null && selector != null) {
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
        } else {
            applyTrack(C.TRACK_TYPE_TEXT, option)
        }
    }

    private fun onSubtitleButton() {
        if (extractorSubtitles.isEmpty()) {
            if (embeddedTextTracks().isNotEmpty()) {
                showEmbeddedSubtitleDialog()
                return
            }
            if (args.name.isBlank()) return
        }
        val currentSelected = extractorSubtitles.getOrNull(model.currentSubEpIndex)
        val isSerial = episodeList.size > 1
        val dialog = SubtitleChooserDialog.newInstance(
            extractorSubtitles,
            currentSelected,
            subtitlesEnabled && currentSelected != null,
            offsetMs = subtitleOffsetMs.toInt(),
            searchTitle = args.name,
            isSerial = isSerial,
            season = if (isSerial) {
                SeasonNumber.of(episodeList.getOrNull(model.currentEpIndex)?.title, args.name)
            } else null,
            episode = if (isSerial) currentEpisodeNumber() else null,
        )
        dialog.setOnOnlinePicked { picked ->
            onlineSubtitles = onlineSubtitles + picked
            extractorSubtitles = extractorSubtitles + picked
            model.currentSubEpIndex = extractorSubtitles.lastIndex
            setSubtitlesEnabled(true)
            extractorSubtitleLabel = picked.label
            prefs.setSubtitleLabel(picked.label)
            selectedText = null
            reloadWithSubtitle(true)
        }
        dialog.setSubtitleSelectionListener { selected ->
            val enabled = selected?.file?.isNotEmpty() == true
            val newIndex = if (enabled) extractorSubtitles.indexOf(selected) else -1
            if (model.currentSubEpIndex == newIndex && subtitlesEnabled == enabled) {
                return@setSubtitleSelectionListener
            }
            model.currentSubEpIndex = newIndex
            setSubtitlesEnabled(enabled)
            extractorSubtitleLabel = selected?.label
            prefs.setSubtitleLabel(selected?.label)
            selectedText = null
            reloadWithSubtitle(enabled)
        }
        dialog.setOnSubtitleOffsetChanged { offsetMs ->
            if (subtitleOffsetMs == offsetMs.toLong()) return@setOnSubtitleOffsetChanged
            subtitleOffsetMs = offsetMs.toLong()
            subtitleOffsetJob?.cancel()
            subtitleOffsetJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(SUBTITLE_OFFSET_DEBOUNCE_MS)
                reloadWithSubtitle(subtitlesEnabled)
            }
        }
        dialog.setOnSubtitleStyleChangedListener { applySubtitleStyle() }
        dialog.show(parentFragmentManager, "subtitle_chooser")
    }

    private fun reloadWithSubtitle(enabled: Boolean) {
        val vod = currentVod ?: return
        val p = player ?: return
        val previousPos = p.currentPosition
        val wasPlaying = p.isPlaying
        p.pause()
        loadSource(vod, previousPos, autoPlay = wasPlaying) { setTextRendererEnabled(enabled) }
    }

    private fun reportSubtitleFailure() {
        val label = subtitleFailure ?: return
        subtitleFailure = null
        if (_binding == null || !isAdded) return
        showToast(getString(R.string.subtitle_load_failed, label))
    }

    private fun buildPlaybackSource(videoUrl: String, mimeType: String?): MediaSource {
        val chosen =
            if (subtitlesEnabled) extractorSubtitles.getOrNull(model.currentSubEpIndex) else null
        val subtitle = when (val outcome = sideloader.prepare(chosen, subtitleOffsetMs)) {
            is SubtitleSideloader.Outcome.Attached -> outcome.config
            is SubtitleSideloader.Outcome.Failed -> {
                subtitleFailure = outcome.label
                null
            }

            SubtitleSideloader.Outcome.None -> null
        } ?: return createMediaSource(videoUrl, mimeType)

        val mime = resolveMime(videoUrl, mimeType)
        val item = MediaItem.Builder()
            .setUri(videoUrl)
            .setTag(args.name)
            .apply { mime?.let { setMimeType(it) } }
            .setSubtitleConfigurations(listOf(subtitle))
            .build()
        return DefaultMediaSourceFactory(http.dataSourceFactory).createMediaSource(item)
    }

    private fun createMediaSource(url: String, mimeType: String?): MediaSource {
        val mime = resolveMime(url, mimeType) ?: MimeTypes.APPLICATION_MP4
        val item = MediaItem.Builder().setUri(url).setMimeType(mime).setTag(args.name).build()
        return if (mime == MimeTypes.APPLICATION_M3U8) {
            HlsMediaSource.Factory(http.dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(http.dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(1024 * 1024).createMediaSource(item)
        }
    }

    private fun resolveMime(url: String, declared: String?): String? {
        val u = url.substringBefore('?').lowercase()
        return when {
            u.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            u.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            u.contains(".mp4") -> MimeTypes.VIDEO_MP4
            u.contains(".mkv") || u.contains(".webm") -> MimeTypes.VIDEO_WEBM
            declared == MimeTypes.APPLICATION_M3U8 || declared == MimeTypes.APPLICATION_MPD -> declared
            u.contains("/m3u8/") || u.contains("/hls/") -> MimeTypes.APPLICATION_M3U8
            else -> declared
        }
    }

    private fun effectiveStreamUrl(vod: VodMovieResponse): String {
        if (!vod.useLocalProxy) return vod.urlobj
        return runCatching {
            hlsProxy.register(
                upstreamUrl = vod.urlobj,
                headers = vod.header,
                localProxy = vod.localProxyJson?.let { JSONObject(it) } ?: JSONObject(),
                requestTransform = vod.requestTransformJson?.let { JSONObject(it) } ?: JSONObject(),
            )
        }.getOrDefault(vod.urlobj)
    }

    private fun applySubtitleStyle() {
        val subtitleView = _binding?.pvPlayer?.subtitleView ?: return
        if (!prefs.isSubtitleCustom()) {
            subtitleView.setStyle(
                CaptionStyleCompat(
                    Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null
                )
            )
            subtitleView.setUserDefaultTextSize()
            return
        }
        val s = prefs.getSubtitleStyle()
        subtitleView.setStyle(
            CaptionStyleCompat(
                s.color,
                if (s.background) Color.argb(180, 0, 0, 0) else Color.TRANSPARENT,
                Color.TRANSPARENT,
                if (s.outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE
                else CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.BLACK,
                when (s.font) {
                    PreferenceManager.Font.DEFAULT -> null
                    PreferenceManager.Font.POPPINS ->
                        ResourcesCompat.getFont(requireContext(), R.font.poppins)

                    PreferenceManager.Font.DAYS ->
                        ResourcesCompat.getFont(requireContext(), R.font.days)

                    PreferenceManager.Font.MONO -> Typeface.MONOSPACE
                }
            )
        )
        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp.toFloat())
    }

    private fun setupOrUpdatePreviewThumbnails(vttUrl: String?, headers: Map<String, String>) {
        val url = vttUrl?.trim().orEmpty()
        val previewImage = controls.exoThumbnail
        val timeBar = timeBar() ?: return

        if (url.isEmpty()) {
            previewImage.visibility = View.GONE
            currentVttUrl = ""
            thumbnailsAvailable = false
            thumbLoadJob?.cancel()
            thumbFetchJob?.cancel()
            thumbLoader?.clear()
            thumbLoader = null
            return
        }

        if (!thumbListenerAttached) {
            thumbListenerAttached = true
            timeBar.addListener(object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    if (timeBar is TrailerPlayerScreen.ExtendedTimeBar) {
                        previewImage.visibility = View.VISIBLE
                        requestThumb(previewImage, timeBar, position)
                    }
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    if (timeBar is TrailerPlayerScreen.ExtendedTimeBar) {
                        previewImage.visibility = View.VISIBLE
                        requestThumb(previewImage, timeBar, position)
                    }
                }

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    previewImage.visibility = View.GONE
                    thumbFetchJob?.cancel()
                    thumbFetchJob = null
                }
            })
        }

        if (currentVttUrl != url || thumbLoader == null) {
            currentVttUrl = url
            val client = http.client
            thumbLoader?.clear()
            thumbLoader = VttSpriteThumbnailLoader(client, headers)
            thumbLoadJob?.cancel()
            thumbLoadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    if (url.endsWith(".jpg") || url.endsWith(".png")) {
                        val request = Request.Builder().url(url)
                            .apply { headers.forEach { (k, v) -> header(k, v) } }
                            .build()
                        val body =
                            client.newCall(request).execute().use { it.body?.string().orEmpty() }
                        if (body.trimStart().startsWith("WEBVTT")) {
                            thumbLoader?.loadVttFromContent(
                                body, url.substringBeforeLast("/") + "/"
                            )
                        }
                        Unit
                    } else {
                        thumbLoader?.loadVtt(url)
                    }
                    thumbnailsAvailable = thumbLoader?.isLoaded() == true
                }.onFailure { Log.e("VTT_THUMB", "loadVtt failed: ${it.message}", it) }
            }
        }
    }

    private fun requestThumb(
        imageView: ImageView, timeBar: TrailerPlayerScreen.ExtendedTimeBar, position: Long
    ) {
        thumbFetchJob?.cancel()
        thumbFetchJob = viewLifecycleOwner.lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { thumbLoader?.getThumbnail(position) }.getOrNull()
            }
            if (_binding == null) return@launch
            if (bmp != null) imageView.setImageBitmap(bmp) else imageView.setImageDrawable(null)
            positionThumb(imageView, timeBar, position)
        }
    }

    private fun positionThumb(
        imageView: ImageView, timeBar: TrailerPlayerScreen.ExtendedTimeBar, position: Long
    ) {
        val total = player?.duration?.takeIf { it > 0 } ?: return
        val w = timeBar.width
        if (w <= 0) return
        val scrubberX = (position.toFloat() / total) * w
        val thumbX = scrubberX - (imageView.width / 2f)
        imageView.translationX =
            thumbX.coerceIn(0f, (w - imageView.width).toFloat().coerceAtLeast(0f))
        val paddingPx = 8f * resources.displayMetrics.density
        val targetY = timeBar.y - imageView.height - paddingPx
        ObjectAnimator.ofFloat(imageView, "translationY", imageView.translationY, targetY).apply {
            duration = 120L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun persistProgress() {
        if (switchingEpisode) return
        val entity = historyEntity() ?: return
        model.persistHistory(entity)
        model.getWatchedHistoryEntity = entity
    }

    private fun historyEntity(): WatchHistoryEntity? {
        val p = player ?: return null
        val position = p.currentPosition
        if (position <= 0) return null
        val duration = p.duration.takeIf { it > 0 } ?: 0L
        val ep = episodeList.getOrNull(model.currentEpIndex) ?: return null
        val session = ep.session ?: return null
        val vod = currentVod
        val provider = extensionEngine.getActiveProvider().orEmpty()
        val providerName = extensionEngine.getActiveProviderName().orEmpty()
        val existing = model.getWatchedHistoryEntity
        if (existing != null && existing.session == session) {
            return existing.copy(
                totalDuration = duration,
                imdbID = args.seriesMainId,
                isEpisode = true,
                epIndex = model.currentEpIndex,
                lastPosition = position,
                videoUrl = vod?.urlobj ?: existing.videoUrl,
                currentQualityIndex = model.currentSelectedVideoOptionIndex,
                watchedAt = System.currentTimeMillis(),
                providerId = existing.providerId.ifBlank { provider },
                currentSourceName = existing.currentSourceName.ifBlank { providerName },
            )
        }
        return WatchHistoryEntity(
            session = session,
            title = getString(R.string.history_episode_title, ep.title, currentEpisodeNumber()),
            mediaName = args.name,
            image = ep.snapshot ?: args.image,
            categoryProperty = "",
            categoryid = args.seriesMainId,
            country = "",
            description = "",
            language = "",
            rating = 0.0,
            page = args.currentPage,
            release_year = "",
            videoUrl = vod?.urlobj.orEmpty(),
            totalDuration = duration,
            lastPosition = position,
            imdbID = args.seriesMainId,
            epIndex = model.currentEpIndex,
            isEpisode = true,
            currentQualityIndex = model.currentSelectedVideoOptionIndex,
            source = prefs.getString(LocalData.SOURCE),
            providerId = provider,
            currentSourceName = providerName,
        )
    }

    private fun toggleSidebarRight(show: Boolean) {
        val sidebar = binding.sidebarRight
        val epListContainer = controls.epListContainer
        sidebar.post {
            if (_binding == null) return@post
            val sidebarWidth = sidebar.width.toFloat()
            if (show) {
                binding.pvPlayer.hideController()
                sidebar.apply {
                    isVisible = true
                    translationX = sidebarWidth
                    animate().translationX(0f).setDuration(120)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()
                }
                binding.btnHideMenuRight.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isEnabled = true
                }
                binding.episodeRv.post {
                    if (_binding == null) return@post
                    binding.episodeRv.setSelectedPosition(model.currentEpIndex.coerceAtLeast(0))
                    binding.episodeRv.requestFocus()
                }
                epListContainer.apply {
                    isFocusable = false
                    gone()
                }
            } else {
                sidebar.animate().translationX(sidebarWidth).setDuration(120)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { sidebar.isVisible = false }.start()
                epListContainer.apply {
                    isFocusable = true
                    visible()
                }
                binding.btnHideMenuRight.apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isEnabled = false
                }
                binding.episodeRv.clearFocus()
                restoreControlFocus()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopProgressTracking()
        player?.let {
            it.pause()
            persistProgress()
        }
    }

    override fun onResume() {
        super.onResume()
        pendingResume?.let { offerResume(it) }
        if (player?.isPlaying == true) startProgressTracking()
    }

    override fun onDestroyView() {
        parentFragmentManager.unregisterFragmentLifecycleCallbacks(dialogWatcher)
        lastFocusedControl = null
        stopProgressTracking()
        loadJob?.cancel()
        loadingJob?.cancel()
        toastJob?.cancel()
        bufferingRunnable?.let { tickHandler.removeCallbacks(it) }
        subtitleOffsetJob?.cancel()
        thumbLoadJob?.cancel()
        thumbFetchJob?.cancel()
        thumbLoader?.clear()
        thumbLoader = null
        skipIntroView?.detach()
        skipIntroView = null
        persistProgress()
        model.syncHistory()
        player?.let {
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        mediaSession?.release()
        mediaSession = null
        sideloader.clear()
        _controls = null
        _binding = null
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private companion object {
        const val TRACKER_WATCHED_FRACTION = 0.85
        const val SUBTITLE_OFFSET_DEBOUNCE_MS = 700L
        const val LOADING_SLOW_MS = 8_000L
        const val LOADING_STUCK_MS = 20_000L
        const val TOAST_MS = 3_000L
        const val BUFFERING_DELAY_MS = 400L
        const val CONTROLS_TIMEOUT_MS = 5_000
        const val SUBTITLE_PADDING_DEFAULT = 0.08f
        const val SUBTITLE_PADDING_CONTROLS = 0.36f
        val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}

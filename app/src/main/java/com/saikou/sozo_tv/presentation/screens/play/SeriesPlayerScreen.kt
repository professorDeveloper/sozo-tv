package com.saikou.sozo_tv.presentation.screens.play

import kotlinx.coroutines.delay
import com.saikou.sozo_tv.data.repository.RemoteControlManager
import com.saikou.sozo_tv.data.remote.remote.RemoteCommand
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.Tracks
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.utils.snackString
import com.saikou.sozo_tv.domain.player.NativeQualities
import com.saikou.sozo_tv.domain.player.NativeTracks
import com.saikou.sozo_tv.domain.player.VideoOptionGroups
import com.saikou.sozo_tv.adapters.VideoServersAdapter
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.repository.AnilistTracker
import org.koin.android.ext.android.inject
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodePlayerAdapter
import com.saikou.sozo_tv.components.SkipIntroView
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.SeriesPlayerScreenBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.screens.play.dialog.SubtitleChooserDialog
import com.saikou.sozo_tv.presentation.viewmodel.PlayAnimeViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.VttSpriteThumbnailLoader
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.observeOnce
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class SeriesPlayerScreen : Fragment() {

    private val remote: RemoteControlManager by inject()
    private var _binding: SeriesPlayerScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var player: ExoPlayer
    @OptIn(UnstableApi::class)
    private var trackSelector: DefaultTrackSelector? = null
    private var selectedNativeQuality: NativeQualities.Variant? = null
    private var selectedAudio: NativeTracks.Option? = null
    private var selectedText: NativeTracks.Option? = null
    private var playbackSpeed = 1.0f
    private lateinit var dataSourceFactory: DataSource.Factory
    private var okHttpClient: OkHttpClient? = null

    private val model by viewModel<PlayAnimeViewModel>()

    private val extensionEngine: ExtensionEngine by inject()

    private val anilistTracker: AnilistTracker by inject()

    private val anilistReported = HashSet<Int>()

    private lateinit var mediaSession: MediaSession
    private val args by navArgs<SeriesPlayerScreenArgs>()
    private val episodeList = arrayListOf<Data>()

    private var countdownShown = false
    private var isCountdownActive = false
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    private val handler = Handler()
    private lateinit var skipIntroView: SkipIntroView

    private var lastHeaders: Map<String, String> = emptyMap()

    private var ignoreNextEpisodeSuccess: Boolean = false
    private var qualityObserversBound: Boolean = false

    private var thumbListenerAttached = false
    private var thumbLoader: VttSpriteThumbnailLoader? = null
    private var thumbLoadJob: Job? = null
    private var thumbFetchJob: Job? = null
    private var currentVttUrl: String = ""

    private val PlayerControlView.binding
        @OptIn(UnstableApi::class) get() = ContentControllerTvSeriesBinding.bind(this.findViewById(R.id.cl_exo_controller_tv))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SeriesPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(UnstableApi::class)
    private fun buildOkHttpClient(headers: Map<String, String>): OkHttpClient {
        return OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                )
            ).addNetworkInterceptor { chain ->
                val original = chain.request()

                val b = original.newBuilder()

                headers.forEach { (k, v) ->
                    b.header(k, v)
                }
                // An extractor that returns no User-Agent used to let OkHttp send
                // its own, which Cloudflare rejects outright and which never
                // matches the cf_clearance earned during extraction.
                if (headers.keys.none { it.equals("User-Agent", true) }) {
                    b.header("User-Agent", SOZO_USER_AGENT)
                }

                val req = b.build()
                val resp = chain.proceed(req)
                if (!resp.isSuccessful) {
                    Log.w(
                        "PlayerHttp",
                        // Values, not just names: a 403 usually comes down to WHICH
                        // Referer or UA went out, and names alone never showed that.
                        "${resp.code} ${req.url}\n  sent: " +
                            req.headers.joinToString("\n        ") { (k, v) -> "$k: $v" } +
                            "\n  got: ${resp.headers}"
                    )
                }
                resp
            }.connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            // Share the global WebView cookie store so the cf_clearance / session cookies that
            // NativeFetch + CloudflareInterceptor solved during resolveMedia are replayed on the
            // stream request.
            .cookieJar(eu.kanade.tachiyomi.network.AndroidCookieJar())
            // …but replaying is useless when nothing ever solved a challenge for THIS host.
            // The extractor talks to the site; the manifest and segments come from a separate
            // CDN the plugin never touches, so no clearance for it exists and the CDN answered
            // 403 with Server: cloudflare. Solving it here, on the request that actually hits
            // the CDN, is what makes the shared jar worth anything.
            .addInterceptor(cloudflareSolver())
            .ignoreAllSSLErrors().build()
    }

    @OptIn(UnstableApi::class)
    private fun updateFactories(headers: Map<String, String>) {
        lastHeaders = headers
        okHttpClient = buildOkHttpClient(headers)

        val okFactory = OkHttpDataSource.Factory(okHttpClient!!)

        dataSourceFactory = DefaultDataSource.Factory(requireContext(), okFactory)
    }

    /**
     * Solves a Cloudflare challenge in a WebView and stores the clearance in the
     * shared cookie jar. Only fires on 403/503 responses that carry a Cloudflare
     * Server header, so ordinary failures pass straight through.
     */
    private fun cloudflareSolver() = CloudflareInterceptor(
        MyApp.context.applicationContext,
        eu.kanade.tachiyomi.network.AndroidCookieJar(),
    ) { SOZO_USER_AGENT }

    // Loopback HLS proxy for IP/cookie-bound + RC4-signed CDNs (uzmovi/uzdown). Uses its own
    // OkHttp client sharing the global WebView cookie jar so every upstream socket (manifest,
    // variants, segments, keys) carries the same IP + cf_clearance + signed headers.
    @OptIn(UnstableApi::class)
    private val hlsProxy by lazy {
        com.saikou.sozo_tv.engine.player.LocalHlsProxy(
            OkHttpClient.Builder()
                .followRedirects(true).followSslRedirects(true)
                .cookieJar(eu.kanade.tachiyomi.network.AndroidCookieJar())
                .addInterceptor(cloudflareSolver())
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                .ignoreAllSSLErrors().build()
        )
    }

    /** If the active source is flagged useLocalProxy, return the loopback URL; else the original. */
    private fun effectiveStreamUrl(originalUrl: String): String {
        val vod = model.seriesResponse ?: return originalUrl
        if (!vod.useLocalProxy) return originalUrl
        return runCatching {
            hlsProxy.register(
                upstreamUrl = originalUrl,
                headers = vod.header,
                localProxy = vod.localProxyJson?.let { org.json.JSONObject(it) } ?: org.json.JSONObject(),
                requestTransform = vod.requestTransformJson?.let { org.json.JSONObject(it) } ?: org.json.JSONObject(),
            )
        }.getOrDefault(originalUrl)
    }

    /**
     * The spinner shown while the stream stalls.
     *
     * Between the resolve overlay coming down and the first frame arriving there
     * was no feedback at all — a black screen at 00:00 that looked like a dead
     * player. Suppressed while the resolve overlay is up so the two don't stack.
     */
    private fun showBuffering(show: Boolean) {
        val b = _binding ?: return
        b.bufferingIndicator.isVisible = show && !b.loadingLayout.isVisible
    }

    private fun resetCountdownState() {
        countdownShown = false
        isCountdownActive = false
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                if (::player.isInitialized && player.isPlaying) {
                    val currentPosition = player.currentPosition
                    val duration = player.duration
                    if (duration > 0 && currentPosition > 0) {
                        val remainingTime = duration - currentPosition
                        if (remainingTime in 9001..10000 && !countdownShown && !isCountdownActive) {
                            showNextEpisodeCountdown()
                        }
                        if (currentPosition >= duration * ANILIST_WATCHED_FRACTION) {
                            reportToAnilist()
                        }
                    }
                }
                progressHandler?.postDelayed(this, 1000)
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    private fun reportToAnilist() {
        val episodeNumber = model.currentEpIndex + 1
        if (episodeNumber <= 0 || !anilistReported.add(episodeNumber)) return

        val contentId = args.seriesMainId
        if (contentId.isBlank()) {
            anilistReported.remove(episodeNumber)
            return
        }

        anilistTracker.reportEpisodeAsync(
            provider = extensionEngine.getActiveProvider().orEmpty(),
            contentId = contentId,
            title = args.name,
            episodeNumber = episodeNumber,
        )
    }

    private fun showNextEpisodeCountdown() {
        binding.apply {
            // Runs for history playback too. The history intent carries the same
            // (page, epIndex) the normal path uses, so `episodeList` is fully
            // populated either way — there was never a data reason to stop the
            // run at one episode, and stopping it is what users hit as "History
            // won't continue to the next episode".
            if (!countdownShown) {
                val nextEpisodeIndex = model.currentEpIndex + 1
                if (nextEpisodeIndex < episodeList.size) {
                    countdownShown = true
                    isCountdownActive = true
                    countdownOverlay.startCountdown(seconds = 10,
                        nextEpisode = nextEpisodeIndex + 1,
                        currentEpisode = model.currentEpIndex + 1,
                        title = args.name,
                        useEnglish = true,
                        onFinished = { playNextEpisodeAutomatically() },
                        onCancelled = {
                            isCountdownActive = false
                            player.play()
                        })
                }
            }
        }
    }

    /**
     * Playback commands from the phone.
     *
     * Handled here rather than in MainActivity because this is the only place
     * that knows whether anything is playing — and the phone's remote is
     * useless if pressing pause depends on which screen the TV happens to show.
     *
     * repeatOnLifecycle(STARTED) is what stops a backgrounded player from
     * acting on presses meant for whatever replaced it.
     */
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
        if (!::player.isInitialized) return
        when (command.type) {
            "play" -> player.play()
            "pause" -> player.pause()
            "playpause" -> if (player.isPlaying) player.pause() else player.play()
            "stop" -> navigateBack()
            "seek" -> command.positionMs?.let { player.seekTo(it.coerceAtLeast(0)) }
            "seekBy" -> command.deltaMs?.let {
                player.seekTo((player.currentPosition + it).coerceAtLeast(0))
            }
            "volume" -> command.value?.let {
                player.volume = it.toFloat().coerceIn(0f, 1f)
            }
            "next" -> playNextEpisodeAutomatically()
            "prev" -> playPreviousEpisode()
        }
    }

    /**
     * A heartbeat of what is on screen, so the phone's remote shows a moving
     * position instead of a still one.
     *
     * Two seconds, not per frame: the remote renders seconds, and the report is
     * a network call.
     */
    private suspend fun reportStateWhileVisible() {
        while (true) {
            if (::player.isInitialized && _binding != null) {
                remote.report(
                    screen = "player",
                    title = args.name,
                    episode = episodeList.getOrNull(model.currentEpIndex)?.episode?.toString(),
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.takeIf { it > 0 },
                )
            }
            delay(2_000)
        }
    }

    private fun navigateBack() {
        if (!isAdded) return

        if (LocalData.isHistoryItemClicked) {
            val intent = Intent(context, ProfileActivity::class.java)
            startActivity(intent)
            activity?.finishDeferred()
        } else {
            runCatching { findNavController().navigateUp() }.onFailure { it.printStackTrace() }
        }
    }

    @SuppressLint("StringFormatMatches")
    /**
     * The mirror of [playNextEpisodeAutomatically], for the phone's back button.
     *
     * Nothing on the TV itself moves backwards through episodes — the d-pad has
     * a previous button but it seeks — so this had no reason to exist until the
     * remote gained one.
     */
    private fun playPreviousEpisode() {
        if (model.currentEpIndex <= 0) return
        lifecycleScope.launch {
            saveWatchHistory()
            withContext(Dispatchers.Main) {
                model.currentEpIndex -= 1
                model.doNotAsk = false
                model.lastPosition = 0

                model.getCurrentEpisodeVodAnime(
                    episodeList[model.currentEpIndex].session.toString(), args.seriesMainId,
                    episodeNum = episodeList[model.currentEpIndex].episode ?: 0
                )

                model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                    if (resource is Resource.Success) {
                        playNewEpisode(resource.data.urlobj, headers = resource.data.header)
                        binding.pvPlayer.controller.binding.filmTitle.text =
                            getString(R.string.episode, args.name, model.currentEpIndex + 1)
                        resetCountdownState()
                    }
                }
            }
        }
    }

    private fun playNextEpisodeAutomatically() {
        if (model.currentEpIndex < episodeList.size - 1) {
            lifecycleScope.launch {
                saveWatchHistory()
                withContext(Dispatchers.Main) {
                    model.currentEpIndex += 1
                    model.doNotAsk = false
                    model.lastPosition = 0

                    model.getCurrentEpisodeVodAnime(
                        episodeList[model.currentEpIndex].session.toString(), args.seriesMainId,
                        episodeNum = episodeList[model.currentEpIndex].episode ?: 0
                    )

                    model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                        if (resource is Resource.Success) {
                            val newUrl = resource.data.urlobj
                            playNewEpisode(newUrl, headers = resource.data.header)

                            binding.pvPlayer.controller.binding.filmTitle.text =
                                getString(R.string.episode, args.name, model.currentEpIndex + 1)

                            resetCountdownState()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("WrongConstant")
    @OptIn(UnstableApi::class)
    private fun initializeVideo(headers: Map<String, String> = emptyMap()) {
        updateFactories(headers)

        if (::player.isInitialized) return

        val renderersFactory =
            DefaultRenderersFactory(requireContext()).setEnableDecoderFallback(true)
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT).setEnableAudioFloatOutput(false)
        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguages("hin", "jpn", "eng") // Hindi > Japanese > English
            )
        }
        this.trackSelector = trackSelector

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .build()

        player.setPlayWhenReady(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)

        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )

        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSession.Builder(requireContext(), player).build()
        }

        player.addListener(object : Player.Listener {
            /**
             * What a stream carries is only known once it has been parsed, so the
             * controls that depend on it are decided here rather than at bind
             * time — a dual-audio release and a mono one get different buttons.
             */
            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                // release() fires this on the way down, and it runs before
                // _binding is cleared — so the view may already be gone.
                val b = _binding ?: return
                val audio = NativeTracks.audio(tracks)
                b.pvPlayer.controller.binding.exoAudio.isVisible = audio.size > 1
                // A track the user picked cannot outlive the stream that
                // declared it; the groups belong to that stream.
                if (selectedAudio != null && audio.none { it.label == selectedAudio?.label }) {
                    selectedAudio = null
                }
                refreshSubtitleButton()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PLAYER_ERR", "code=${error.errorCodeName}", error)
                showBuffering(false)
                Bugsnag.notify(Exception("GGGG:${model.seriesResponse?.urlobj} || ${model.parser.name}" + error.message))
                handlePlaybackError(error)
            }

            @SuppressLint("SwitchIntDef")
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        showBuffering(false)
                        // Unconditional: the countdown/auto-advance tracker is what
                        // drives "next episode", and history playback needs it too.
                        resetCountdownState()
                        startProgressTracking()

                        val dur = player.duration
                        if (::skipIntroView.isInitialized) {
                            skipIntroView.cleanup()
                        }

                        runCatching {
                            skipIntroView = SkipIntroView(
                                binding.pvPlayer,
                                player,
                                model,
                                handler,
                                args.idMal,
                                episodeList.getOrNull(model.currentEpIndex)?.episode ?: 0,
                                dur / 1000
                            )
                            skipIntroView.initialize()
                        }.onFailure {
                            Log.w("SeriesPlayerScreen", "SkipIntro init failed: ${it.message}")
                        }
                    }

                    Player.STATE_BUFFERING -> {
                        Log.d("GGG", "Buffering... ${player.currentPosition} / ${player.duration}")
                        showBuffering(true)
                    }

                    Player.STATE_ENDED -> {
                        showBuffering(false)
                        if (player.duration > 0) {
                            stopProgressTracking()
                            if (!isCountdownActive) playNextEpisodeAutomatically()
                        }
                    }
                }
            }
        })

        binding.pvPlayer.player = player
        // Remotes with transport keys reach episode navigation through these.
        binding.pvPlayer.onNextEpisode = { playNextEpisodeAutomatically() }
        binding.pvPlayer.onPreviousEpisode = { playPreviousEpisode() }
        observeRemote()
        binding.pvPlayer.controller.binding.exoNextTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }
        binding.pvPlayer.controller.binding.exoPrevTenContainer.setOnClickListener {
            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
        }

        binding.pvPlayer.controller.binding.exoPlayPauseContainer.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                player.play()
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
            }
        }
        runCatching {
            binding.pvPlayer.controller.findViewById<View>(R.id.exo_progress).let { v ->
                (v as? TrailerPlayerScreen.ExtendedTimeBar)?.setKeyTimeIncrement(10_000)
            }
        }
    }

    private var currentResizeIdx = 0

    /** Wire the ⚙ settings button → screen-size (resize mode) + playback-speed menus. */
    @OptIn(UnstableApi::class)
    private fun setupPlayerSettings() {
        binding.pvPlayer.controller.binding.exoSettings.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Player settings")
                .setItems(arrayOf("Screen size", "Playback speed")) { _, which ->
                    if (which == 0) showResizeDialog() else showSpeedDialog()
                }
                .show()
        }
    }

    @OptIn(UnstableApi::class)
    private fun showResizeDialog() {
        val labels = arrayOf("Fit (letterbox)", "Fill (zoom)", "Stretch")
        val modes = intArrayOf(
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Screen size")
            .setSingleChoiceItems(labels, currentResizeIdx) { d, i ->
                currentResizeIdx = i
                binding.pvPlayer.resizeMode = modes[i]
                d.dismiss()
            }
            .show()
    }

    private fun bindQualityObserversOnce() {
        if (qualityObserversBound) return
        qualityObserversBound = true
        setupPlayerSettings()

        model.videoOptionsData.observe(viewLifecycleOwner) { videoOptions ->
            val controls = binding.pvPlayer.controller.binding
            val options = videoOptions.orEmpty()
            val servers = VideoOptionGroups.servers(options)

            // Only worth a button when there is a choice to make.
            controls.exoServer.isVisible = servers.size > 1

            controls.exoServer.setOnClickListener {
                if (servers.size < 2) return@setOnClickListener
                val current = VideoOptionGroups.serverOf(
                    options, model.currentSelectedVideoOptionIndex,
                )
                val rows = servers.map { name ->
                    VideoServersAdapter.ServerRow(
                        name = name,
                        // The raw resolution label repeats the host ("Vidstream-2 [Sub] ·
                        // 1080p"), so listing it verbatim printed the server name once per
                        // quality. Pull out the number instead.
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
                            ignoreNextEpisodeSuccess = true
                            model.currentSelectedVideoOptionIndex = target
                            model.updateQualityByIndex()
                        }
                    }
                }.show(parentFragmentManager, "VideoServerDialog")
            }

            controls.exoQuality.setOnClickListener {
                // The stream's own renditions come first: switching between them is a track
                // change, not a reload, and it is the only path that can offer auto.
                val variants = if (::player.isInitialized) {
                    NativeQualities.of(player.currentTracks)
                } else {
                    emptyList()
                }
                if (variants.isNotEmpty()) {
                    showNativeQualityDialog(variants)
                    return@setOnClickListener
                }
                if (options.isEmpty()) return@setOnClickListener
                // Scoped to the current server: a quality list spanning hosts means
                // picking a resolution silently moves you to a different one.
                val currentServer = VideoOptionGroups.serverOf(
                    options, model.currentSelectedVideoOptionIndex,
                )
                val indices = VideoOptionGroups.indicesFor(options, currentServer)
                    .ifEmpty { options.indices.toList() }
                val subset = indices.map { options[it] }
                val selected = indices.indexOf(model.currentSelectedVideoOptionIndex)
                    .coerceAtLeast(0)

                VideoQualityDialog(subset, selected).apply {
                    setYesContinueListener { _, i ->
                        val target = indices.getOrNull(i) ?: return@setYesContinueListener
                        if (target != model.currentSelectedVideoOptionIndex) {
                            ignoreNextEpisodeSuccess = true
                            model.currentSelectedVideoOptionIndex = target
                            model.updateQualityByIndex()
                        }
                    }
                }.show(parentFragmentManager, "VideoQualityDialog")
            }
        }

        model.currentQualityEpisode.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.loadingText.text = getString(R.string.quality_is_loading)
                }

                is Resource.Success -> {
                    ignoreNextEpisodeSuccess = false
                    binding.loadingLayout.gone()
                    val vod = resource.data
                    playQualityVideo(
                        videoUrl = vod.urlobj, headers = vod.header, mimeType = vod.type
                    )
                    setupOrUpdatePreviewThumbnails(vod.thumbnail, vod.header)
                }

                is Resource.Error -> {
                    ignoreNextEpisodeSuccess = false
                    binding.loadingLayout.gone()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.quality_switch_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> Unit
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(url: String, mimeType: String?): MediaSource {
        val mime = resolveMime(url, mimeType) ?: MimeTypes.APPLICATION_MP4
        val item = MediaItem.Builder().setUri(url).setMimeType(mime).setTag(args.name).build()

        return if (mime == MimeTypes.APPLICATION_M3U8) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(1024 * 1024).createMediaSource(item)
        }
    }

    /**
     * Choose the ExoPlayer container from the URL first, declared type second. Cloud providers
     * often omit `type`, which upstream defaulted to "hls" — forcing the HLS parser on a plain
     * .mp4/direct URL threw "Input does not start with #EXTM3U". Trust the URL extension; only
     * when it's unknown AND the declared type is a manifest that the URL contradicts do we drop
     * to null so ExoPlayer sniffs the real content instead of forcing a wrong parser.
     */
    private fun resolveMime(url: String, declared: String?): String? {
        val u = url.substringBefore('?').lowercase()
        val resolved = when {
            u.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            u.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            u.contains(".mp4") -> MimeTypes.VIDEO_MP4
            u.contains(".mkv") || u.contains(".webm") -> MimeTypes.VIDEO_WEBM
            // Declared a manifest but the URL is clearly not one → let ExoPlayer sniff.
            declared == MimeTypes.APPLICATION_M3U8 || declared == MimeTypes.APPLICATION_MPD -> null
            else -> declared
        }
        android.util.Log.i("PlayerSrc", "mime: url=$u declared=$declared -> $resolved")
        return resolved
    }

    @OptIn(UnstableApi::class)
    private fun playNewEpisode(videoUrl: String, headers: Map<String, String>) {
        initializeVideo(headers)

        if (::skipIntroView.isInitialized) {
            skipIntroView.resetSkippedTimestamps()
        }

        resetCountdownState()
        stopProgressTracking()

        player.stop()
        player.clearMediaItems()

        val mime = model.videoOptions.getOrNull(model.currentSelectedVideoOptionIndex)?.mimeTypes
        val mediaSource = createMediaSource(effectiveStreamUrl(videoUrl), mime)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun playQualityVideo(
        videoUrl: String, headers: Map<String, String>, mimeType: String? = null
    ) {
        initializeVideo(headers)

        resetCountdownState()
        val resumePos = player.currentPosition.coerceAtLeast(0L)
        stopProgressTracking()

        player.stop()
        player.clearMediaItems()

        val mediaSource = createMediaSource(
            effectiveStreamUrl(videoUrl),
            mimeType
                ?: model.seriesResponse?.type
        )
        player.setMediaSource(mediaSource)
        // Track groups belong to the stream that declared them, so a pinned rendition cannot
        // survive a reload. Back to auto rather than to a stale override.
        selectedNativeQuality = null
        selectedAudio = null
        autoFallbackTries = 0
        selectedText = null
        applyNativeQuality()
        // Speed is the user's, not the stream's, so it is deliberately kept.
        if (playbackSpeed != 1.0f) player.setPlaybackSpeed(playbackSpeed)
        player.prepare()
        player.seekTo(resumePos)
        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun showNativeQualityDialog(variants: List<NativeQualities.Variant>) {
        val rows = buildList {
            add(
                VideoServersAdapter.ServerRow(
                    name = getString(R.string.quality_auto),
                    qualities = getString(R.string.quality_auto_hint),
                )
            )
            variants.forEach {
                add(
                    VideoServersAdapter.ServerRow(
                        name = "${it.height}p",
                        qualities = NativeQualities.bitrateLabel(it.bitrate),
                    )
                )
            }
        }
        // By identity, not by height: an adaptive stream routinely carries the same
        // resolution at two bitrates, and matching on height alone put the tick on
        // whichever came first.
        val selected = selectedNativeQuality
            ?.let { variants.indexOf(it) + 1 }
            ?.coerceAtLeast(0) ?: 0

        VideoServerDialog(
            rows,
            selected,
            titleRes = R.string.player_quality_title,
            subtitleRes = R.string.player_quality_subtitle,
        ).apply {
            setOnRowPicked { index ->
                selectedNativeQuality = if (index == 0) null else variants.getOrNull(index - 1)
                applyNativeQuality()
            }
        }.show(parentFragmentManager, "NativeQualityDialog")
    }

    /**
     * Picks the audio track.
     *
     * No "auto" entry: unlike video quality there is nothing to adapt to — the
     * player just follows the preferred-language list, which is a guess the user
     * is now overruling. Listing the tracks and marking the current one is the
     * whole of it.
     */

    /**
     * What to do when playback dies.
     *
     * It used to log, notify Bugsnag, and stop — leaving a black screen with no
     * explanation and no way forward. The most common cause on TV boxes is a
     * codec the device does not have: an HEVC release plays on one stick and
     * not on the next, and ExoPlayer says so precisely
     * (format_supported=NO_EXCEEDS_CAPABILITIES) before failing.
     *
     * Another source usually carries the same episode in a format this box CAN
     * decode, so the first response is to move to one rather than to ask the
     * user to. Only when there is nothing left to try does it explain itself.
     */
    @OptIn(UnstableApi::class)
    private fun handlePlaybackError(error: PlaybackException) {
        val options = model.videoOptionsData.value.orEmpty()
        val decodeProblem = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

        val next = model.currentSelectedVideoOptionIndex + 1
        if (decodeProblem && next in options.indices && autoFallbackTries < MAX_AUTO_FALLBACK) {
            autoFallbackTries++
            model.currentSelectedVideoOptionIndex = next
            ignoreNextEpisodeSuccess = true
            snackString(getString(R.string.player_switching_source), requireActivity())
            model.updateQualityByIndex()
            return
        }

        val message = when {
            decodeProblem -> getString(R.string.player_error_codec)
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                getString(R.string.player_error_network)
            else -> getString(R.string.player_error_generic)
        }
        snackString(message, requireActivity())
    }

    /** Reset per episode: a new stream deserves its own budget of attempts. */
    private var autoFallbackTries = 0

    private val MAX_AUTO_FALLBACK = 2

    @OptIn(UnstableApi::class)
    private fun showAudioDialog() {
        val options = NativeTracks.audio(player.currentTracks)
        if (options.isEmpty()) return

        val rows = options.map {
            VideoServersAdapter.ServerRow(name = it.label, qualities = it.detail)
        }
        val current = selectedAudio?.let { chosen ->
            options.indexOfFirst { it.label == chosen.label && it.detail == chosen.detail }
        } ?: options.indexOfFirst { isPlaying(it) }

        VideoServerDialog(
            rows,
            current.coerceAtLeast(0),
            titleRes = R.string.player_audio_title,
            subtitleRes = R.string.player_audio_subtitle,
        ).apply {
            setOnRowPicked { index ->
                selectedAudio = options.getOrNull(index)
                applyTrack(C.TRACK_TYPE_AUDIO, selectedAudio)
            }
        }.show(parentFragmentManager, "AudioTrackDialog")
    }

    /** Which track the player settled on, so the tick starts in the right place. */
    @OptIn(UnstableApi::class)
    private fun isPlaying(option: NativeTracks.Option): Boolean =
        runCatching { option.group.isTrackSelected(option.index) }.getOrDefault(false)

    /**
     * Playback speed.
     *
     * Native to the player, so it costs nothing and survives a track change —
     * and on a dubbed catalogue where a lot of the audio is slow, it is asked
     * for more than most of what is already here.
     */
    private fun showSpeedDialog() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val rows = speeds.map {
            VideoServersAdapter.ServerRow(
                name = if (it == 1.0f) getString(R.string.player_speed_normal) else "${it}x",
                qualities = "",
            )
        }
        VideoServerDialog(
            rows,
            speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0),
            titleRes = R.string.player_speed_title,
            subtitleRes = R.string.player_speed_subtitle,
        ).apply {
            setOnRowPicked { index ->
                playbackSpeed = speeds.getOrNull(index) ?: 1.0f
                if (::player.isInitialized) player.setPlaybackSpeed(playbackSpeed)
            }
        }.show(parentFragmentManager, "PlaybackSpeedDialog")
    }

    /**
     * Pins one track of [type], or hands the choice back to the player.
     *
     * Overrides are cleared by type rather than wholesale, so choosing an audio
     * track cannot silently undo a pinned resolution.
     */
    @OptIn(UnstableApi::class)
    private fun applyTrack(type: Int, option: NativeTracks.Option?) {
        val selector = trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(type)
                // A text override means subtitles are wanted; without this the
                // renderer stays disabled and the track is chosen but silent.
                .setTrackTypeDisabled(type, false)
                .apply { option?.let { addOverride(NativeTracks.overrideFor(it)) } }
        )
    }

    /** The extractor's own subtitle files, if this episode came with any. */
    private var extractorSubtitles: List<SubTitle> = emptyList()

    @OptIn(UnstableApi::class)
    private fun refreshSubtitleButton() {
        if (!::player.isInitialized) return
        val b = _binding ?: return
        val embedded = NativeTracks.text(player.currentTracks)
        b.pvPlayer.controller.binding.exoSubtidtle.isVisible =
            extractorSubtitles.isNotEmpty() || embedded.isNotEmpty()
    }

    /**
     * The subtitle tracks inside the stream, plus off.
     *
     * Separate from the extractor's dialog because the two are not the same
     * operation: one is a track override the player applies instantly, the
     * other rebuilds the media source around a downloaded file.
     */
    @OptIn(UnstableApi::class)
    private fun showEmbeddedSubtitleDialog() {
        val options = NativeTracks.text(player.currentTracks)
        if (options.isEmpty()) return

        val rows = buildList {
            add(
                VideoServersAdapter.ServerRow(
                    name = getString(R.string.off),
                    qualities = getString(R.string.player_subtitle_off_hint),
                )
            )
            options.forEach { add(VideoServersAdapter.ServerRow(it.label, it.detail)) }
        }
        val current = selectedText
            ?.let { chosen -> options.indexOfFirst { it.label == chosen.label } + 1 }
            ?.coerceAtLeast(0) ?: 0

        VideoServerDialog(
            rows,
            current,
            titleRes = R.string.player_subtitle_title,
            subtitleRes = R.string.player_subtitle_subtitle,
        ).apply {
            setOnRowPicked { index ->
                selectedText = if (index == 0) null else options.getOrNull(index - 1)
                val selector = trackSelector
                if (index == 0 && selector != null) {
                    // Off means off: clearing the override alone would let the
                    // player pick a track again on the next selection pass.
                    selector.setParameters(
                        selector.buildUponParameters()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    )
                } else {
                    applyTrack(C.TRACK_TYPE_TEXT, selectedText)
                }
                binding.pvPlayer.subtitleView?.visibility =
                    if (index == 0) View.GONE else View.VISIBLE
                binding.pvPlayer.controller.binding.exoSubtitlee.setImageResource(
                    if (index == 0) R.drawable.ic_subtitle else R.drawable.ic_subtitle_fill
                )
            }
        }.show(parentFragmentManager, "EmbeddedSubtitleDialog")
    }

    @OptIn(UnstableApi::class)
    private fun applyNativeQuality() {
        val selector = trackSelector ?: return
        val variant = selectedNativeQuality
        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .apply { variant?.let { addOverride(NativeQualities.overrideFor(it)) } }
        )
    }

    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val vod = model.seriesResponse ?: return@launch

            val videoUrl = effectiveStreamUrl(vod.urlobj)
            val subtitles = vod.subtitleList.orEmpty()
            var isSubtitleHave = subtitles.isNotEmpty()
            val useSubtitles = isSubtitleHave

            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L

            // Episode controls are shown for history playback as well. Hiding
            // them was the second half of the same restriction: a user who
            // resumed from History could neither auto-advance nor reach the
            // episode list, so a resumed show was a dead end.
            binding.pvPlayer.controller.binding.exoNextContainer.visible()
            binding.pvPlayer.controller.binding.exoPrevContainer.visible()
            binding.pvPlayer.controller.binding.epListContainer.visible()

            setupOrUpdatePreviewThumbnails(vod.thumbnail, vod.header)

            val finalSource = withContext(Dispatchers.IO) {
                buildMediaSourceWithSubtitle(videoUrl, useSubtitles)
            }

            applySubtitleStyleToPlayer(binding.pvPlayer, PreferenceManager())

            player.setMediaSource(finalSource)
            player.prepare()

            if (!model.doNotAsk) {
                if (lastPosition > 0) player.seekTo(lastPosition)
            } else {
                player.seekTo(model.lastPosition)
            }

            player.play()

            // Visible when EITHER kind exists. It used to key off the extractor's
            // list alone, so a stream carrying its own subtitle tracks showed no
            // subtitle button at all — the tracks were parsed, selectable, and
            // unreachable.
            extractorSubtitles = subtitles
            refreshSubtitleButton()

            binding.pvPlayer.controller.binding.exoAudio.setOnClickListener { showAudioDialog() }
            binding.pvPlayer.controller.binding.exoSpeed.setOnClickListener { showSpeedDialog() }

            binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(
                if (player.isPlaying) R.drawable.anim_play_to_pause else R.drawable.anim_pause_to_play
            )

            binding.pvPlayer.controller.binding.exoSubtidtle.setOnClickListener {
                // A stream's own subtitle tracks need no reload — they are
                // already there, and picking one is a track override. Only the
                // extractor's separate files require rebuilding the source, so
                // that path is left exactly as it was.
                if (subtitles.isEmpty()) {
                    showEmbeddedSubtitleDialog()
                    return@setOnClickListener
                }
                val currentSelected = subtitles.getOrNull(model.currentSubEpIndex)
                val dialog =
                    SubtitleChooserDialog.newInstance(subtitles, currentSelected, isSubtitleHave)

                dialog.setSubtitleSelectionListener { selectedSubtitle ->
                    val enabled = selectedSubtitle?.file?.isNotEmpty() == true

                    val newIndex = if (enabled) subtitles.indexOf(selectedSubtitle) else -1
                    if (model.currentSubEpIndex == newIndex) return@setSubtitleSelectionListener

                    model.currentSubEpIndex = newIndex

                    binding.pvPlayer.subtitleView?.visibility =
                        if (enabled) View.VISIBLE else View.GONE
                    binding.pvPlayer.controller.binding.exoSubtitlee.setImageResource(
                        if (enabled) R.drawable.ic_subtitle_fill else R.drawable.ic_subtitle
                    )

                    val previousPos = player.currentPosition
                    player.pause()

                    isSubtitleHave = enabled

                    lifecycleScope.launch {
                        val newSource = withContext(Dispatchers.IO) {
                            buildMediaSourceWithSubtitle(videoUrl, isSubtitleHave)
                        }
                        player.setMediaSource(newSource)
                        player.prepare()
                        player.seekTo(previousPos)
                        player.play()
                    }
                }
                dialog.setOnSubtitleStyleChangedListener {
                    applySubtitleStyleToPlayer(
                        binding.pvPlayer,
                        PreferenceManager(requireContext())
                    )
                }

                dialog.show(parentFragmentManager, "subtitle_chooser")
            }

            if (model.isWatched && model.getWatchedHistoryEntity != null && model.getWatchedHistoryEntity!!.lastPosition > 0 && !model.doNotAsk) {
                player.pause()
                val dialog = AlertPlayerDialog(model.getWatchedHistoryEntity!!)
                dialog.setNoClearListener {
                    lifecycleScope.launch {
                        dialog.dismiss()
                        model.removeHistory(args.id)
                        withContext(Dispatchers.Main) {
                            player.seekTo(0)
                            player.play()
                        }
                    }
                }
                dialog.setYesContinueListener {
                    dialog.dismiss()
                    player.play()
                }
                dialog.show(parentFragmentManager, "ConfirmationDialog")
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun buildMediaSourceWithSubtitle(
        videoUrl: String,
        useSubtitles: Boolean
    ): MediaSource {

        val vod = model.seriesResponse
        val mime = resolveMime(videoUrl, vod?.type)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(videoUrl)
            .setTag(args.name)
            .apply { mime?.let { setMimeType(it) } }

        if (useSubtitles) {
            val list = vod?.subtitleList.orEmpty()
            val idx = model.currentSubEpIndex

            if (idx in list.indices) {
                val subUrl = list[idx].file
                if (subUrl.isNotBlank()) {
                    // A failing/unreachable subtitle (HTTP 4xx, network error, bad body) must NOT
                    // crash playback — skip the subtitle and play the video without it.
                    try {
                        val client = okHttpClient ?: buildOkHttpClient(lastHeaders)
                        val tmp =
                            File(requireContext().cacheDir, "sub_${System.currentTimeMillis()}.vtt")

                        val request = Request.Builder()
                            .url(subUrl)
                            .header("User-Agent", SOZO_USER_AGENT)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            response.body!!.byteStream().use { input ->
                                tmp.outputStream().use { output -> input.copyTo(output) }
                            }
                        }

                        val text = tmp.readText()

                        val fixedFile = if (!text.startsWith("WEBVTT")) {
                            tmp.writeText("WEBVTT\n\n$text")
                            tmp
                        } else tmp

                        val subConfig = MediaItem.SubtitleConfiguration.Builder(
                            Uri.fromFile(fixedFile)
                        )
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()

                        mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "SeriesPlayerScreen",
                            "subtitle fetch failed, playing without it: ${e.message}"
                        )
                    }
                }
            }
        }

        val finalItem = mediaItemBuilder.build()

        return DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(finalItem)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun applySubtitleStyleToPlayer(playerView: PlayerView, prefs: PreferenceManager) {
        val subtitleView = playerView.subtitleView ?: return

        if (!prefs.isSubtitleCustom()) {
            subtitleView.setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
            return
        }

        val s = prefs.getSubtitleStyle()

        subtitleView.setStyle(
            CaptionStyleCompat(
                s.color,
                if (s.background) Color.argb(180, 0, 0, 0) else Color.TRANSPARENT,
                Color.TRANSPARENT,
                if (s.outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.BLACK,
                when (s.font) {
                    PreferenceManager.Font.DEFAULT -> null
                    PreferenceManager.Font.POPPINS -> ResourcesCompat.getFont(
                        playerView.context, R.font.poppins
                    )

                    PreferenceManager.Font.DAYS -> ResourcesCompat.getFont(
                        playerView.context, R.font.days
                    )

                    PreferenceManager.Font.MONO -> Typeface.MONOSPACE
                }
            )
        )

        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp.toFloat())
    }

    private fun setupOrUpdatePreviewThumbnails(vttUrl: String?, headers: Map<String, String>) {
        val url = vttUrl?.trim().orEmpty()
        val previewImage = binding.pvPlayer.controller.binding.exoThumbnail
        val timeBar =
            binding.pvPlayer.controller.findViewById<TrailerPlayerScreen.ExtendedTimeBar>(R.id.exo_progress)
                ?: return

        if (url.isEmpty()) {
            previewImage.visibility = View.GONE
            currentVttUrl = ""
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
                    if (timeBar is TrailerPlayerScreen.ExtendedTimeBar) {
                        previewImage.visibility = View.GONE
                        thumbFetchJob?.cancel()
                        thumbFetchJob = null
                    }
                }
            })
        }

        if (currentVttUrl != url || thumbLoader == null) {
            currentVttUrl = url
            val client = buildOkHttpClient(headers)
            thumbLoader?.clear()
            thumbLoader = VttSpriteThumbnailLoader(client, headers)

            thumbLoadJob?.cancel()
            thumbLoadJob = lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    if (url.endsWith(".jpg") || url.endsWith(".png")) {
                        val request = Request.Builder()
                            .url(url)
                            .apply { headers.forEach { (k, v) -> header(k, v) } }
                            .build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        response.close()

                        Log.d("VTT_THUMB", "jpg body first 100: ${body.take(100)}")
                        Log.d("VTT_THUMB", "starts with WEBVTT: ${body.trimStart().startsWith("WEBVTT")}")

                        if (body.trimStart().startsWith("WEBVTT")) {
                            val baseUrl = url.substringBeforeLast("/") + "/"
                            Log.d("VTT_THUMB", "baseUrl: $baseUrl")
                            thumbLoader?.loadVttFromContent(body, baseUrl)
                            Log.d("VTT_THUMB", "loaded: ${thumbLoader?.isLoaded()}")
                        } else {
                            Log.d("VTT_THUMB", "jpg is actual image, not VTT")
                        }
                    } else {
                        thumbLoader?.loadVtt(url)
                    }
                }.onFailure {
                    Log.e("VTT_THUMB", "loadVtt failed: ${it.message}", it)
                }
            }        }
    }
    private fun requestThumb(
        imageView: ImageView, timeBar: TrailerPlayerScreen.ExtendedTimeBar, position: Long
    ) {
        thumbFetchJob?.cancel()
        thumbFetchJob = lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { thumbLoader?.getThumbnail(position) }.getOrNull()
            }

            if (bmp != null) {
                imageView.setImageBitmap(bmp)
            } else {
                imageView.setImageDrawable(null)
            }

            positionThumb(imageView, timeBar, position)
        }
    }

    private fun positionThumb(
        imageView: ImageView, timeBar: TrailerPlayerScreen.ExtendedTimeBar, position: Long
    ) {
        var duration = player.duration.takeIf { it > 0 } ?: return
        val w = timeBar.width
        if (w <= 0) return

        val scrubberX = (position.toFloat() / duration) * w
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

    private suspend fun saveWatchHistory() {
        try {
            if (!::player.isInitialized) return

            if (player.duration <= 0 && player.currentPosition >= 100_000 && player.currentPosition >= player.duration - 50) return

            if (model.isWatched) {
                val getEpIndex = model.getWatchedHistoryEntity ?: return
                val series = model.seriesResponse ?: return

                val newEp = getEpIndex.copy(
                    totalDuration = player.duration,
                    imdbID = args.seriesMainId,
                    isEpisode = true,
                    epIndex = model.currentEpIndex,
                    lastPosition = player.currentPosition,
                    videoUrl = series.urlobj,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex,
                    providerId = getEpIndex.providerId
                        .ifBlank { extensionEngine.getActiveProvider().orEmpty() },
                    currentSourceName = getEpIndex.currentSourceName
                        .ifBlank { extensionEngine.getActiveProviderName().orEmpty() },
                )
                model.updateHistory(newEp)
                model.getWatchedHistoryEntity = null
            } else {
                if (episodeList.isEmpty() || model.currentEpIndex !in episodeList.indices) return
                val ep = episodeList[model.currentEpIndex]
                if (ep.session == null || ep.snapshot == null) return

                val historyBuild = WatchHistoryEntity(
                    ep.session ?: return,
                    "${ep.title} - Episode ${model.currentEpIndex + 1}",
                    mediaName = args.name,
                    ep.snapshot ?: return,
                    "",
                    args.seriesMainId,
                    "",
                    "",
                    "",
                    0.0,
                    args.currentPage,
                    "2024/01/01",
                    model.seriesResponse?.urlobj.toString(),
                    totalDuration = player.duration,
                    lastPosition = player.currentPosition,
                    imdbID = args.seriesMainId,
                    epIndex = model.currentEpIndex,
                    isEpisode = true,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex,
                    source = PreferenceManager().getString(LocalData.SOURCE),
                    providerId = extensionEngine.getActiveProvider().orEmpty(),
                    currentSourceName = extensionEngine.getActiveProviderName().orEmpty(),
                )
                model.addHistory(historyBuild)
            }
        } catch (e: Exception) {
            Log.e("SaveHistory", "saveWatchHistory error: ${e.message}", e)
        }
    }

    private fun toggleSidebarRight(show: Boolean) {
        val sidebar = binding.sidebarRight
        val epListContainer = binding.pvPlayer.controller.binding.epListContainer

        sidebar.post {
            val sidebarWidth = sidebar.width.toFloat()

            if (show) {
                sidebar.apply {
                    isVisible = true
                    translationX = sidebarWidth
                    animate().translationX(0f).setDuration(100)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .start()
                }

                binding.btnHideMenuRight.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isEnabled = true
                    requestFocus()
                }

                epListContainer.apply {
                    isFocusable = false
                    gone()
                }
            } else {
                sidebar.animate().translationX(sidebarWidth).setDuration(100)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
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
                binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()
            }
        }
    }

    @SuppressLint("SetTextI18n", "StringFormatMatches")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener { navigateBack() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.sidebarRight.isVisible) {
                        toggleSidebarRight(false)
                        return
                    }
                    if (isCountdownActive) {
                        binding.countdownOverlay.stopCountdown()
                        isCountdownActive = false
                        if (::player.isInitialized) player.play()
                        return
                    }
                    navigateBack()
                }
            })
        bindQualityObserversOnce()

        model.currentEpIndex = args.currentIndex

        model.getAllEpisodeByPage(
            args.currentPage, args.seriesMainId, ShowResponse(args.name, args.id, args.image)
        )

        binding.pvPlayer.controller.binding.filmTitle.text =
            "${args.name} - Episode ${model.currentEpIndex + 1}"

        model.allEpisodeData.observe(viewLifecycleOwner) {
            when (it) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.loadingText.text =
                        getString(R.string.part_are_episodes_loading, args.currentPage)
                    binding.pvPlayer.gone()
                }

                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    episodeList.clear()
                    episodeList.addAll(it.data.data ?: listOf())

                    model.loadWatched(args.id)
                    model.isWatchedLiveData.observe(viewLifecycleOwner) { history ->
                        model.getCurrentEpisodeVodAnime(
                            args.id, args.seriesMainId, history, episodeNum =
                            episodeList.getOrNull(model.currentEpIndex)?.episode ?: 0
                        )
                    }

                    model.currentEpisodeData.observe(viewLifecycleOwner) { epRes ->
                        when (epRes) {
                            Resource.Loading -> {
                                binding.loadingLayout.visible()
                                binding.pvPlayer.gone()
                                binding.loadingText.text =
                                    getString(R.string.episode_is_loading, model.currentEpIndex + 1)
                            }

                            is Resource.Success -> {
                                if (ignoreNextEpisodeSuccess) return@observe

                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()

                                binding.textView9.text = getString(
                                    R.string.part_episode, args.currentPage, episodeList.size
                                )

                                initializeVideo(headers = epRes.data.header)

                                displayVideo()

                                binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()

                                binding.pvPlayer.controller.binding.epListContainer.setOnClickListener {
                                    binding.episodeRv.scrollToPosition(model.currentEpIndex)
                                    toggleSidebarRight(true)
                                }
                                binding.btnHideMenuRight.setOnClickListener {
                                    toggleSidebarRight(false)
                                }

                                val episodeAdapter =
                                    EpisodePlayerAdapter(model.currentEpIndex, args.image)
                                episodeAdapter.submitList(episodeList)
                                binding.episodeRv.adapter = episodeAdapter

                                episodeAdapter.setOnEpisodeClick { position, _ ->
                                    toggleSidebarRight(false)
                                    if (position != model.currentEpIndex) {
                                        lifecycleScope.launch { saveWatchHistory() }

                                        model.doNotAsk = false
                                        model.currentEpIndex = position
                                        model.lastPosition = 0

                                        model.getCurrentEpisodeVodAnime(
                                            episodeList[position].session.toString(),
                                            args.seriesMainId,
                                            episodeNum = episodeList[position].episode ?: 0
                                        )

                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                            if (resource is Resource.Success) {
                                                val newUrl =
                                                    resource.data.urlobj ?: return@observeOnce
                                                playNewEpisode(
                                                    newUrl, headers = resource.data.header
                                                )

                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    getString(
                                                        R.string.current_episode,
                                                        args.name,
                                                        position + 1
                                                    )
                                            }
                                        }
                                    }
                                }

                                binding.pvPlayer.controller.binding.exoNextContainer.setOnClickListener {
                                    if (model.currentEpIndex < episodeList.size - 1) {
                                        lifecycleScope.launch { saveWatchHistory() }
                                        model.currentEpIndex += 1
                                        model.doNotAsk = false
                                        model.getCurrentEpisodeVodAnime(
                                            episodeList[model.currentEpIndex].session.toString(),
                                            args.seriesMainId,
                                            episodeNum = episodeList[model.currentEpIndex].episode
                                                ?: 0
                                        )

                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                                            if (resource is Resource.Success) {
                                                val newUrl = resource.data.urlobj
                                                playNewEpisode(
                                                    newUrl, headers = resource.data.header
                                                )
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    getString(
                                                        R.string.current_episode,
                                                        args.name,
                                                        model.currentEpIndex + 1
                                                    )
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            getString(R.string.this_is_the_last_episode),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                binding.pvPlayer.controller.binding.exoPrevContainer.setOnClickListener {
                                    if (model.currentEpIndex > 0) {
                                        lifecycleScope.launch {
                                            saveWatchHistory()
                                            withContext(Dispatchers.Main) {
                                                model.currentEpIndex -= 1
                                                model.doNotAsk = false
                                                model.lastPosition = 0

                                                model.getCurrentEpisodeVodAnime(
                                                    episodeList[model.currentEpIndex].session.toString(),
                                                    args.seriesMainId,
                                                    episodeNum = episodeList[model.currentEpIndex].episode
                                                        ?: 0
                                                )

                                                model.currentEpisodeData.observeOnce(
                                                    viewLifecycleOwner
                                                ) { resource ->
                                                    if (resource is Resource.Success) {
                                                        val newUrl = resource.data.urlobj
                                                        playNewEpisode(
                                                            newUrl, headers = resource.data.header
                                                        )
                                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                                            getString(
                                                                R.string.current_episode,
                                                                args.name,
                                                                model.currentEpIndex + 1
                                                            )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            getString(R.string.this_is_the_first_episode),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    override fun onDestroyView() {
        stopProgressTracking()
        model.syncHistory()

        thumbLoadJob?.cancel()
        thumbFetchJob?.cancel()
        thumbLoader?.clear()
        thumbLoader = null

        if (::skipIntroView.isInitialized) {
            skipIntroView.cleanup()
        }

        if (::player.isInitialized) {
            if (player.currentPosition > 10) {
                runBlocking { saveWatchHistory() }
            }
            player.release()
            if (::mediaSession.isInitialized) mediaSession.release()
        }

        _binding = null
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        stopProgressTracking()
        if (::player.isInitialized) {
            player.pause()
            lifecycleScope.launch { saveWatchHistory() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && player.isPlaying) {
            startProgressTracking()
        }
    }

    private companion object {
        const val ANILIST_WATCHED_FRACTION = 0.85
    }
}

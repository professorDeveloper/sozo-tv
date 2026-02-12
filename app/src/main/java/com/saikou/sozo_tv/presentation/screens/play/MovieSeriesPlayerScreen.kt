package com.saikou.sozo_tv.presentation.screens.play

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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bugsnag.android.Bugsnag
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodePlayerAdapter
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ContentControllerTvSeriesBinding
import com.saikou.sozo_tv.databinding.ImdbSeriesPlayerScreenBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.screens.play.dialog.SubtitleChooserDialog
import com.saikou.sozo_tv.presentation.viewmodel.PlayMovieViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.observeOnce
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import kotlin.math.max
import org.koin.androidx.viewmodel.ext.android.viewModel

class MovieSeriesPlayerScreen : Fragment() {

    private var _binding: ImdbSeriesPlayerScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var okHttpDataSourceFactory: OkHttpDataSource.Factory
    private lateinit var dataSourceFactory: DataSource.Factory

    private val model by viewModel<PlayMovieViewModel>()
    private lateinit var mediaSession: MediaSession
    private val args by navArgs<MovieSeriesPlayerScreenArgs>()

    private val episodeList = arrayListOf<Data>()
    private var countdownShown = false
    private var isCountdownActive = false

    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    private var canUseSubtitle = false

    private companion object {
        private const val SUBTITLE_OFFSET_MS = 0L
        private val TIME_LINE_REGEX =
            Regex("""^\s*(\d{1,2}:\d{2}:\d{2}[,\.]\d{3}|\d{2}:\d{2}[,\.]\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[,\.]\d{3}|\d{2}:\d{2}[,\.]\d{3})(.*)$""")
    }

    private val PlayerControlView.binding
        @OptIn(UnstableApi::class)
        get() = ContentControllerTvSeriesBinding.bind(this.findViewById(R.id.cl_exo_controller_tv))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ImdbSeriesPlayerScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun showNextEpisodeCountdown() {
        binding.apply {
            if (!LocalData.isHistoryItemClicked && !countdownShown) {
                val nextEpisodeIndex = model.currentEpIndex + 1
                if (nextEpisodeIndex < episodeList.size) {
                    countdownShown = true
                    isCountdownActive = true
                    countdownOverlay.startCountdown(
                        seconds = 10,
                        nextEpisode = nextEpisodeIndex + 1,
                        currentEpisode = model.currentEpIndex + 1,
                        title = args.name,
                        useEnglish = true,
                        onFinished = { playNextEpisodeAutomatically() },
                        onCancelled = {
                            isCountdownActive = false
                            player.play()
                        }
                    )
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
                    model.getCurrentEpisodeVodByImdb(
                        args.imdbId,
                        args.iframeLink,
                        args.iframeLink,
                        args.isMovie,
                        args.currentPage,
                        args.currentIndex,
                        args.tmdbId
                    )
                    model.currentEpisodeData.observeOnce(viewLifecycleOwner) { resource ->
                        if (resource is Resource.Success) {
                            val newUrl = resource.data.urlobj
                            playNewEpisode(newUrl, args.name)
                            binding.pvPlayer.controller.binding.filmTitle.text =
                                "${args.name} - Episode ${model.currentEpIndex + 1}"
                            resetCountdownState()
                        }
                    }
                }
            }
        }
    }

    private fun resetCountdownState() {
        countdownShown = false
        isCountdownActive = false
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
                    }
                }
                progressHandler?.postDelayed(this, 1000)
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    private fun navigateBack() {
        if (!isAdded) return
        if (LocalData.isHistoryItemClicked) {
            val intent = Intent(context, ProfileActivity::class.java)
            startActivity(intent)
            activity?.finish()
        } else {
            runCatching { findNavController().navigateUp() }.onFailure { it.printStackTrace() }
        }
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeVideo()

        binding.pvPlayer.controller.binding.frameBackButton.setOnClickListener { navigateBack() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateBack()
            }
        )

        model.currentEpIndex = args.currentIndex
        model.changeCurrentSource(args.currentSource)
        model.getAllEpisodeByImdb(args.imdbId, args.tmdbId, args.currentPage, args.isMovie, args.image)

        binding.pvPlayer.controller.binding.filmTitle.text =
            "${args.name} - Episode ${model.currentEpIndex + 1}"

        model.allEpisodeData.observe(viewLifecycleOwner) {
            when (it) {
                Resource.Loading -> {
                    binding.loadingLayout.visible()
                    binding.loadingText.text = "Part ${args.currentPage} are episodes loading..."
                    binding.pvPlayer.gone()
                }
                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    episodeList.clear()
                    episodeList.addAll(it.data.data ?: listOf())
                    model.getCurrentEpisodeVodByImdb(
                        args.imdbId,
                        args.iframeLink,
                        args.iframeLink,
                        args.isMovie,
                        args.currentPage,
                        args.currentEp,
                        args.tmdbId
                    )
                    model.currentEpisodeData.observe(viewLifecycleOwner) { epRes ->
                        when (epRes) {
                            Resource.Loading -> {
                                binding.loadingLayout.visible()
                                binding.pvPlayer.gone()
                                binding.loadingText.text =
                                    "Episode ${model.currentEpIndex + 1} is loading..."
                            }
                            is Resource.Success -> {
                                binding.loadingLayout.gone()
                                binding.pvPlayer.visible()
                                binding.textView9.text =
                                    "Part ${args.currentPage} • Episode ${episodeList.size}"
                                displayVideo()
                                binding.pvPlayer.controller.binding.exoQuality.gone()
                                binding.pvPlayer.controller.binding.exoSubtidtle.visible()
                                binding.pvPlayer.controller.binding.exoPlayPauseContainer.requestFocus()
                                binding.pvPlayer.controller.binding.epListContainer.setOnClickListener {
                                    binding.episodeRv.scrollToPosition(model.currentEpIndex)
                                    toggleSidebarRight(true)
                                }
                                binding.btnHideMenuRight.setOnClickListener { toggleSidebarRight(false) }
                                val episodeAdapter = EpisodePlayerAdapter(model.currentEpIndex, args.image)
                                episodeAdapter.submitList(episodeList)
                                binding.episodeRv.adapter = episodeAdapter
                                episodeAdapter.setOnEpisodeClick { position, data ->
                                    toggleSidebarRight(false)
                                    if (position != model.currentEpIndex) {
                                        lifecycleScope.launch { saveWatchHistory() }
                                        model.doNotAsk = false
                                        model.currentEpIndex = position
                                        model.lastPosition = 0
                                        model.getCurrentEpisodeVodByImdb(
                                            args.imdbId,
                                            episodeList[position].session.toString(),
                                            episodeList[position].session.toString(),
                                            args.isMovie,
                                            args.currentPage,
                                            data.episode ?: -1,
                                            args.tmdbId
                                        )
                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { r ->
                                            if (r is Resource.Success) {
                                                val newUrl = r.data.urlobj
                                                playNewEpisode(newUrl, args.name)
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    "${args.name} - Episode ${position + 1}"
                                            }
                                        }
                                    }
                                }
                                binding.pvPlayer.controller.binding.exoNextContainer.setOnClickListener {
                                    if (model.currentEpIndex < episodeList.size - 1) {
                                        lifecycleScope.launch { saveWatchHistory() }
                                        model.currentEpIndex += 1
                                        model.doNotAsk = false
                                        model.getCurrentEpisodeVodByImdb(
                                            args.imdbId,
                                            episodeList[model.currentEpIndex].session.toString(),
                                            episodeList[model.currentEpIndex].session.toString(),
                                            args.isMovie,
                                            args.currentPage,
                                            model.currentEpIndex + 1,
                                            args.tmdbId
                                        )
                                        model.currentEpisodeData.observeOnce(viewLifecycleOwner) { r ->
                                            if (r is Resource.Success) {
                                                val newUrl = r.data.urlobj
                                                playNewEpisode(newUrl, args.name)
                                                binding.pvPlayer.controller.binding.filmTitle.text =
                                                    "${args.name} - Episode ${model.currentEpIndex + 1}"
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "This is the last episode",
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
                                                model.getCurrentEpisodeVodByImdb(
                                                    args.imdbId,
                                                    episodeList[model.currentEpIndex].session.toString(),
                                                    episodeList[model.currentEpIndex].session.toString(),
                                                    args.isMovie,
                                                    args.currentPage,
                                                    model.currentEpIndex + 1,
                                                    args.tmdbId
                                                )
                                                model.currentEpisodeData.observeOnce(viewLifecycleOwner) { r ->
                                                    if (r is Resource.Success) {
                                                        val newUrl = r.data.urlobj
                                                        playNewEpisode(newUrl, args.name)
                                                        binding.pvPlayer.controller.binding.filmTitle.text =
                                                            "${args.name} - Episode ${model.currentEpIndex + 1}"
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "This is the first episode",
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

    private suspend fun saveWatchHistory() {
        try {
            if (!::player.isInitialized) return
            val dur = player.duration
            val pos = player.currentPosition
            if (dur > 0 && pos >= max(0L, dur - 50L)) return

            if (model.isWatched) {
                val getEpIndex = model.getWatchedHistoryEntity ?: return
                val series = model.seriesResponse ?: return
                val newEp = getEpIndex.copy(
                    totalDuration = player.duration,
                    imdbID = args.tmdbId.toString(),
                    isEpisode = true,
                    epIndex = model.currentEpIndex,
                    lastPosition = player.currentPosition,
                    videoUrl = series.urlobj,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex
                )
                model.updateHistory(newEp)
                model.getWatchedHistoryEntity = null
            } else {
                if (episodeList.isEmpty() || model.currentEpIndex !in episodeList.indices) return
                if (episodeList[model.currentEpIndex].session == null) return
                if (episodeList[model.currentEpIndex].snapshot == null) return
                val historyBuild = WatchHistoryEntity(
                    episodeList[model.currentEpIndex].session ?: return,
                    "${args.name} - Episode ${model.currentEpIndex + 1}",
                    mediaName = args.name,
                    episodeList[model.currentEpIndex].snapshot ?: return,
                    "",
                    args.tmdbId.toString(),
                    "",
                    "",
                    "",
                    0.0,
                    args.currentPage,
                    "2024/01/01",
                    model.seriesResponse?.urlobj.toString(),
                    currentSourceName = model.currentSource,
                    totalDuration = player.duration,
                    lastPosition = player.currentPosition,
                    imdbID = args.imdbId.toString(),
                    epIndex = model.currentEpIndex,
                    isEpisode = true,
                    currentQualityIndex = model.currentSelectedVideoOptionIndex,
                    isSeries = !args.isMovie,
                    isAnime = false
                )
                model.addHistory(historyBuild)
            }
        } catch (e: Exception) {
            Log.e("SaveHistory", "Exception in saveWatchHistory: ${e.message}", e)
        }
    }

    @SuppressLint("WrongConstant", "NewApi")
    @OptIn(UnstableApi::class)
    private fun initializeVideo() {
        if (::player.isInitialized) return

        okHttpClient = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .ignoreAllSSLErrors()
            .build()

        okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        dataSourceFactory = DefaultDataSource.Factory(requireContext(), okHttpDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        val mediaSourceFactory = DefaultMediaSourceFactory(requireContext(), extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        trackSelector = DefaultTrackSelector(requireContext())
        trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(false))

        val renderersFactory =
            DefaultRenderersFactory(requireContext())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 90_000, 2_500, 5_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build()

        val offloadPrefs = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(offloadPrefs)
                .build()

        player.volume = 1.0f
        player.playWhenReady = true
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )

        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSession.Builder(requireContext(), player).build()
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Bugsnag.notify(
                    Exception("GGMoviePlayer: | ${model.currentSource} | ${model.seriesResponse?.urlobj} | ${error.message}")
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (LocalData.isAnimeEnabled) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!LocalData.isHistoryItemClicked) {
                                resetCountdownState()
                                startProgressTracking()
                            }
                        }
                        Player.STATE_ENDED -> {
                            if (!LocalData.isHistoryItemClicked) {
                                stopProgressTracking()
                                if (!isCountdownActive) {
                                    playNextEpisodeAutomatically()
                                }
                            }
                        }
                    }
                }
            }
        })

        binding.pvPlayer.player = player

        binding.pvPlayer.controller.binding.exoNextTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }
        binding.pvPlayer.controller.binding.exoPrevTenContainer.setOnClickListener {
            player.seekTo(player.currentPosition - 10_000)
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

        model.videoOptionsData.observe(viewLifecycleOwner) { videoOptions ->
            binding.pvPlayer.controller.binding.exoQuality.setOnClickListener {
                val dialog = VideoQualityDialog(videoOptions, model.currentSelectedVideoOptionIndex)
                dialog.setYesContinueListener { _, i ->
                    if (i != model.currentSelectedVideoOptionIndex) {
                        model.currentSelectedVideoOptionIndex = i
                        model.updateQualityByIndex()
                    }
                }
                dialog.show(parentFragmentManager, "VideoQualityDialog")
            }
            model.currentQualityEpisode.observe(viewLifecycleOwner) { resource ->
                if (resource is Resource.Success) {
                    val newUrl = resource.data.urlobj
                    playQualityVideo(newUrl, args.name)
                }
            }
        }

        binding.pvPlayer.controller.findViewById<TrailerPlayerScreen.ExtendedTimeBar>(R.id.exo_progress)
            .setKeyTimeIncrement(10_000)
    }

    @OptIn(UnstableApi::class)
    private suspend fun buildMediaItemWithOptionalSubtitle(
        videoUrl: String,
        title: String,
        useSubtitle: Boolean
    ): MediaItem = withContext(Dispatchers.IO) {
        val mime = model.seriesResponse?.type?.takeIf { it.isNotBlank() }
        val builder = MediaItem.Builder()
            .setUri(videoUrl)
            .setTag(title)

        if (mime != null) builder.setMimeType(mime)

        if (!useSubtitle) return@withContext builder.build()

        return@withContext runCatching {
            val subs = model.seriesResponse?.subtitleList.orEmpty()
            val idx = model.currentSubEpIndex
            if (idx !in subs.indices) return@runCatching builder.build()
            val subUrl = subs[idx].file
            if (subUrl.isBlank()) return@runCatching builder.build()

            val encodingHint = Uri.parse(subUrl).getQueryParameter("encoding") ?: "UTF-8"
            val subMime = guessSubtitleMime(subUrl)
            val ext = if (subMime == MimeTypes.TEXT_VTT) "vtt" else "srt"
            val localFile = File(requireContext().cacheDir, "sub_${idx}_${subUrl.hashCode()}.$ext")

            val adjustedFile = downloadAndAdjustSubtitleGeneric(
                url = subUrl,
                headers = model.seriesResponse?.header,
                encodingHint = encodingHint,
                outFile = localFile,
                offsetMs = SUBTITLE_OFFSET_MS,
                isVtt = subMime == MimeTypes.TEXT_VTT
            )

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(adjustedFile))
                .setMimeType(subMime)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            builder.setSubtitleConfigurations(listOf(subtitleConfig))
            builder.build()
        }.getOrElse { builder.build() }
    }

    private fun guessSubtitleMime(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains(".vtt") || u.contains("text/vtt") || u.contains("webvtt") -> MimeTypes.TEXT_VTT
            u.contains(".ssa") || u.contains(".ass") -> MimeTypes.TEXT_SSA
            u.contains(".ttml") || u.contains(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun downloadAndAdjustSubtitleGeneric(
        url: String,
        headers: Map<String, String>?,
        encodingHint: String,
        outFile: File,
        offsetMs: Long,
        isVtt: Boolean
    ): File {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }

        val bytes = okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subtitle HTTP ${response.code}")
            response.body.bytes()
        }

        val charset = when (encodingHint.uppercase()) {
            "CP1252", "WINDOWS-1252", "WIN1252" -> Charset.forName("windows-1252")
            "UTF-8", "UTF8" -> Charsets.UTF_8
            else -> runCatching { Charset.forName(encodingHint) }.getOrDefault(Charsets.UTF_8)
        }

        val content = bytes.toString(charset)
        val adjustedContent = if (offsetMs == 0L) {
            content
        } else {
            shiftSubtitleTimings(content, offsetMs, isVtt)
        }

        outFile.writeText(adjustedContent, Charsets.UTF_8)
        return outFile
    }

    private fun shiftSubtitleTimings(
        content: String,
        offsetMs: Long,
        isVtt: Boolean
    ): String {
        val sb = StringBuilder()
        val lines = content.lines()
        for (line in lines) {
            val m = TIME_LINE_REGEX.matchEntire(line)
            if (m != null) {
                val start = m.groupValues[1]
                val end = m.groupValues[2]
                val tail = m.groupValues[3]
                val startMs = parseSubtitleTimeMs(start)
                val endMs = parseSubtitleTimeMs(end)
                val newStart = max(0L, startMs + offsetMs)
                val newEnd = max(newStart + 100L, endMs + offsetMs)
                val s = formatSubtitleTimeMs(newStart, isVtt)
                val e = formatSubtitleTimeMs(newEnd, isVtt)
                sb.append(s).append(" --> ").append(e).append(tail).append("\n")
            } else {
                sb.append(line).append("\n")
            }
        }
        return sb.toString()
    }

    private fun parseSubtitleTimeMs(time: String): Long {
        val t = time.trim()
        val parts = t.split(":")
        val (h, m, secPart) = when (parts.size) {
            3 -> Triple(parts[0].toLongOrNull() ?: 0L, parts[1].toLongOrNull() ?: 0L, parts[2])
            2 -> Triple(0L, parts[0].toLongOrNull() ?: 0L, parts[1])
            else -> Triple(0L, 0L, t)
        }
        val secMs = secPart.split(',', '.')
        val s = secMs.getOrNull(0)?.toLongOrNull() ?: 0L
        val ms = secMs.getOrNull(1)?.toLongOrNull() ?: 0L
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun formatSubtitleTimeMs(ms: Long, isVtt: Boolean): String {
        val hours = ms / 3_600_000L
        val minutes = (ms % 3_600_000L) / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        val millis = ms % 1_000L
        val sep = if (isVtt) "." else ","
        return String.format("%02d:%02d:%02d%s%03d", hours, minutes, seconds, sep, millis)
    }

    @OptIn(UnstableApi::class)
    private fun playNewEpisode(videoUrl: String, title: String) {
        if (!::player.isInitialized) initializeVideo()
        applyHeaders(model.seriesResponse?.header)
        resetCountdownState()
        stopProgressTracking()
        player.stop()
        player.clearMediaItems()
        lifecycleScope.launch {
            val subtitles = model.seriesResponse?.subtitleList.orEmpty()
            val subtitleEnabled =
                subtitles.isNotEmpty() && model.currentSubEpIndex in subtitles.indices && canUseSubtitle
            val item = buildMediaItemWithOptionalSubtitle(videoUrl, title, subtitleEnabled)
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    @OptIn(UnstableApi::class)
    private fun playQualityVideo(videoUrl: String, title: String) {
        if (!::player.isInitialized) initializeVideo()
        applyHeaders(model.seriesResponse?.header)
        resetCountdownState()
        model.qualityProgress = player.currentPosition
        stopProgressTracking()
        player.stop()
        player.clearMediaItems()
        val resumePos = model.qualityProgress
        lifecycleScope.launch {
            val subtitles = model.seriesResponse?.subtitleList.orEmpty()
            val subtitleEnabled =
                subtitles.isNotEmpty() && model.currentSubEpIndex in subtitles.indices && canUseSubtitle
            val item = buildMediaItemWithOptionalSubtitle(videoUrl, title, subtitleEnabled)
            player.setMediaItem(item)
            player.prepare()
            player.seekTo(resumePos)
            player.play()
            model.qualityProgress = 0
        }
    }

    @OptIn(UnstableApi::class)
    private fun displayVideo() {
        lifecycleScope.launch {
            val subtitles = model.seriesResponse?.subtitleList.orEmpty()
            val videoUrl = model.seriesResponse!!.urlobj
            val lastPosition = model.getWatchedHistoryEntity?.lastPosition ?: 0L
            binding.pvPlayer.subtitleView?.visible()

            if (LocalData.isHistoryItemClicked) {
                binding.pvPlayer.controller.binding.exoNextContainer.gone()
                binding.pvPlayer.controller.binding.exoPrevContainer.gone()
                binding.pvPlayer.controller.binding.epListContainer.gone()
            } else {
                binding.pvPlayer.controller.binding.exoNextContainer.visible()
                binding.pvPlayer.controller.binding.exoPrevContainer.visible()
                binding.pvPlayer.controller.binding.epListContainer.visible()
            }

            applyHeaders(model.seriesResponse?.header)
            val subtitleEnabled = subtitles.isNotEmpty() && model.currentSubEpIndex in subtitles.indices
            canUseSubtitle = subtitleEnabled

            val mediaItem = buildMediaItemWithOptionalSubtitle(
                videoUrl = videoUrl,
                title = args.name,
                useSubtitle = subtitleEnabled
            )

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            applySubtitleStyleToPlayer(binding.pvPlayer, PreferenceManager(requireContext()))

            if (!model.doNotAsk) {
                if (lastPosition > 0) player.seekTo(lastPosition)
            } else {
                player.seekTo(model.lastPosition)
            }

            player.play()

            binding.pvPlayer.controller.binding.exoSubtitlee.setImageResource(
                if (subtitleEnabled) R.drawable.ic_subtitle_fill else R.drawable.ic_subtitle_off
            )

            binding.pvPlayer.controller.binding.exoSubtidtle.setOnClickListener {
                val currentSelected = subtitles.getOrNull(model.currentSubEpIndex)
                val dialog = SubtitleChooserDialog.newInstance(subtitles, currentSelected, subtitleEnabled)
                dialog.setSubtitleSelectionListener { selectedSubtitle ->
                    val enabledNow = selectedSubtitle?.file?.isNotEmpty() == true
                    val newIndex = if (enabledNow) subtitles.indexOf(selectedSubtitle) else -1
                    if (model.currentSubEpIndex == newIndex) return@setSubtitleSelectionListener
                    model.currentSubEpIndex = newIndex
                    canUseSubtitle = enabledNow
                    binding.pvPlayer.subtitleView?.visibility = if (enabledNow) View.VISIBLE else View.GONE
                    binding.pvPlayer.controller.binding.exoSubtitlee.setImageResource(
                        if (enabledNow) R.drawable.ic_subtitle_fill else R.drawable.ic_subtitle
                    )
                    val previousPos = player.currentPosition
                    player.pause()
                    lifecycleScope.launch {
                        val item = buildMediaItemWithOptionalSubtitle(
                            videoUrl = videoUrl,
                            title = args.name,
                            useSubtitle = enabledNow
                        )
                        player.setMediaItem(item)
                        player.prepare()
                        player.seekTo(previousPos)
                        player.play()
                    }
                }
                dialog.setOnSubtitleStyleChangedListener {
                    applySubtitleStyleToPlayer(binding.pvPlayer, PreferenceManager(requireContext()))
                }
                dialog.show(parentFragmentManager, "subtitle_chooser")
            }

            if (player.isPlaying) {
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_play_to_pause)
            } else {
                binding.pvPlayer.controller.binding.exoPlayPaused.setImageResource(R.drawable.anim_pause_to_play)
            }

            if (model.isWatched && model.getWatchedHistoryEntity != null
                && model.getWatchedHistoryEntity!!.lastPosition > 0 && !model.doNotAsk
            ) {
                player.pause()
                val dialog = AlertPlayerDialog(model.getWatchedHistoryEntity!!)
                dialog.setNoClearListener {
                    lifecycleScope.launch {
                        dialog.dismiss()
                        model.removeHistory(args.iframeLink)
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

    @OptIn(UnstableApi::class)
    private fun applyHeaders(headers: Map<String, String>?) {
        if (!::okHttpDataSourceFactory.isInitialized) return
        if (headers.isNullOrEmpty()) return
        okHttpDataSourceFactory.setDefaultRequestProperties(headers)
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
                    PreferenceManager.Font.POPPINS -> ResourcesCompat.getFont(playerView.context, R.font.poppins)
                    PreferenceManager.Font.DAYS -> ResourcesCompat.getFont(playerView.context, R.font.days)
                    PreferenceManager.Font.MONO -> Typeface.MONOSPACE
                }
            )
        )
        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp.toFloat())
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
                    .withEndAction { sidebar.isVisible = false }
                    .start()
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

    override fun onDestroyView() {
        stopProgressTracking()
        if (::player.isInitialized) {
            if (player.currentPosition > 10) {
                runBlocking { saveWatchHistory() }
            }
        }
        if (::player.isInitialized) {
            player.release()
            mediaSession.release()
        }
        _binding = null
        super.onDestroyView()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        stopProgressTracking()
        if (::player.isInitialized && player.currentPosition > 10) {
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
}

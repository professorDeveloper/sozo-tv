package com.saikou.sozo_tv.presentation.activities

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.saikou.sozo_tv.data.extensions.*
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.remote.party.*
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.engine.player.*
import com.saikou.sozo_tv.engine.torrent.TorrentEngine
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.koin.android.ext.android.get
import org.koin.core.qualifier.named
import kotlin.math.abs

/** A room resolves identity independently on this TV; no signed stream URL leaves the device. */
@androidx.media3.common.util.UnstableApi
class WatchPartyActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var roomTitle: TextView
    private lateinit var chatLine: TextView
    private lateinit var lobby: LinearLayout
    private lateinit var roomControls: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var client: WatchPartyClient
    private lateinit var player: ExoPlayer
    private val streamHttp by lazy { StreamHttp(this) }
    private val proxy by lazy { LocalHlsProxy(streamHttp.proxyClient) }
    private val torrent by lazy { TorrentEngine(this) }
    private var torrentHash: String? = null
    private var room = JSONObject()
    private var contentKey = ""
    private var sourceIndex = 0
    private var sources: List<ExtVideoSource> = emptyList()
    private var media: ExtMedia? = null
    private var playback = PartyPlayback(0, false, 1f, 0)
    private var resolveJob: Job? = null
    private var heartbeat: Job? = null
    private var generation = 0
    private var stopped = false
    private var incomingContent: JSONObject? = null
    private var sentIncoming = false
    private val controlButtons = mutableListOf<Button>()
    private val userId get() = get<DeviceSessionStore>().current()?.userId
    private val isHost get() = room.optString("hostUserId") == userId
    private val canControl get() = isHost || !room.optJSONObject("settings").let { it?.optBoolean("onlyHostControls", true) ?: true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val initialContent = runCatching { contentFromIntent() }
        incomingContent = initialContent.getOrNull()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(36, 22, 36, 20); setBackgroundColor(Color.rgb(16, 18, 24))
        }
        roomTitle = label("Watch Party", 26f); root.addView(roomTitle)
        status = label(initialContent.exceptionOrNull()?.message ?: "Join with a room code, or create a room from the video player.", 17f); root.addView(status)
        lobby = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val code = EditText(this).apply { hint = "Room code"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); filters = arrayOf(android.text.InputFilter.LengthFilter(12)); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        lobby.addView(code, LinearLayout.LayoutParams(270, 64))
        lobby.addView(button("Join") { guarded { client.join(code.text.toString()) } })
        lobby.addView(button("Create room") { guarded { val selected = requireNotNull(incomingContent) { "Open a video and choose Watch Party to create a room" }; ensureAllowed(selected); client.create(selected) } }.apply { isEnabled = incomingContent != null })
        root.addView(lobby)
        playerView = PlayerView(this).apply { useController = false; isFocusable = false }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        roomControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE }
        listOf("−10s" to "back", "Play / Pause" to "toggle", "+10s" to "forward").forEach { (text, action) ->
            val b = button(text) {
                if (canControl) {
                    val position = player.currentPosition.coerceAtLeast(0)
                    val target = when (action) { "back" -> (position - 10000).coerceAtLeast(0); "forward" -> position + 10000; else -> position }
                    client.emit("party:control", JSONObject().put("action", if (action == "toggle") { if (player.playWhenReady) "pause" else "play" } else "seek").put("positionSec", target / 1000.0))
                }
            }; controlButtons.add(b); roomControls.addView(b)
        }
        roomControls.addView(button("Source") { if (sources.isNotEmpty()) { sourceIndex = (sourceIndex + 1) % sources.size; prepareSource() } })
        roomControls.addView(button("Leave") { leaveRoom() })
        roomControls.addView(button("Close room") { guarded { check(isHost) { "Only the host can close this room" }; client.closeRoom(); leaveRoom() } })
        root.addView(roomControls)
        val social = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        social.addView(button("Tracks") { showTracks() })
        social.addView(button("Message") {
            if (client.code != null) {
                val input = EditText(this).apply { hint = "Message"; filters = arrayOf(android.text.InputFilter.LengthFilter(500)) }
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Room message").setView(input)
                    .setPositiveButton("Send") { _, _ -> client.emit("party:chat", JSONObject().put("text", input.text.toString()).put("clientId", java.util.UUID.randomUUID().toString())) }
                    .setNegativeButton("Cancel", null).show()
            }
        })
        social.addView(button("♥") { client.emit("party:reaction", JSONObject().put("emoji", "❤️")) })
        chatLine = label("", 16f); social.addView(chatLine, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(social)
        setContentView(root)
        player = ExoPlayer.Builder(this).build(); playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (sourceIndex + 1 < sources.size) { sourceIndex++; prepareSource() }
                else status.text = "Playback failed: ${error.errorCodeName}. Choose Source or rejoin."
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) applyPlayback() }
        })
        val auth = get<DeviceAuthRepository>()
        client = WatchPartyClient({ force -> if (force) auth.refreshNow() else auth.accessToken() }, get<OkHttpClient>(named("authOkHttp")), lifecycleScope, ::onEvent, onStatus = { status.text = it })
        val originalUser = userId
        val originalProfile = get<ActiveProfileStore>().profileId()
        lifecycleScope.launch {
            get<DeviceSessionStore>().session.collect { if (it?.userId != originalUser) { leaveRoom(); status.text = "Account changed. Join again with the current account." } }
        }
        lifecycleScope.launch {
            get<ActiveProfileStore>().active.collect { if (it?.id != originalProfile) { leaveRoom(); status.text = "Profile changed. Join again from the active profile." } }
        }
        heartbeat = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                if (isHost && !stopped && player.playbackState == Player.STATE_READY) client.emit("party:heartbeat", JSONObject().put("positionSec", player.currentPosition / 1000.0))
            }
        }
    }

    private fun onEvent(event: String, payload: JSONObject) {
        when (event) {
            "party:state" -> {
                room = payload
                lobby.visibility = View.GONE; roomControls.visibility = View.VISIBLE
                updateMembers()
                acceptPlayback(payload.optJSONObject("playback"))
                val selected = incomingContent
                if (isHost && !sentIncoming && selected != null) {
                    sentIncoming = true; guarded { ensureAllowed(selected); client.emit("party:content", selected) }
                } else acceptContent(payload.optJSONObject("content"))
                controlButtons.firstOrNull { it.isEnabled }?.requestFocus()
            }
            "party:member" -> {
                room.put("members", payload.optJSONArray("members")).put("hostUserId", payload.optString("hostUserId")); updateMembers()
            }
            "party:sync" -> acceptPlayback(payload.optJSONObject("playback"))
            "party:content" -> { acceptPlayback(payload.optJSONObject("playback")); acceptContent(payload.optJSONObject("content")) }
            "party:closed" -> { leaveRoom(); status.text = "This room has closed." }
            "party:chat", "party:reaction" -> chatLine.text = "${payload.optJSONObject("user")?.optString("username", "Viewer")}: ${payload.optString("text").ifEmpty { payload.optString("emoji") }}"
            "party:error" -> status.text = payload.optString("message", "Room request failed")
        }
    }
    private fun updateMembers() {
        val members = room.optJSONArray("members")
        val online = (0 until (members?.length() ?: 0)).count { members?.optJSONObject(it)?.optBoolean("online") == true }
        roomTitle.text = "Room ${client.code.orEmpty()}  ·  $online watching  ·  ${if (isHost) "You are hosting" else "Following host"}"
        controlButtons.forEach { it.isEnabled = canControl }
    }
    private fun acceptPlayback(value: JSONObject?) {
        if (value == null) return
        playback = PartyPlayback.parse(value, SystemClock.elapsedRealtime()); applyPlayback()
    }
    private fun applyPlayback() {
        if (stopped) return
        val target = playback.expected(SystemClock.elapsedRealtime())
        if (abs(player.currentPosition - target) > 1500) player.seekTo(target)
        player.setPlaybackSpeed(playback.rate); player.playWhenReady = playback.playing
    }
    private fun acceptContent(content: JSONObject?) {
        if (content == null || content.optString("mediaRef").isBlank()) { status.text = "Waiting for the host to choose a video…"; return }
        val key = content.optString("provider") + "\u0000" + content.optString("mediaRef")
        if (key == contentKey) return
        contentKey = key; room.put("content", content); sources = emptyList(); player.stop()
        resolveJob?.cancel(); val stamp = ++generation
        resolveJob = lifecycleScope.launch {
            try {
                status.text = "Resolving ${content.optString("title", "video")}…"
                val provider = content.optString("provider").let { if (it.startsWith("cs:") || it.startsWith("an:") || it.startsWith("sv:")) it else "sv:$it" }
                ensureAllowed(content)
                val resolved = withTimeout(120_000) {
                    var ref = content.getString("mediaRef")
                    check(!ref.startsWith("direct::")) { "Room sent a device-specific stream. Ask the host to select the video again." }
                    if (ref == content.optString("contentUrl") && provider.startsWith("sv:")) {
                        val detail = ExtensionEngine.shared.load(provider, ref) ?: error("Could not reload this video's identity on the TV")
                        val ep = content.optInt("episode", -1)
                        ref = (detail.episodes.firstOrNull { it.episode == ep } ?: detail.episodes.singleOrNull())?.mediaRef
                            ?: error("This episode could not be matched on the TV")
                    }
                    ExtensionEngine.shared.loadLinks(provider, ref)
                }
                ensureActive(); if (stamp != generation) return@launch
                check(resolved.sources.isNotEmpty()) { "This source is not available on this TV. Install its provider in Sources." }
                media = resolved; sources = resolved.sources; sourceIndex = 0; prepareSource()
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { contentKey = ""; status.text = e.message ?: "Could not resolve the room video" }
        }
    }
    private fun prepareSource() {
        val source = sources.getOrNull(sourceIndex) ?: return
        resolveJob?.cancel(); val stamp = ++generation
        resolveJob = lifecycleScope.launch {
            try {
                status.text = "Loading ${source.quality}…"
                torrentHash?.let { torrent.drop(it) }; torrentHash = null
                var url = source.videoUrl
                var headers = media?.headers.orEmpty() + source.headers
                var type = source.type
                if (type == "torrent" || url.startsWith("magnet:")) {
                    val stream = torrent.resolve(url, room.optJSONObject("content")?.optInt("episode", -1)?.takeIf { it > 0 })
                    torrentHash = stream.hash; url = stream.url; type = "http"
                }
                if (source.useWebViewSniff || WebViewStreamExtractor.needsExtraction(url)) {
                    val sniffed = WebViewStreamExtractor.extract(this@WatchPartyActivity, url, headers) ?: error("Could not open the embedded player")
                    url = sniffed.url; headers = sniffed.headers; type = sniffed.playType
                }
                if (source.useLocalProxy || StreamRequestRules.needsManifestProxy(url)) {
                    url = withContext(Dispatchers.IO) { proxy.register(url, headers,
                        source.localProxy?.let(::JSONObject) ?: JSONObject(), source.requestTransform?.let(::JSONObject) ?: JSONObject()) }
                    headers = emptyMap()
                }
                ensureActive(); if (stamp != generation) return@launch
                streamHttp.headers = headers
                val mime = when (type) { "hls" -> MimeTypes.APPLICATION_M3U8; "dash" -> MimeTypes.APPLICATION_MPD; else -> null }
                val subtitles = media?.subtitles.orEmpty().map { sub ->
                    val subUrl = if (sub.headers.isEmpty()) sub.file else withContext(Dispatchers.IO) { proxy.register(sub.file, sub.headers, JSONObject(), JSONObject()) }
                    val subMime = when (sub.file.substringBefore('?').substringAfterLast('.').lowercase()) {
                        "srt" -> MimeTypes.APPLICATION_SUBRIP; "ass", "ssa" -> MimeTypes.TEXT_SSA; else -> MimeTypes.TEXT_VTT
                    }
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl)).setMimeType(subMime).setLabel(sub.label)
                        .setSelectionFlags(if (sub.default) C.SELECTION_FLAG_DEFAULT else 0).build()
                }
                val item = StreamDrm.configure(MediaItem.Builder().setUri(url).setMimeType(mime).setSubtitleConfigurations(subtitles), source.drm).build()
                val factory = StreamDrm.configure(DefaultMediaSourceFactory(streamHttp.dataSourceFactory), source.drm)
                val video = factory.createMediaSource(item)
                val audio = source.audioTracks.map { track ->
                    DefaultMediaSourceFactory(streamHttp.dataSourceFactoryFor(track.headers)).createMediaSource(MediaItem.fromUri(track.url))
                }
                player.setMediaSource(if (audio.isEmpty()) video else androidx.media3.exoplayer.source.MergingMediaSource(true, video, *audio.toTypedArray()))
                player.prepare(); applyPlayback()
                status.text = "${room.optJSONObject("content")?.optString("title").orEmpty()} · ${source.quality}"
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) {
                if (sourceIndex + 1 < sources.size) { sourceIndex++; prepareSource() }
                else status.text = e.message ?: "Could not play this source"
            }
        }
    }
    private fun showTracks() {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Playback tracks")
            .setItems(arrayOf("Audio", "Subtitles", "Video quality")) { _, kind ->
                if (kind == 2) {
                    val variants = com.saikou.sozo_tv.domain.player.NativeQualities.of(player.currentTracks)
                    val names = listOf("Auto") + variants.map { "${it.height}p · ${com.saikou.sozo_tv.domain.player.NativeQualities.bitrateLabel(it.bitrate)}" }
                    androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Video quality").setItems(names.toTypedArray()) { _, choice ->
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .apply { variants.getOrNull(choice - 1)?.let { addOverride(com.saikou.sozo_tv.domain.player.NativeQualities.overrideFor(it)) } }.build()
                    }.show()
                } else {
                    val type = if (kind == 0) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    val options = if (kind == 0) com.saikou.sozo_tv.domain.player.NativeTracks.audio(player.currentTracks)
                        else com.saikou.sozo_tv.domain.player.NativeTracks.text(player.currentTracks)
                    val names = listOf(if (kind == 0) "Auto" else "Off") + options.map { "${it.label} ${it.detail}" }
                    androidx.appcompat.app.AlertDialog.Builder(this).setTitle(if (kind == 0) "Audio" else "Subtitles").setItems(names.toTypedArray()) { _, choice ->
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
                            .setTrackTypeDisabled(type, kind == 1 && choice == 0)
                            .apply { options.getOrNull(choice - 1)?.let { addOverride(com.saikou.sozo_tv.domain.player.NativeTracks.overrideFor(it)) } }.build()
                    }.show()
                }
            }.show()
    }
    private fun leaveRoom() {
        client.leave(); generation++; resolveJob?.cancel(); player.stop(); contentKey = ""; room = JSONObject(); sentIncoming = false
        torrentHash?.let { hash -> lifecycleScope.launch { torrent.drop(hash) } }; torrentHash = null
        proxy.stop(); lobby.visibility = View.VISIBLE; roomControls.visibility = View.GONE; roomTitle.text = "Watch Party"; status.text = "You left the room."
        lobby.getChildAt(0)?.requestFocus()
    }
    private suspend fun ensureAllowed(content: JSONObject) {
        val id = content.optString("provider").let { if (it.startsWith("cs:") || it.startsWith("an:") || it.startsWith("sv:")) it else "sv:$it" }
        check(ExtensionEngine.shared.hasProvider(id)) { "This provider is unavailable or restricted in the active profile. Install or enable it in Sources." }
    }
    private fun contentFromIntent(): JSONObject? {
        val provider = intent.getStringExtra("provider") ?: return null
        val ref = intent.getStringExtra("mediaRef") ?: return null
        return JSONObject().put("provider", provider.removePrefix("sv:")).put("mediaRef", PartyIdentity.reference(ref, intent.getStringExtra("contentUrl"))).apply {
            listOf("contentUrl", "title", "thumbnail", "type").forEach { key -> intent.getStringExtra(key)?.let { put(key, it) } }
            listOf("episode", "season").forEach { key -> if (intent.hasExtra(key)) put(key, intent.getIntExtra(key, 1)) }
        }
    }
    private fun label(text: String, size: Float) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(8, 8, 8, 8) }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; textSize = 16f; minimumHeight = 60; setPadding(18, 8, 18, 8); setOnClickListener { action() } }
    private fun guarded(block: suspend () -> Unit) { lifecycleScope.launch { try { block() } catch (c: CancellationException) { throw c } catch (e: Exception) { status.text = e.message ?: "Could not complete room request" } } }
    override fun onStop() { stopped = true; if (isHost && player.playWhenReady) client.emit("party:control", JSONObject().put("action", "pause").put("positionSec", player.currentPosition / 1000.0)); player.pause(); super.onStop() }
    override fun onStart() { super.onStart(); stopped = false; if (::player.isInitialized) applyPlayback() }
    override fun onDestroy() { client.leave(); heartbeat?.cancel(); resolveJob?.cancel(); playerView.player = null; player.release(); proxy.stop(); torrentHash?.let { hash -> CoroutineScope(Dispatchers.IO).launch { torrent.drop(hash) } }; super.onDestroy() }
}

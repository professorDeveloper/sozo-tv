package com.saikou.sozo_tv.debug

import android.app.Instrumentation
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.PlaceholderSurface
import com.saikou.sozo_tv.engine.player.LocalHlsProxy
import com.saikou.sozo_tv.engine.player.StreamHttp
import com.saikou.sozo_tv.engine.player.WebViewStreamExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Debug APK only. Runs unattended extraction + real decoder/progress checks on a TV emulator. */
@androidx.media3.common.util.UnstableApi
class PlayerProbeInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); super.onCreate(arguments); start() }
    override fun onStart() {
        val result = runCatching { runBlocking {
            val input = requireNotNull(arguments.getString("url"))
            val expectedHeight = arguments.getString("height")?.toIntOrNull() ?: 0
            val extracted = WebViewStreamExtractor.extract(targetContext, input,
                mapOf("Referer" to arguments.getString("referer", "https://primesrc.me/")), timeoutMs = 35000)
                ?: error("No media captured")
            val http = StreamHttp(targetContext)
            http.headers = extracted.headers
            val proxy = LocalHlsProxy(http.proxyClient)
            val url = proxy.register(extracted.url, extracted.headers, JSONObject(), JSONObject())
            var player: ExoPlayer? = null
            var surface: PlaceholderSurface? = null
            var failure: Throwable? = null
            var width = 0
            var height = 0
            var position = 0L
            try {
                runOnMainSync {
                    surface = PlaceholderSurface.newInstance(targetContext, false)
                    player = ExoPlayer.Builder(targetContext).build().apply {
                        setVideoSurface(surface)
                        addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failure = error } })
                        val mime = if (extracted.playType == "dash") MimeTypes.APPLICATION_MPD else if (extracted.playType == "mp4") MimeTypes.VIDEO_MP4 else MimeTypes.APPLICATION_M3U8
                        setMediaSource(DefaultMediaSourceFactory(http.dataSourceFactory).createMediaSource(MediaItem.Builder().setUri(url).setMimeType(mime).build()))
                        prepare(); play()
                    }
                }
                repeat(120) {
                    delay(500)
                    runOnMainSync { player?.let { p -> width = p.videoSize.width; height = p.videoSize.height; position = p.currentPosition } }
                    failure?.let { throw it }
                    if (width > 0 && height >= expectedHeight && position >= 1500) return@runBlocking "PASS ${width}x$height position=$position type=${extracted.playType} headers=${extracted.headers.keys}"
                }
                error("Playback timed out ${width}x$height position=$position")
            } finally {
                runOnMainSync { player?.release(); surface?.release() }
                proxy.stop()
            }
        } }
        val text = result.fold({ it }, { "FAIL ${it.stackTraceToString()}" })
        android.util.Log.i("SozoPlayerProbe", text)
        targetContext.filesDir.resolve("player-probe.txt").writeText(text)
        finish(if (result.isSuccess) 0 else 1, Bundle().apply { putString("result", text) })
    }
}

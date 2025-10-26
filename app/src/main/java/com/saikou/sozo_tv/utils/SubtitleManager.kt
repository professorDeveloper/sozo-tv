package com.saikou.sozo_tv.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.saikou.sozo_tv.data.model.SubtitleItem
import java.io.File
import java.net.URL

class SubtitleManager(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory
) {

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun loadSubtitleWithVideo(
        player: ExoPlayer,
        videoUrl: String,
        subtitleItem: SubtitleItem?,
        onSubtitleLoaded: (Boolean) -> Unit
    ) {
        try {
            val videoUri = Uri.parse(videoUrl)
            val videoSource = if (videoUrl.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
            }

            val mergedSource = if (subtitleItem != null && !subtitleItem.url.isNullOrEmpty()) {
                try {
                    val localFile = File(context.cacheDir, "sub_${System.currentTimeMillis()}.srt")
                    URL(subtitleItem.url).openStream().use { input ->
                        localFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    val subUri = Uri.fromFile(localFile)
                    val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(
                            MediaItem.SubtitleConfiguration.Builder(subUri)
                                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                .setLanguage(subtitleItem.lang)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build(),
                            33_000_000L
                        )

                    Log.d("SubtitleManager", "Subtitle loaded: ${subtitleItem.lang}")
                    onSubtitleLoaded(true)
                    MergingMediaSource(videoSource, subtitleSource)
                } catch (e: Exception) {
                    Log.e("SubtitleManager", "Subtitle load failed: ${e.message}")
                    onSubtitleLoaded(false)
                    videoSource
                }
            } else {
                Log.w("SubtitleManager", "No subtitle provided")
                onSubtitleLoaded(false)
                videoSource
            }

            player.setMediaSource(mergedSource)
            player.prepare()
        } catch (e: Exception) {
            Log.e("SubtitleManager", "Error loading subtitle: ${e.message}")
            onSubtitleLoaded(false)
        }
    }

    fun toggleSubtitles(player: ExoPlayer, enable: Boolean) {
        try {
            val trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enable)
                .build()
            player.trackSelectionParameters = trackSelectionParameters
            Log.d("SubtitleManager", "Subtitles ${if (enable) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e("SubtitleManager", "Error toggling subtitles: ${e.message}")
        }
    }
}
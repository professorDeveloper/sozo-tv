package com.saikou.sozo_tv.domain.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

/**
 * The renditions an adaptive stream already carries.
 *
 * The extractor's quality list swaps one URL for another, so every change tears
 * the stream down and builds it again. An HLS master playlist already holds all
 * of its renditions, and the player can move between them mid-playback — or be
 * left to choose for itself, which is what the auto entry is.
 */
object NativeQualities {

    data class Variant(
        val height: Int,
        val bitrate: Int,
        val group: Tracks.Group,
        val index: Int,
    )

    /** Empty when the stream has nothing to choose between. */
    fun of(tracks: Tracks): List<Variant> {
        val found = mutableListOf<Variant>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                if (format.height <= 0) continue
                found += Variant(format.height, format.bitrate, group, i)
            }
        }
        val distinct = found
            .sortedByDescending { it.height }
            .distinctBy { it.height to it.bitrate }
        return if (distinct.size > 1) distinct else emptyList()
    }

    fun overrideFor(variant: Variant): TrackSelectionOverride =
        TrackSelectionOverride(variant.group.mediaTrackGroup, variant.index)

    /** Blank when the manifest declares no bitrate, which is common enough. */
    fun bitrateLabel(bitrate: Int): String =
        if (bitrate <= 0) "" else "${(bitrate / 1_000_000.0 * 10).toInt() / 10.0} Mbps"
}

package com.saikou.sozo_tv.domain.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

object NativeQualities {

    data class Variant(
        val height: Int,
        val bitrate: Int,
        val group: Tracks.Group,
        val index: Int,
    )

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

    fun bitrateLabel(bitrate: Int): String =
        if (bitrate <= 0) "" else "${(bitrate / 1_000_000.0 * 10).toInt() / 10.0} Mbps"
}

package com.saikou.sozo_tv.domain.player

import com.saikou.sozo_tv.parser.models.VideoOption

/** Stable preferences and a finite fallback ladder, independent of provider ordering. */
object SourceSelection {
    fun label(option: VideoOption) = "${option.fansub}|${option.resolution}|${option.quality}"
    fun initial(options: List<VideoOption>, preferred: String?, legacyIndex: Int, maxHeight: Int? = null): Int {
        if (maxHeight == -1) return options.indices.filter { VideoOptionGroups.resolutionOf(options[it]) != null }
            .minByOrNull { VideoOptionGroups.resolutionOf(options[it])!! } ?: legacyIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val candidates = options.indices.filter { i ->
            maxHeight == null || (VideoOptionGroups.resolutionOf(options[i]) ?: 0) <= maxHeight
        }.ifEmpty { options.indices.toList() }
        candidates.firstOrNull { label(options[it]) == preferred }?.let { return it }
        val resolution = preferred?.split('|')?.getOrNull(1)
        if (!resolution.isNullOrBlank()) candidates.firstOrNull { options[it].resolution == resolution }?.let { return it }
        if (maxHeight != null) return candidates.maxByOrNull { VideoOptionGroups.resolutionOf(options[it]) ?: 0 } ?: 0
        return legacyIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    }
    fun next(options: List<VideoOption>, current: Int, failedUrls: Set<String>, codecFailure: Boolean): Int? {
        val currentHeight = options.getOrNull(current)?.let { VideoOptionGroups.resolutionOf(it) }
        return options.indices.filter { it != current && options[it].videoUrl !in failedUrls }
            .sortedWith(compareBy<Int> {
                val option = options[it]
                // Prefer compatible codecs after a decoder failure, using only explicit hints.
                if (codecFailure && Regex("hevc|h\\.?265|av1", RegexOption.IGNORE_CASE)
                        .containsMatchIn(option.fullText + " " + option.quality)) 1 else 0
            }.thenBy { if (VideoOptionGroups.resolutionOf(options[it]) == currentHeight) 0 else 1 }
                .thenBy { if (options[it].useWebViewSniff) 1 else 0 }
                .thenBy { kotlin.math.abs(it - current) })
            .firstOrNull()
    }
}

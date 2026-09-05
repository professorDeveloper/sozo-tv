package com.saikou.sozo_tv.domain.player

import com.saikou.sozo_tv.parser.models.VideoOption

object VideoOptionGroups {

    private val RESOLUTION = Regex("""(\d{3,4})""")

    fun servers(options: List<VideoOption>): List<String> =
        options.map { it.serverName() }.distinct()

    /** Positions in the original list, so the caller's selected index stays valid. */
    fun indicesFor(options: List<VideoOption>, server: String): List<Int> =
        options.indices.filter { options[it].serverName() == server }

    fun serverOf(options: List<VideoOption>, index: Int): String =
        options.getOrNull(index)?.serverName().orEmpty()

    fun switchTo(options: List<VideoOption>, currentIndex: Int, server: String): Int {
        val candidates = indicesFor(options, server)
        if (candidates.isEmpty()) return currentIndex

        val wanted = options.getOrNull(currentIndex)?.let { resolutionOf(it) }
        if (wanted != null) {
            candidates.firstOrNull { resolutionOf(options[it]) == wanted }?.let { return it }
        }
        return candidates.maxByOrNull { resolutionOf(options[it]) ?: 0 } ?: candidates.first()
    }

    fun resolutionOf(option: VideoOption): Int? =
        RESOLUTION.find(option.resolution)?.value?.toIntOrNull()
            ?: RESOLUTION.find(option.quality)?.value?.toIntOrNull()

    private fun VideoOption.serverName(): String =
        fansub.trim().ifBlank { "Default" }
}

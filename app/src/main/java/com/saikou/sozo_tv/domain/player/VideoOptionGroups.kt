package com.saikou.sozo_tv.domain.player

import com.saikou.sozo_tv.parser.models.VideoOption

/**
 * Splits the flat option list into servers and the qualities each one offers.
 *
 * A source returns one list mixing both — "Server A 1080p", "Server B 720p" — and
 * the player showed it raw, so picking a quality could silently move you to a
 * different host. Servers and qualities are independent choices and get one
 * button each.
 */
object VideoOptionGroups {

    private val RESOLUTION = Regex("""(\d{3,4})""")

    fun servers(options: List<VideoOption>): List<String> =
        options.map { it.serverName() }.distinct()

    /** Positions in the ORIGINAL list, so the caller's selected index stays valid. */
    fun indicesFor(options: List<VideoOption>, server: String): List<Int> =
        options.indices.filter { options[it].serverName() == server }

    fun serverOf(options: List<VideoOption>, index: Int): String =
        options.getOrNull(index)?.serverName().orEmpty()

    /**
     * The option to land on when switching to [server], keeping the current
     * quality where that server has it.
     *
     * Falls back to the server's highest resolution rather than its first entry:
     * sources list hosts in arbitrary order, and dropping someone from 1080p to
     * 360p because that host happened to be listed first reads as a bug.
     */
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

    /** Blank host names are common; they still need a stable label to group under. */
    private fun VideoOption.serverName(): String =
        fansub.trim().ifBlank { "Default" }
}

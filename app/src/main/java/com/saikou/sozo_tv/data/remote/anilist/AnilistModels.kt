package com.saikou.sozo_tv.data.remote.anilist

/**
 * The AniList shapes this app reads.
 *
 * Kept deliberately small: the TV shows a library and writes progress, so a
 * faithful mirror of AniList's schema would be mostly dead fields that still
 * have to be parsed on a box with a slow CPU.
 */

/** The account a stored token belongs to. */
data class AnilistViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
)

/** The next episode AniList expects to air. [airingAt] is a UNIX SECOND. */
data class AnilistAiring(
    val episode: Int,
    val airingAt: Long,
) {
    val airsAtMillis: Long get() = airingAt * 1000L

    /** Recomputed from the clock on every read, so a screen left open stays honest. */
    val millisLeft: Long get() = airsAtMillis - System.currentTimeMillis()

    val hasAired: Boolean get() = millisLeft <= 0
}

/** One anime. */
data class AnilistMedia(
    val id: Int,
    val romajiTitle: String? = null,
    val englishTitle: String? = null,
    val nativeTitle: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val nextAiring: AnilistAiring? = null,
) {
    /**
     * English first: source sites are indexed under the title people actually
     * type, and a romaji-only name misses far more than it finds.
     */
    val displayTitle: String
        get() = englishTitle?.takeIf { it.isNotBlank() }
            ?: romajiTitle?.takeIf { it.isNotBlank() }
            ?: nativeTitle.orEmpty()

    /** Every title worth trying against a source, best guess first, no blanks or duplicates. */
    val searchTitles: List<String>
        get() = listOfNotNull(englishTitle, romajiTitle, nativeTitle)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Episodes that exist to watch right now.
     *
     * AniList keeps [episodes] at the announced season total while only
     * `nextAiring.episode - 1` have actually gone out. Offering to mark an
     * unaired episode watched is nonsense, so callers cap against this.
     */
    val airedEpisodes: Int?
        get() = nextAiring?.takeIf { it.episode > 0 }?.let { it.episode - 1 } ?: episodes
}

/** One row of the viewer's library. */
data class AnilistListEntry(
    val id: Int,
    val media: AnilistMedia,
    val status: String,
    /** Episodes FINISHED, not an index. */
    val progress: Int,
    val score: Double? = null,
    val updatedAt: Long = 0,
) {
    /** The next episode to watch, capped at what has aired. Null when caught up. */
    val nextEpisode: Int?
        get() {
            val aired = media.airedEpisodes
            if (aired != null && progress >= aired) return null
            return progress + 1
        }

    /** Aired but unwatched. 0 when caught up or unknown. */
    val behindBy: Int
        get() = media.airedEpisodes?.let { (it - progress).coerceAtLeast(0) } ?: 0

    /** 0..1, or null for an ongoing show with no announced total, where a bar would be a guess. */
    val completion: Float?
        get() {
            val total = media.episodes ?: return null
            if (total <= 0) return null
            return (progress.toFloat() / total).coerceIn(0f, 1f)
        }
}

/** The viewer's position on one title, as AniList holds it. */
data class AnilistEntryState(
    val onList: Boolean,
    val progress: Int,
    val status: String?,
    val totalEpisodes: Int?,
)

/** What AniList actually stored after a write. */
data class AnilistSaveResult(
    val progress: Int,
    val status: String,
)

/**
 * The statuses AniList exposes, in the order a library reads best.
 *
 * [label] is plain English here rather than a string resource because this app
 * ships one language; the enum is the place to change that if it ever ships more.
 */
enum class AnilistStatus(val value: String, val label: String) {
    CURRENT("CURRENT", "Watching"),
    REPEATING("REPEATING", "Rewatching"),
    PLANNING("PLANNING", "Planning"),
    COMPLETED("COMPLETED", "Completed"),
    PAUSED("PAUSED", "Paused"),
    DROPPED("DROPPED", "Dropped");

    companion object {
        fun fromValue(value: String?): AnilistStatus? = entries.firstOrNull { it.value == value }
    }
}

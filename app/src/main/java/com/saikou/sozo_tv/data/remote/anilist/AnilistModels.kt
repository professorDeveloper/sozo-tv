package com.saikou.sozo_tv.data.remote.anilist

data class AnilistViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
)

data class AnilistAiring(
    val episode: Int,
    val airingAt: Long,
) {
    val airsAtMillis: Long get() = airingAt * 1000L

    val millisLeft: Long get() = airsAtMillis - System.currentTimeMillis()

    val hasAired: Boolean get() = millisLeft <= 0
}

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
    val displayTitle: String
        get() = englishTitle?.takeIf { it.isNotBlank() }
            ?: romajiTitle?.takeIf { it.isNotBlank() }
            ?: nativeTitle.orEmpty()

    val searchTitles: List<String>
        get() = listOfNotNull(englishTitle, romajiTitle, nativeTitle)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val airedEpisodes: Int?
        get() = nextAiring?.takeIf { it.episode > 0 }?.let { it.episode - 1 } ?: episodes
}

data class AnilistListEntry(
    val id: Int,
    val media: AnilistMedia,
    val status: String,
    val progress: Int,
    val score: Double? = null,
    val updatedAt: Long = 0,
) {
    val nextEpisode: Int?
        get() {
            val aired = media.airedEpisodes
            if (aired != null && progress >= aired) return null
            return progress + 1
        }

    val behindBy: Int
        get() = media.airedEpisodes?.let { (it - progress).coerceAtLeast(0) } ?: 0

    val completion: Float?
        get() {
            val total = media.episodes ?: return null
            if (total <= 0) return null
            return (progress.toFloat() / total).coerceIn(0f, 1f)
        }
}

data class AnilistEntryState(
    val onList: Boolean,
    val progress: Int,
    val status: String?,
    val totalEpisodes: Int?,
)

data class AnilistSaveResult(
    val progress: Int,
    val status: String,
)

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

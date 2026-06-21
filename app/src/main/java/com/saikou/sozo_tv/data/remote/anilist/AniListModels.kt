package com.saikou.sozo_tv.data.remote.anilist

/**
 * Gson models for the AniList GraphQL responses we consume.
 * Field names match the GraphQL selection sets in [AniListQueries] exactly.
 */

data class AniListPage(val media: List<AniListMedia>?)

data class AniListMedia(
    val id: Int,
    val title: AniListTitle?,
    val coverImage: AniListCover?,
    val averageScore: Int?,
    val format: String?,
    val episodes: Int?,
    val genres: List<String>?,
    val status: String?,
    val mediaListEntry: AniListListEntry?,
) {
    /** Best human title (English → Romaji → Native). */
    val displayTitle: String get() = title?.preferred() ?: "Unknown"

    /** Largest available cover. */
    val cover: String? get() = coverImage?.let { it.extraLarge ?: it.large ?: it.medium }
}

data class AniListTitle(val romaji: String?, val english: String?, val native: String?) {
    fun preferred(): String? = english ?: romaji ?: native
}

data class AniListCover(val large: String?, val extraLarge: String?, val medium: String?)

/** The signed-in user's list entry for a media (null when logged out). */
data class AniListListEntry(val status: String?, val progress: Int?, val score: Double?)

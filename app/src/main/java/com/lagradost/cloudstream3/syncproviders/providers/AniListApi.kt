package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI

/**
 * A stand-in for CloudStream's AniList client. See [com.lagradost.cloudstream3.utils.Event]'s
 * file for why these classes exist.
 *
 * Plugins use it for two different things and only one of them is a sync feature: reading
 * somebody's watch lists, and reading a title's RECOMMENDATIONS — which is public catalogue data
 * needing no account at all. Both are empty here.
 *
 * Recommendations could in principle be answered, since this app already talks to AniList
 * (`data/remote/anilist`). They are not, on purpose: this class has no way to know which title
 * the plugin is asking about without implementing the query, and a half-implemented client that
 * sometimes returns data and sometimes does not is harder to reason about than one that
 * consistently returns none. The detail screen answers the same question with the app's own
 * client, next to the rest of the title's metadata.
 *
 * It implements [SyncAPI] because plugins hand it to a `SyncRepo`, whose constructor takes one —
 * a class that is merely present but not assignable fails verification just as loudly as a
 * missing one.
 *
 * The nested types mirror AniList's GraphQL response shape, because that is what the plugin
 * destructures. Every field is nullable: the plugin already has to handle a partial response
 * from the real API.
 */
class AniListApi : SyncAPI {

    data class MediaTitle(
        val romaji: String? = null,
        val english: String? = null,
    )

    /**
     * AniList's other title shape.
     *
     * The API returns `title` under two different schemas depending on the query, and plugins
     * destructure whichever one their query asked for. Anichi names only this one, and one
     * unresolved class is as fatal as twenty-four.
     */
    data class Title(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
        val userPreferred: String? = null,
    )

    data class MediaCoverImage(
        val large: String? = null,
    )

    data class CoverImage(
        val medium: String? = null,
        val large: String? = null,
        val extraLarge: String? = null,
    )

    data class SeasonNextAiringEpisode(
        val episode: Int? = null,
        val timeUntilAiring: Int? = null,
    )

    data class LikePageInfo(
        val hasNextPage: Boolean? = null,
        val total: Int? = null,
    )

    data class RecommendedMedia(
        val id: Int? = null,
        val title: MediaTitle? = null,
        val coverImage: MediaCoverImage? = null,
    )

    data class Recommendation(
        val mediaRecommendation: RecommendedMedia? = null,
    )

    data class RecommendationEdge(
        val node: Recommendation? = null,
    )

    data class RecommendationConnection(
        val edges: List<RecommendationEdge>? = emptyList(),
        val pageInfo: LikePageInfo? = null,
    )
}

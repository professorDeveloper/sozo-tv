package com.saikou.sozo_tv.domain.model

data class SearchResults(
    var hasNextPage: Boolean,
    var currentPage: Int,
    var genre: String?,
    var results: List<MainModel>?,
    var tag: String = "",
    var year: Int = -1,
    var avgScore: Int = -1,
    // Provider catalog section/genre slug for the selected chip. When non-null the
    // Categories repo pages engine.section(slug); when null it falls back to the legacy
    // title-search-by-genre-name path (AniList / hardcoded LocalData.genres).
    var slug: String? = null,
)

/**
 * A genre/section chip in the Categories filter row. [slug] is the provider's
 * "type::slug" identifier for engine.section(); null means "use the search-by-name
 * fallback" (hardcoded AniList genres / TMDB genres).
 */
data class CategoryChip(
    val name: String,
    val slug: String?,
)
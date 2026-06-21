package com.saikou.sozo_tv.domain.model

data class SearchModel(
    val id: Int?,
    val title: String?,
    val image: String?,
    val studios: List<String?>?,
    val genres: List<String?>?,
    val averageScore: Int?,
    /** AniList media id when this result came from the AniList catalog (else null). */
    val aniListId: Int? = null,
    /** The signed-in user's AniList list status for this media (CURRENT/PLANNING/…), if any. */
    val listStatus: String? = null,
)
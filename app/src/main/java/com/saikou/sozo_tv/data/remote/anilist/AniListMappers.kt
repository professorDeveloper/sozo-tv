package com.saikou.sozo_tv.data.remote.anilist

import com.saikou.sozo_tv.domain.model.SearchModel

/**
 * Map an AniList media onto the [SearchModel] the search UI already renders.
 * `id` stays null (there is no provider-registry id yet) and the AniList media id is kept in
 * [SearchModel.aniListId] so the row can: (a) be opened on the selected provider by title, and
 * (b) be managed on the user's AniList list.
 */
fun AniListMedia.toSearchModel(): SearchModel = SearchModel(
    id = null,
    title = displayTitle,
    image = cover,
    studios = null,
    genres = genres,
    averageScore = averageScore,
    aniListId = id,
    listStatus = mediaListEntry?.status,
)

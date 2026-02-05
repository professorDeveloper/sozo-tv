package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.MyListQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.MyListRepository

class MyListRepositoryImpl(
    private val client: ApolloClient
) : MyListRepository {

    override suspend fun getMyList(userId: Int, listType: String): Result<List<MainModel>> =
        runCatching {
            val data = client.query(MyListQuery(userId = Optional.present(userId))).execute().data

            val lists = data?.MediaListCollection?.lists.orEmpty()
            val normalized = normalizeListType(listType)

            val entries = if (normalized == "Favorites") {
                lists.flatMap { it?.entries.orEmpty() }.filter { it?.media?.isFavourite == true }
            } else {
                lists.firstOrNull { it?.name == normalized }?.entries.orEmpty()
            }
            entries.mapNotNull { entry ->
                val media = entry?.media ?: return@mapNotNull null
                if (!media.isAdult!!) {
                    MainModel(
                        id = media.id,
                        title = media.title?.userPreferred ?: media.title?.romaji
                        ?: media.title?.english ?: "Unknown Title",
                        idMal = media.idMal ?: -1,
                        image = media.coverImage?.large.orEmpty(),
                        genres = arrayListOf(),
                        studios = arrayListOf(),
                        averageScore = media.meanScore ?: -1,
                        meanScore = media.meanScore ?: -1,
                        isAnime = true
                    )
                } else {
                    null
                }
            }
        }

    private fun normalizeListType(listType: String): String {
        return when (listType.trim()) {
            "On-Hold" -> "Paused"
            "Plan to Watch" -> "Planning"

            "Watching" -> "Watching"
            "Completed TV" -> "Completed TV"
            "Completed Movie" -> "Completed Movie"
            "Paused" -> "Paused"
            "Planning" -> "Planning"

            "Favorites" -> "Favorites"

            else -> listType.trim()
        }
    }
}

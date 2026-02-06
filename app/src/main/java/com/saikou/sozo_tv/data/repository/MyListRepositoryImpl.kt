package com.saikou.sozo_tv.data.repository

import com.animestudios.animeapp.MyListQuery
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.MyListRepository

class MyListRepositoryImpl(
    private val client: ApolloClient
) : MyListRepository {

    override suspend fun getMyList(userId: Int, listType: String): Result<List<MainModel>> =
        runCatching {
            val response = client.query(
                MyListQuery(
                    userId = Optional.Present(userId),
                )
            ).execute()

            if (!response.errors.isNullOrEmpty()) {
                throw IllegalStateException(response.errors!!.joinToString(" | ") { it.message })
            }

            val lists = response.data?.MediaListCollection?.lists.orEmpty()
            val normalized = normalizeListType(listType)

            val entries = when (normalized) {
                "Favorites" -> lists
                    .flatMap { it?.entries.orEmpty() }
                    .filter { it?.media?.isFavourite == true }

                "Completed" -> lists
                    .filter { it?.name == "Completed" || (it?.name?.startsWith("Completed ") == true) }
                    .flatMap { it?.entries.orEmpty() }

                else -> lists
                    .firstOrNull { it?.name == normalized }
                    ?.entries
                    .orEmpty()
            }

            entries.mapNotNull { entry ->
                val media = entry?.media ?: return@mapNotNull null
                if (!PreferenceManager().isNsfwEnabled()) {
                    if (media.isAdult == true) return@mapNotNull null
                }
                MainModel(
                    id = media.id,
                    title = media.title?.userPreferred
                        ?: media.title?.romaji
                        ?: media.title?.english
                        ?: "Unknown Title",
                    idMal = media.idMal ?: -1,
                    image = media.coverImage?.large.orEmpty(),
                    genres = arrayListOf(),
                    studios = arrayListOf(),
                    averageScore = media.meanScore ?: -1,
                    meanScore = media.meanScore ?: -1,
                    isAnime = true
                )
            }
        }

    private fun normalizeListType(listType: String): String {
        return when (listType.trim()) {
            "On-Hold" -> "Paused"
            "Plan to Watch" -> "Planning"
            else -> listType.trim()
        }
    }
}

package com.saikou.sozo_tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moviebookmark")
data class AnimeBookmark(
    @PrimaryKey
    val id: Int,
    val title: String,
    val idMal: Int = -1,
    val image: String,
    val isSeries: Boolean = false,
    val isAnime: Boolean = true,
)

@Entity(tableName = "characterbookmark")
data class CharacterEntity(
    @PrimaryKey
    val id: Int,
    val image: String,
    val name: String,
    val role: String,
    val age: String,
    val isAnime: Boolean = true
)

@Entity
data class EpisodeInfoEntity(
    val episodeId: Int,
    val episodeName: String,
    val episodeDuration: Long,
    val currPosition: Int,
    val videoUrl: String,
)


@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val session: String,
    val title: String,
    val mediaName: String,
    val image: String,
    val categoryProperty: String?,
    val categoryid: String?,
    val country: String?,
    val description: String?,
    val language: String?,
    val rating: Double?,
    val page: Int?,
    val release_year: String?,
    val videoUrl: String,
    val totalDuration: Long,
    val lastPosition: Long,
    val watchedAt: Long = System.currentTimeMillis(),
    val isEpisode: Boolean = true,
    val imdbID: String = "-1",
    var epIndex: Int = -1,
    val currentQualityIndex: Int = -1,
    val isAnime: Boolean = true,
    val isSeries: Boolean = false,
)
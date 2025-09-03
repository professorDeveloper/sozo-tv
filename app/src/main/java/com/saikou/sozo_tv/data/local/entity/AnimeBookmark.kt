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
    val averageScore: Int,
    val meanScore: Int = -1
)

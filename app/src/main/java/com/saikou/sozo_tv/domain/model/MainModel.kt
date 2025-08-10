package com.saikou.sozo_tv.domain.model

import java.io.Serializable

data class MainModel(
    val id: Int,
    val title: String,
    val idMal: Int = -1,
    val image: String,
    val genres: List<String?>?,
    val studios: List<String?>?,
    val averageScore: Int,
    val meanScore: Int = -1
) : Serializable




package com.saikou.sozo_tv.domain.model

data class AiringSchedule(
    val airingAt: Long=-1,
    val episode: Int=-1,
    val timeUntilAiring: Int=-1
)
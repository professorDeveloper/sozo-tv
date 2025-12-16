package com.saikou.sozo_tv.data.model

import java.io.Serializable

data class SubSource(
    val sourceId: String = "",
    val title: String = "",
    val country: String = "",
    val sourceType: String = ""
) : Serializable
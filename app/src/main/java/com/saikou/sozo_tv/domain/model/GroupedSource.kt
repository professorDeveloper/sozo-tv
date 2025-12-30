package com.saikou.sozo_tv.domain.model

import com.saikou.sozo_tv.data.model.SubSource

data class GroupedSource(
    val type: String,
    val title: String,
    val sources: List<SubSource>
)
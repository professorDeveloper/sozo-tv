package com.saikou.sozo_tv.data.model

data class HlsVariant(
    val originalUrl: String,
    val resolvedUrl: String,
    val attributes: Map<String, String>
)
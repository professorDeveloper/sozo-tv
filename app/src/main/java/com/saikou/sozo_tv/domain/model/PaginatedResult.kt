package com.saikou.sozo_tv.domain.model

data class PaginatedResult(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val list: List<MainModel>
)
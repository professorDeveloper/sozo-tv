package com.saikou.sozo_tv.domain.model

data class SearchResults(
    var hasNextPage: Boolean,
    var currentPage: Int,
    var genre: String?,
    var results: List<MainModel>?,
    var tag: String = "",
    var year: Int = -1,
    var avgScore: Int = -1,
)
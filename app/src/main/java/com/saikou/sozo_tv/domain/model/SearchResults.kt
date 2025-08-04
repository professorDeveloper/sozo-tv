package com.saikou.sozo_tv.domain.model

data class SearchResults(
    var hasNextPage: Boolean,
    var currentPage: Int,
    var genre: String?,
    var results: List<MainModel>?
)
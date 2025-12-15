package com.saikou.sozo_tv.parser.base

abstract class BaseMovieParser : BaseParser() {
    open suspend fun extractMovie(imdbId: String): String = ""
}

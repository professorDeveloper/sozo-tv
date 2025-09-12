package com.saikou.sozo_tv.data.model

import java.io.Serializable

data class VodMovieResponse(
    val authInfo: String,
    @Transient val subtitleList: Any,  // Ignored during serialization
    val urlobj: String
) : Serializable

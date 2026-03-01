package com.saikou.sozo_tv.domain.model

data class AppUpdate(
    val versionCode: Long = 0L,
    val releaseDate: Long? = null,
    val isMandatory: Boolean = false,
    val changeLog: String? = null,
    var appLink: String? = null,
    val imageLink: String? = null
)

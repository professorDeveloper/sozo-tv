package com.saikou.sozo_tv.parser.models

import com.lagradost.nicehttp.Session

data class Kiwi(
    val session: String,
    val provider: String,
    val url: String,
)
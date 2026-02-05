package com.saikou.sozo_tv.data.model.hianime

data class ServerResponse(val link: String)
data class HiServer(
    val id: String,
    val label: String,
    val type: String = "",
)

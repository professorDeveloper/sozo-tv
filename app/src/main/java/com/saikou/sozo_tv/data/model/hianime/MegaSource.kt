package com.saikou.sozo_tv.data.model.hianime
data class MegaSource(
    val file: String,
    val type: String
)

data class MegaTrack(
    val file: String,
    val label: String,
    val kind: String
)

data class MegaResponse(
    val sources: List<MegaSource>,
    val tracks: List<MegaTrack>,
    val encrypted: Boolean
)
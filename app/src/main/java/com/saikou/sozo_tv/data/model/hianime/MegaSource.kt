package com.saikou.sozo_tv.data.model.hianime

data class MegaSource(
    val file: String,
    val type: String
)
data class MegaTrack(
    val file: String,
    val label: String? = "",
    val kind: String,
    /** Headers for fetching this track — see [com.saikou.sozo_tv.data.extensions.ExtSubtitle]. */
    val headers: Map<String, String> = emptyMap(),
)
data class MegaResponse(
    val sources: List<MegaSource>,
    val tracks: List<MegaTrack>,
    val encrypted: Boolean
)
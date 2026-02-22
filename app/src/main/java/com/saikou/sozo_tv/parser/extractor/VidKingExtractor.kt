package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.parser.models.Video

class VidKingExtractor : Extractor() {
    override val name: String = "vidking"
    override val mainUrl: String = "https://vidking.net"
    override fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
                is Video.Type.Movie -> "$mainUrl/embed/movie/${videoType.id}"
            },
        )
    }

    override suspend fun extract(link: String): Video {

    }
}
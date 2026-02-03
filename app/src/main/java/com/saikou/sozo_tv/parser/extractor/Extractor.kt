package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.parser.models.Video

abstract class Extractor {
    abstract val name: String
    abstract val mainUrl: String
    open val aliasUrls: List<String> = emptyList()
    open val rotatingDomain: List<Regex> = emptyList()

    abstract suspend fun extract(link: String): Video

    open fun server(videoType: Video.Type): Video.Server {
        throw NotImplementedError("server() method is not implemented for ${this.name} extractor")
    }


    companion object {
        private val extractors = listOf(
            VixSrcExtractor(),
            FilemoonExtractor(),
            VidzeeExtractor(),
            PrimeSrcExtractor()
        )

        fun getCurrentExtractor(name: String): Extractor? {
            return extractors.find { it.name.equals(name, ignoreCase = true) }
        }

        suspend fun extract(link: String): Video {
            val urlRegex = Regex("^(https?://)?(www\\.)?")
            val compareUrl = link.lowercase().replace(urlRegex, "")

            for (extractor in extractors) {
                // Asosiy URL ni tekshirish
                if (compareUrl.startsWith(extractor.mainUrl.lowercase().replace(urlRegex, ""))) {
                    return extractor.extract(link)
                }

                // Alias URL larni tekshirish
                for (aliasUrl in extractor.aliasUrls) {
                    if (compareUrl.startsWith(aliasUrl.lowercase().replace(urlRegex, ""))) {
                        return extractor.extract(link)
                    }
                }

                // Rotating domain lar uchun tekshirish
                for (regex in extractor.rotatingDomain) {
                    if (regex.containsMatchIn(compareUrl)) {
                        return extractor.extract(link)
                    }
                }
            }

            throw Exception("No extractor found for URL: $link")
        }
    }
}
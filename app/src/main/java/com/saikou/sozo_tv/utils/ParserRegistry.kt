package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.anime.HentaiMama
import com.saikou.sozo_tv.parser.anime.HiAnime

object ParserRegistry {

    private val parsers: Map<String, () -> BaseParser> = mapOf(
        "animepahe" to { AnimePahe() },
        "hianime" to { HiAnime() },
        "hentaimama" to { HentaiMama() },
    )

    /**
     * Returns the CURRENT parser selected by the user.
     * Always returns BaseParser.
     */
    fun getCurrentParser(): BaseParser {
        val currentId = readData("currentSourceId") ?: "animepahe"
        return parsers[currentId]!!.invoke()
    }
}


package com.saikou.sozo_tv.parser.sources

import com.saikou.sozo_tv.parser.BaseParser
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.anime.HiAnime

object AnimeSources {

    fun getCurrent(): BaseParser {
        return when (SourceManager.getCurrentSourceKey()) {
            "hianime" -> HiAnime()
            else -> AnimePahe()
        }
    }

}

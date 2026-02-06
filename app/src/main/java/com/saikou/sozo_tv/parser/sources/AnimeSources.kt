package com.saikou.sozo_tv.parser.sources

import android.util.Log
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.anime.AnimeWorldParser
import com.saikou.sozo_tv.parser.anime.HiAnime
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.utils.LocalData.SOURCE

object AnimeSources {

    fun getCurrent(): BaseParser {
        val readData = PreferenceManager().getString(SOURCE)
        Log.d("GGG", "getCurrent:${readData} ")
        return when (readData) {
            "animeworld" -> AnimeWorldParser()
            "hianime" -> HiAnime()
            else -> AnimePahe()
        }
    }

    fun getSourceById(id: String): BaseParser {
        return when (id) {
            "animeworld" -> AnimeWorldParser()
            "hianime" -> HiAnime()
            else -> AnimePahe()
        }
    }
}

package com.saikou.sozo_tv.parser.sources

import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.SOURCE
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData

object SourceManager {

    private var currentSourceKey: String = readData(SOURCE) ?: "animepahe"

    fun setCurrentSource(key: String) {
        currentSourceKey = key
        saveData(SOURCE, key)
    }

    fun getCurrentSourceKey(): String = currentSourceKey
}

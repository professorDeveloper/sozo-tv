package com.saikou.sozo_tv.parser.sources

import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData

object SourceManager {

    private var currentSourceKey: String = readData("subSource") ?: "pahe"

    fun setCurrentSource(key: String) {
        currentSourceKey = key
        saveData("subSource", key)
    }

    fun getCurrentSourceKey(): String = currentSourceKey
}

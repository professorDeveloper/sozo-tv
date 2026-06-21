package com.saikou.sozo_tv.parser.sources

import android.util.Log
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.utils.LocalData.SOURCE

object AnimeSources {

    /** Sentinel source key stored once the user selects an extension provider. */
    const val EXTENSION = "extension"

    fun getCurrent(): BaseParser {
        val readData = PreferenceManager().getString(SOURCE)
        Log.d("GGG", "getCurrent:${readData} ")
        return getSourceById(readData)
    }

    fun getSourceById(id: String): BaseParser {
        return ExtensionParser()
    }
}

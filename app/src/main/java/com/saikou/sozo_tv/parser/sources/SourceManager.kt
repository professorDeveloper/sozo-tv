package com.saikou.sozo_tv.parser.sources

import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.SOURCE
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData

class SourceManager {

    fun getCurrentSourceKey(): String = PreferenceManager().getString(SOURCE)

}

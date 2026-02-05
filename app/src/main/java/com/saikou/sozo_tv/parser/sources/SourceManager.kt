package com.saikou.sozo_tv.parser.sources

import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.utils.LocalData.SOURCE

class SourceManager {

    fun getCurrentSourceKey(): String = PreferenceManager().getString(SOURCE)

}

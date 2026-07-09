package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Minimal clean-room re-declaration of CloudStream's `DataStore`.
 *
 * Like [com.lagradost.cloudstream3.plugins.Plugin], this lives in CloudStream's **app**
 * module and is absent from the published `library` artifact, so a `.cs3` that touches it
 * dies with `NoClassDefFoundError` while its dex is verified. Plugins reach for it to
 * persist their own settings.
 *
 * Only `getSharedPrefs` is provided — that is the single member the plugins we install
 * actually link against. Adding more is safe; removing this breaks AnimePahe.
 */
object DataStore {
    fun getSharedPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getDefaultSharedPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
}

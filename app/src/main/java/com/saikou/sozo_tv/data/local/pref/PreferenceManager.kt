package com.saikou.sozo_tv.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import com.saikou.sozo_tv.app.MyApp

class PreferenceManager {

    private val sharedPreferences: SharedPreferences =
        MyApp.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "app_preferences"
        private const val KEY_NSFW_ENABLED = "nsfw_enabled"
        private const val KEY_CHANNEL_ENABLED = "nsfw_enabled"
        private const val KEY_SKIP_INTRO_ENABLED = "skip_intro_enabled"
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_SKIP_INTRO_ENABLED, enabled)
            .apply()
    }

    fun isSkipIntroEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SKIP_INTRO_ENABLED, false)
    }


    fun setNsfwEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_NSFW_ENABLED, enabled)
            .apply()
    }

    fun isNsfwEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_NSFW_ENABLED, false)
    }


    fun setChannelEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_CHANNEL_ENABLED, enabled)
            .apply()
    }

    fun isChannelEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_CHANNEL_ENABLED, false)
    }

}

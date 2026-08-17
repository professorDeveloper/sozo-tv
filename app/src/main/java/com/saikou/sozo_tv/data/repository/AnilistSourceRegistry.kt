package com.saikou.sozo_tv.data.repository

import android.content.Context

class AnilistSourceRegistry(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_PROVIDERS, emptySet()).orEmpty()

    fun supports(providerId: String): Boolean = providerId.trim() in all()

    fun remember(providerId: String) {
        val id = providerId.trim()
        if (id.isEmpty() || id in all()) return
        prefs.edit().putStringSet(KEY_PROVIDERS, all() + id).apply()
    }

    private companion object {
        const val FILE = "sozo_anilist_sources"
        const val KEY_PROVIDERS = "providers"
    }
}

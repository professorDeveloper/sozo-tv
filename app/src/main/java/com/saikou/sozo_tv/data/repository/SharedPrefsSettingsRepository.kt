package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.ContentMode
import com.saikou.sozo_tv.data.model.SeasonalTheme
import com.saikou.sozo_tv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SharedPrefsSettingsRepository(
    private val prefs: PreferenceManager
) : SettingsRepository {

    override val contentMode: Flow<ContentMode> =
        prefs.observeModeAnimeEnabled()
            .map { enabled -> if (enabled) ContentMode.ANIME else ContentMode.MOVIE }
            .distinctUntilChanged()

    override val seasonalTheme: Flow<SeasonalTheme> =
        prefs.observeSeasonalTheme()
            .distinctUntilChanged()

    override fun setContentMode(mode: ContentMode) {
        prefs.setModeAnime(mode == ContentMode.ANIME)
    }

    override fun setSeasonalTheme(theme: SeasonalTheme) {
        prefs.setSeasonalTheme(theme)
    }
}

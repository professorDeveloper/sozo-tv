package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.ContentMode
import com.saikou.sozo_tv.data.model.SeasonalTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val contentMode: Flow<ContentMode>
    val seasonalTheme: Flow<SeasonalTheme>

    fun setContentMode(mode: ContentMode)
    fun setSeasonalTheme(theme: SeasonalTheme)
}

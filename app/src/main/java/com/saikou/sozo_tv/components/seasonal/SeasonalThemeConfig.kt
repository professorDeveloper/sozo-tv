package com.saikou.sozo_tv.components.seasonal

import androidx.annotation.DrawableRes
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SeasonalTheme

internal data class SeasonalThemeConfig(
    @DrawableRes val backgroundDrawable: Int?,
    val useSnow: Boolean,
)

internal object SeasonalThemeRegistry {

    private val configs: Map<SeasonalTheme, SeasonalThemeConfig> = mapOf(
        SeasonalTheme.DEFAULT to SeasonalThemeConfig(
            backgroundDrawable = null,
            useSnow = false
        ),
        SeasonalTheme.WINTER to SeasonalThemeConfig(
            backgroundDrawable = R.drawable.bg_theme_preview_winter,
            useSnow = true
        ),
        SeasonalTheme.HALLOWEEN to SeasonalThemeConfig(
            backgroundDrawable = R.drawable.bg_theme_preview_halloween,
            useSnow = false
        ),
    )

    fun config(theme: SeasonalTheme): SeasonalThemeConfig =
        configs[theme] ?: configs.getValue(SeasonalTheme.DEFAULT)
}

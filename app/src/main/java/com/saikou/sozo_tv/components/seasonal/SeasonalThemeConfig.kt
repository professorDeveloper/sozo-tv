package com.saikou.sozo_tv.components.seasonal

import androidx.annotation.DrawableRes
import com.saikou.sozo_tv.data.model.SeasonalTheme

internal data class SeasonalThemeConfig(
    @DrawableRes val backgroundDrawable: Int?,
    val useSnow: Boolean,
    val useWinterDecorations: Boolean,
)

internal object SeasonalThemeRegistry {

    /**
     * NOTE:
     * - We intentionally DO NOT ship multiple app themes here.
     * - Default Netflix black stays everywhere.
     * - WINTER only enables a lightweight "Surprise" layer (snow + small icons).
     *
     * If theme == HALLOWEEN (legacy value), it falls back to DEFAULT.
     */
    private val configs: Map<SeasonalTheme, SeasonalThemeConfig> = mapOf(
        SeasonalTheme.DEFAULT to SeasonalThemeConfig(
            backgroundDrawable = null,
            useSnow = false,
            useWinterDecorations = false,
        ),
        SeasonalTheme.WINTER to SeasonalThemeConfig(
            backgroundDrawable = null,
            useSnow = true,
            useWinterDecorations = true,
        ),
    )

    fun config(theme: SeasonalTheme): SeasonalThemeConfig =
        configs[theme] ?: configs.getValue(SeasonalTheme.DEFAULT)
}

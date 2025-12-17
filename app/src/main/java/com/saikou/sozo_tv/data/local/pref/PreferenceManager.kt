package com.saikou.sozo_tv.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.SeasonalTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PreferenceManager {

    private val prefs: SharedPreferences =
        MyApp.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "app_preferences"

        private const val KEY_NSFW_ENABLED = "nsfw_enabled"
        private const val KEY_CHANNEL_ENABLED = "channel_enabled"
        private const val KEY_SKIP_INTRO_ENABLED = "skip_intro_enabled"
        private const val KEY_MODE_ANIME_ENABLED = "mode_anime_enabled"

        private const val KEY_SUBTITLE_CUSTOM = "subtitle_custom"
        private const val KEY_SUBTITLE_FONT = "subtitle_font"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SUBTITLE_SIZE = "subtitle_size"
        private const val KEY_SUBTITLE_BG = "subtitle_bg"
        private const val KEY_SUBTITLE_OUTLINE = "subtitle_outline"

        private const val KEY_APPEARANCE_EXPANDED = "appearance_expanded"
        private const val KEY_CONTENT_CONTROLS_EXPANDED = "content_controls_expanded"

        // New: the only thing we store for surprise layer
        private const val KEY_SEASONAL_THEME = "seasonal_theme"

        // Legacy (old demo theme UI)
        private const val KEY_DEMO_THEME_LEGACY = "demo_theme"
    }

    fun isModeAnimeEnabled() = prefs.getBoolean(KEY_MODE_ANIME_ENABLED, true)
    fun setModeAnime(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MODE_ANIME_ENABLED, enabled).apply()

    fun isSkipIntroEnabled() = prefs.getBoolean(KEY_SKIP_INTRO_ENABLED, false)
    fun setSkipIntroEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_SKIP_INTRO_ENABLED, enabled).apply()

    fun isNsfwEnabled() = prefs.getBoolean(KEY_NSFW_ENABLED, false)
    fun setNsfwEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_NSFW_ENABLED, enabled).apply()

    fun isChannelEnabled() = prefs.getBoolean(KEY_CHANNEL_ENABLED, false)
    fun setChannelEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_CHANNEL_ENABLED, enabled).apply()

    // ===== Subtitle Style =====
    data class SubtitleStyle(
        val font: Font = Font.DEFAULT,
        val color: Int = Color.WHITE,
        val sizeSp: Int = 16,
        val background: Boolean = false,
        val outline: Boolean = true
    ) {
        fun isDefault() =
            font == Font.DEFAULT &&
                    color == Color.WHITE &&
                    sizeSp == 16 &&
                    !background &&
                    outline
    }

    enum class Font { DEFAULT, POPPINS, DAYS, MONO }

    fun getSubtitleStyle(): SubtitleStyle {
        return SubtitleStyle(
            font = Font.valueOf(prefs.getString(KEY_SUBTITLE_FONT, Font.DEFAULT.name)!!),
            color = prefs.getInt(KEY_SUBTITLE_COLOR, Color.WHITE),
            sizeSp = prefs.getInt(KEY_SUBTITLE_SIZE, 16),
            background = prefs.getBoolean(KEY_SUBTITLE_BG, false),
            outline = prefs.getBoolean(KEY_SUBTITLE_OUTLINE, true)
        )
    }

    fun isSubtitleCustom(): Boolean =
        prefs.getBoolean(KEY_SUBTITLE_CUSTOM, false)

    fun saveSubtitleStyle(style: SubtitleStyle) {
        prefs.edit()
            .putBoolean(KEY_SUBTITLE_CUSTOM, !style.isDefault())
            .putString(KEY_SUBTITLE_FONT, style.font.name)
            .putInt(KEY_SUBTITLE_COLOR, style.color)
            .putInt(KEY_SUBTITLE_SIZE, style.sizeSp)
            .putBoolean(KEY_SUBTITLE_BG, style.background)
            .putBoolean(KEY_SUBTITLE_OUTLINE, style.outline)
            .apply()
    }

    // ===== Surprise (Winter) =====

    fun getSeasonalTheme(): SeasonalTheme {
        // new key first
        val raw = prefs.getString(KEY_SEASONAL_THEME, null)
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching { SeasonalTheme.valueOf(raw) }
                .getOrDefault(SeasonalTheme.DEFAULT)

            // Halloween removed from UI; treat it as DEFAULT.
            return if (parsed.name.equals("HALLOWEEN", ignoreCase = true)) {
                SeasonalTheme.DEFAULT
            } else parsed
        }

        // legacy migration
        val legacy = prefs.getString(KEY_DEMO_THEME_LEGACY, null)
        val migrated = when (legacy) {
            "WINTER" -> SeasonalTheme.WINTER
            else -> SeasonalTheme.DEFAULT // DEFAULT + HALLOWEEN -> DEFAULT
        }

        prefs.edit()
            .putString(KEY_SEASONAL_THEME, migrated.name)
            .remove(KEY_DEMO_THEME_LEGACY)
            .apply()

        return migrated
    }

    fun setSeasonalTheme(theme: SeasonalTheme) {
        val safeTheme = if (theme.name.equals("HALLOWEEN", ignoreCase = true)) {
            SeasonalTheme.DEFAULT
        } else theme

        prefs.edit().putString(KEY_SEASONAL_THEME, safeTheme.name).apply()
    }

    fun observeSeasonalTheme(): Flow<SeasonalTheme> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SEASONAL_THEME || key == KEY_DEMO_THEME_LEGACY) {
                trySend(getSeasonalTheme())
            }
        }
        trySend(getSeasonalTheme())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ===== Dropdown expanded states =====
    fun isAppearanceExpanded(): Boolean =
        prefs.getBoolean(KEY_APPEARANCE_EXPANDED, true)

    fun setAppearanceExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_APPEARANCE_EXPANDED, expanded).apply()
    }

    fun isContentControlsExpanded(): Boolean =
        prefs.getBoolean(KEY_CONTENT_CONTROLS_EXPANDED, false)

    fun setContentControlsExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CONTENT_CONTROLS_EXPANDED, expanded).apply()
    }

    // ===== Backward compatibility (optional) =====
    @Deprecated("Theme UI removed. Use getSeasonalTheme()/setSeasonalTheme() or observeSeasonalTheme().")
    enum class DemoTheme { DEFAULT, WINTER, HALLOWEEN }

    @Deprecated("Theme UI removed. Use observeSeasonalTheme().")
    fun observeDemoTheme(): Flow<DemoTheme> =
        observeSeasonalTheme()
            .map { t ->
                when (t.name) {
                    "WINTER" -> DemoTheme.WINTER
                    "HALLOWEEN" -> DemoTheme.DEFAULT
                    else -> DemoTheme.DEFAULT
                }
            }
            .distinctUntilChanged()

    @Deprecated("Theme UI removed. Use getSeasonalTheme().")
    fun getDemoTheme(): DemoTheme =
        when (getSeasonalTheme().name) {
            "WINTER" -> DemoTheme.WINTER
            else -> DemoTheme.DEFAULT
        }

    @Deprecated("Theme UI removed. Use setSeasonalTheme().")
    fun setDemoTheme(theme: DemoTheme) {
        setSeasonalTheme(
            when (theme) {
                DemoTheme.WINTER -> SeasonalTheme.WINTER
                else -> SeasonalTheme.DEFAULT
            }
        )
    }

    fun observeModeAnimeEnabled(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MODE_ANIME_ENABLED) trySend(isModeAnimeEnabled())
        }
        trySend(isModeAnimeEnabled())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}

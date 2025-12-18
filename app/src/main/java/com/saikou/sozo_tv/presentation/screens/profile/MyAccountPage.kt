package com.saikou.sozo_tv.presentation.screens.profile

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SeasonalTheme
import com.saikou.sozo_tv.databinding.MyAccountPageBinding
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class MyAccountPage : Fragment() {

    private var _binding: MyAccountPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager

    private var ignoreNsfwCallback = false
    private var themePreviewAnimator: ValueAnimator? = null

    private var ignoreWinterToggleCallback = false
    private var lastNonWinterTheme: SeasonalTheme = SeasonalTheme.DEFAULT
    private val settingsViewModel: SettingsViewModel by activityViewModel()

    interface AuthNavigator {
        fun openLogin()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = MyAccountPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        themePreviewAnimator?.cancel()
        themePreviewAnimator = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager()

        setupLoginButton()

        loadModePreference()
        setupModeButtons()

        setupAppearanceSection()
        setupContentControlsSection()
    }

    private fun setupLoginButton() {
        binding.loginButton.setOnClickListener {
            val host = activity
            if (host is AuthNavigator) {
                host.openLogin()
            } else {
                Toast.makeText(requireContext(), "Login page not connected yet", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupModeButtons() {
        binding.apply {
            animeModeButton.setOnClickListener {
                setModeAnime(true)
                updateModeUI(true)
            }

            movieModeButton.setOnClickListener {
                setModeAnime(false)
                updateModeUI(false)
            }
        }
    }

    private fun loadModePreference() {
        val isAnimeMode = preferenceManager.isModeAnimeEnabled()
        updateModeUI(isAnimeMode)
    }

    private fun updateModeUI(isAnimeMode: Boolean) {
        binding.apply {
            updateButtonBackground(animeModeButton, isAnimeMode)
            updateButtonBackground(movieModeButton, !isAnimeMode)
        }
    }

    private fun setModeAnime(enabled: Boolean) {
        preferenceManager.setModeAnime(enabled)
        LocalData.isAnimeEnabled = enabled
    }

    private fun updateButtonBackground(button: TextView, isActive: Boolean) {
        button.setBackgroundResource(
            if (isActive) R.drawable.switch_selected_background
            else R.drawable.switch_background
        )
    }

    private fun setupAppearanceSection() {
        setupSubtitleStyle(binding, preferenceManager)
        setupThemeDemo(binding)
    }

    private fun setupContentControlsSection() {
        binding.contentControlsDropdown.setExpanded(
            preferenceManager.isContentControlsExpanded(),
            animate = false
        )
        binding.contentControlsDropdown.setOnExpandedChangeListener { expanded ->
            preferenceManager.setContentControlsExpanded(expanded)
        }
        binding.channelToggleRow.setChecked(preferenceManager.isChannelEnabled())
        binding.channelToggleRow.setOnCheckedChangedListener { isChecked ->
            preferenceManager.setChannelEnabled(isChecked)
            updateContentControlsHeader()
        }

        binding.nsfwToggleRow.setChecked(preferenceManager.isNsfwEnabled())
        binding.nsfwToggleRow.setOnCheckedChangedListener { isChecked ->
            if (ignoreNsfwCallback) return@setOnCheckedChangedListener

            if (isChecked && !preferenceManager.isNsfwEnabled()) {
                ignoreNsfwCallback = true
                binding.nsfwToggleRow.setChecked(false)
                ignoreNsfwCallback = false

                showNsfwWarningDialog()
                return@setOnCheckedChangedListener
            }

            preferenceManager.setNsfwEnabled(isChecked)
            updateContentControlsHeader()
        }

        updateContentControlsHeader()
    }

    private fun updateContentControlsHeader() {
        val ch = if (preferenceManager.isChannelEnabled()) "Enabled" else "Disabled"
        val ns = if (preferenceManager.isNsfwEnabled()) "Enabled" else "Disabled"

        binding.contentControlsDropdown.setSummary("Home Channels: $ch • Adult: $ns")
        binding.contentControlsDropdown.setBadge(if (preferenceManager.isNsfwEnabled()) "18+" else null)
    }

    private fun showNsfwWarningDialog() {
        val dialog = NsfwAlertDialog()

        dialog.setYesContinueListener {
            ignoreNsfwCallback = true
            binding.nsfwToggleRow.setChecked(true)
            ignoreNsfwCallback = false

            preferenceManager.setNsfwEnabled(true)
            updateContentControlsHeader()

            dialog.dismiss()
        }

        dialog.setOnBackPressedListener {
            ignoreNsfwCallback = true
            binding.nsfwToggleRow.setChecked(false)
            ignoreNsfwCallback = false

            preferenceManager.setNsfwEnabled(false)
            updateContentControlsHeader()

            dialog.dismiss()
        }

        dialog.show(parentFragmentManager, "NsfwWarningDialog")
    }

    private fun setupThemeDemo(
        binding: MyAccountPageBinding,
        onChanged: (() -> Unit)? = null
    ) {
        fun render(theme: SeasonalTheme) {
            if (theme != SeasonalTheme.WINTER) lastNonWinterTheme = theme


            binding.themeDropdown.setSummary(
                when (theme) {
                    SeasonalTheme.DEFAULT -> "Default"
                    SeasonalTheme.WINTER -> "Winter"
                }
            )
            binding.themeDropdown.setBadge("DEMO")
            ignoreWinterToggleCallback = true
            binding.winterToggleRow.setChecked(theme == SeasonalTheme.WINTER)
            ignoreWinterToggleCallback = false

            onChanged?.invoke()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.seasonalTheme.collect { theme ->
                    render(theme)
                }
            }
        }

        binding.winterToggleRow.setOnCheckedChangedListener { isChecked ->
            if (ignoreWinterToggleCallback) return@setOnCheckedChangedListener

            if (isChecked) {
                settingsViewModel.setSeasonalTheme(SeasonalTheme.WINTER)
            } else {
                settingsViewModel.setSeasonalTheme(lastNonWinterTheme)
            }
        }

    }


    private fun startTextPulse(tv: TextView, fromColor: Int, toColor: Int) {
        themePreviewAnimator?.cancel()
        themePreviewAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                val c = a.animatedValue as Int
                tv.setTextColor(c)
            }
            start()
        }
    }

    private fun setupSubtitleStyle(
        binding: MyAccountPageBinding,
        prefs: PreferenceManager,
        onChanged: (() -> Unit)? = null
    ) {
        var state = prefs.getSubtitleStyle()
        val preview = binding.subtitlePreviewText

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        fun colorName(color: Int): String {
            val ctx = preview.context
            return when (color) {
                ContextCompat.getColor(ctx, R.color.netflix_white) -> "White"
                ContextCompat.getColor(ctx, R.color.orange) -> "Orange"
                ContextCompat.getColor(ctx, R.color.netflix_gray) -> "Gray"
                ContextCompat.getColor(ctx, R.color.netflix_green) -> "Green"
                ContextCompat.getColor(ctx, R.color.netflix_red) -> "Red"
                ContextCompat.getColor(ctx, R.color.cta_button_normal) -> "Blue"
                else -> "#%06X".format(0xFFFFFF and color)
            }
        }

        fun fontName(font: PreferenceManager.Font): String = when (font) {
            PreferenceManager.Font.DEFAULT -> "Default"
            PreferenceManager.Font.POPPINS -> "Poppins"
            PreferenceManager.Font.DAYS -> "Days"
            PreferenceManager.Font.MONO -> "Mono"
        }

        fun updateHeaderInfo() {
            val summary =
                "${fontName(state.font)} • ${state.sizeSp}sp • ${colorName(state.color)} • " +
                        "BG ${if (state.background) "On" else "Off"} • Outline ${if (state.outline) "On" else "Off"}"

            binding.subtitleStyleDropdown.setSummary(summary)
            binding.subtitleStyleDropdown.setBadge(if (state.isDefault()) null else "CUSTOM")
        }

        fun applyPreview() {
            preview.setTextColor(state.color)
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, state.sizeSp.toFloat())

            preview.typeface = when (state.font) {
                PreferenceManager.Font.DEFAULT -> Typeface.SANS_SERIF
                PreferenceManager.Font.POPPINS -> ResourcesCompat.getFont(
                    preview.context,
                    R.font.poppins
                )

                PreferenceManager.Font.DAYS -> ResourcesCompat.getFont(preview.context, R.font.days)
                PreferenceManager.Font.MONO -> Typeface.MONOSPACE
            }

            if (state.background) {
                preview.setBackgroundColor(
                    ContextCompat.getColor(
                        preview.context,
                        R.color.netflix_focus_overlay
                    )
                )
                preview.setPadding(dp(12), dp(6), dp(12), dp(6))
            } else {
                preview.background = null
                preview.setPadding(0, 0, 0, 0)
            }

            if (state.outline) preview.setShadowLayer(6f, 0f, 0f, Color.BLACK)
            else preview.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        fun commit() {
            prefs.saveSubtitleStyle(state)
            applyPreview()
            updateHeaderInfo()
            onChanged?.invoke()
        }

        applyPreview()
        updateHeaderInfo()

        val fonts = mapOf(
            binding.subtitleFontDefault to PreferenceManager.Font.DEFAULT,
            binding.subtitleFontPoppins to PreferenceManager.Font.POPPINS,
            binding.subtitleFontDays to PreferenceManager.Font.DAYS,
            binding.subtitleFontMono to PreferenceManager.Font.MONO
        )

        fonts.forEach { (view, font) ->
            view.isSelected = state.font == font
            view.setOnClickListener {
                fonts.keys.forEach { it.isSelected = false }
                view.isSelected = true
                state = state.copy(font = font)
                commit()
            }
        }

        binding.subtitleSizeStepper.setValue(state.sizeSp)
        binding.subtitleSizeStepper.setOnValueChangedListener {
            state = state.copy(sizeSp = it)
            commit()
        }

        val ctx = preview.context
        val colorMap = mapOf(
            binding.subtitleColorWhite to ContextCompat.getColor(ctx, R.color.netflix_white),
            binding.subtitleColorOrange to ContextCompat.getColor(ctx, R.color.orange),
            binding.subtitleColorGray to ContextCompat.getColor(ctx, R.color.netflix_gray),
            binding.subtitleColorGreen to ContextCompat.getColor(ctx, R.color.netflix_green),
            binding.subtitleColorRed to ContextCompat.getColor(ctx, R.color.netflix_red),
            binding.subtitleColorBlue to ContextCompat.getColor(ctx, R.color.cta_button_normal)
        )
        colorMap.forEach { (v, c) -> v.isSelected = (state.color == c) }

        fun selectColor(view: View, color: Int) {
            colorMap.keys.forEach { it.isSelected = false }
            view.isSelected = true
            state = state.copy(color = color)
            commit()
        }
        colorMap.forEach { (v, c) -> v.setOnClickListener { selectColor(v, c) } }

        binding.subtitleBgOn.isSelected = state.background
        binding.subtitleBgOff.isSelected = !state.background

        binding.subtitleBgOff.setOnClickListener {
            binding.subtitleBgOff.isSelected = true
            binding.subtitleBgOn.isSelected = false
            state = state.copy(background = false)
            commit()
        }
        binding.subtitleBgOn.setOnClickListener {
            binding.subtitleBgOff.isSelected = false
            binding.subtitleBgOn.isSelected = true
            state = state.copy(background = true)
            commit()
        }

        binding.subtitleOutlineOn.isSelected = state.outline
        binding.subtitleOutlineOff.isSelected = !state.outline

        binding.subtitleOutlineOff.setOnClickListener {
            binding.subtitleOutlineOff.isSelected = true
            binding.subtitleOutlineOn.isSelected = false
            state = state.copy(outline = false)
            commit()
        }
        binding.subtitleOutlineOn.setOnClickListener {
            binding.subtitleOutlineOff.isSelected = false
            binding.subtitleOutlineOn.isSelected = true
            state = state.copy(outline = true)
            commit()
        }
    }
}

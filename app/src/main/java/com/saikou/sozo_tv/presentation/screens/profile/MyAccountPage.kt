package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.MyAccountPageBinding
import com.saikou.sozo_tv.utils.LocalData


class MyAccountPage : Fragment() {
    private var _binding: MyAccountPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyAccountPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager()
        loadChannelPreference()
        loadNsfwPreference()
        loadModePreference()
        setupModeButtons()
        setupSubtitleStyle(binding, preferenceManager)
        binding.channelToggleContainer.setOnClickListener {
            binding.channelSwitch.toggle()
        }
        binding.channelSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateChannelStatus(isChecked)
            saveChannelPreference(isChecked)
        }

        binding.nsfwToggleContainer.setOnClickListener {
            binding.nsfwSwitch.toggle()
        }
        binding.nsfwSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreNsfwCallback) return@setOnCheckedChangeListener

            if (isChecked && !preferenceManager.isNsfwEnabled()) {
                ignoreNsfwCallback = true
                binding.nsfwSwitch.isChecked = false
                ignoreNsfwCallback = false

                showNsfwWarningDialog()
                return@setOnCheckedChangeListener
            }

            updateNsfStatus(isChecked)
            saveNsfPreference(isChecked)
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
        val isAnimeMode = isModeAnimeEnabled()
        updateModeUI(isAnimeMode)
    }

    private fun updateModeUI(isAnimeMode: Boolean) {
        binding.apply {
            updateButtonBackground(animeModeButton, isAnimeMode)
            updateButtonBackground(movieModeButton, !isAnimeMode)

        }
    }

    private fun isModeAnimeEnabled(): Boolean {
        return preferenceManager.isModeAnimeEnabled()
    }

    private fun setModeAnime(enabled: Boolean) {
        preferenceManager.setModeAnime(enabled)
        LocalData.isAnimeEnabled = enabled
    }

    @SuppressLint("SetTextI18n")
    private fun updateButtonBackground(button: android.widget.TextView, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(
                R.drawable.switch_selected_background
            )

        } else {
            button.setBackgroundResource(
                R.drawable.switch_background
            )

        }
    }


    private var ignoreNsfwCallback = false

    private fun showNsfwWarningDialog() {
        val dialog = NsfwAlertDialog()

        dialog.setYesContinueListener {
            ignoreNsfwCallback = true
            binding.nsfwSwitch.isChecked = true
            ignoreNsfwCallback = false

            updateNsfStatus(true)
            saveNsfPreference(true)
            dialog.dismiss()
        }

        dialog.setOnBackPressedListener {
            ignoreNsfwCallback = true
            binding.nsfwSwitch.isChecked = false
            ignoreNsfwCallback = false

            updateNsfStatus(false)
            saveNsfPreference(false)
            dialog.dismiss()
        }

        dialog.show(parentFragmentManager, "NsfwWarningDialog")
    }

    private fun saveChannelPreference(isEnabled: Boolean) {
        preferenceManager.setChannelEnabled(isEnabled)
    }

    private fun saveNsfPreference(isEnabled: Boolean) {
        preferenceManager.setNsfwEnabled(isEnabled)
    }

    private fun loadChannelPreference() {
        val isEnabled = preferenceManager.isChannelEnabled()
        binding.channelSwitch.isChecked = isEnabled
        updateChannelStatus(isEnabled)
    }

    private fun loadNsfwPreference() {
        val isEnabled = preferenceManager.isNsfwEnabled()
        binding.nsfwSwitch.isChecked = isEnabled
        updateNsfStatus(isEnabled)
    }

    @SuppressLint("SetTextI18n")
    private fun updateChannelStatus(isEnabled: Boolean) {
        binding.apply {
            if (isEnabled) {
                channelStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_enabled
                )
                channelStatusText.text = "Enabled"
                channelStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_green
                    )
                )
            } else {
                channelStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_disabled
                )
                channelStatusText.text = "Disabled"
                channelStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_gray
                    )
                )
            }
        }
    }

    private fun setupSubtitleStyle(
        binding: MyAccountPageBinding,
        prefs: PreferenceManager
    ) {
        var state = prefs.getSubtitleStyle()
        val preview = binding.subtitlePreviewText

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
                preview.setPadding(12, 6, 12, 6)
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
        }

        // init
        applyPreview()
        updateHeaderInfo()

        // Fonts
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

        // Size
        binding.subtitleSizeStepper.setValue(state.sizeSp)
        binding.subtitleSizeStepper.setOnValueChangedListener {
            state = state.copy(sizeSp = it)
            commit()
        }

        // Colors (init selected ham)
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

        // BG init + click
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

        // Outline init + click
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


    private fun updateNsfStatus(isEnabled: Boolean) {
        binding.apply {
            if (isEnabled) {
                nsfwStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_enabled
                )
                nsfwStatusText.text = "Enabled"
                nsfwStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_green
                    )
                )
            } else {
                nsfwStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_disabled
                )
                nsfwStatusText.text = "Disabled"
                nsfwStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_gray
                    )
                )
            }
        }
    }
}
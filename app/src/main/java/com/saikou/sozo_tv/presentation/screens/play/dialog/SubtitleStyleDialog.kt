package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.DialogSubtitleStyleBinding

class SubtitleStyleDialog : DialogFragment() {

    private var _binding: DialogSubtitleStyleBinding? = null
    private val binding get() = _binding!!

    private var onStyleChanged: (() -> Unit)? = null

    companion object {
        fun newInstance(): SubtitleStyleDialog = SubtitleStyleDialog()
    }

    fun setOnStyleChangedListener(listener: () -> Unit) {
        onStyleChanged = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow()

        binding.close.setOnClickListener { dismiss() }

        val prefs = PreferenceManager(requireContext())
        setupSubtitleStyleUi(binding, prefs) {
            onStyleChanged?.invoke()
        }

        val font = prefs.getSubtitleStyle().font
        val focusTarget = when (font) {
            PreferenceManager.Font.POPPINS -> binding.subtitleFontPoppins
            PreferenceManager.Font.DAYS -> binding.subtitleFontDays
            PreferenceManager.Font.MONO -> binding.subtitleFontMono
            PreferenceManager.Font.DEFAULT -> binding.subtitleFontDefault
        }
        focusTarget.post { focusTarget.requestFocus() }
    }

    private fun setupSubtitleStyleUi(
        binding: DialogSubtitleStyleBinding,
        prefs: PreferenceManager,
        onChanged: (() -> Unit)? = null
    ) {
        var state = prefs.getSubtitleStyle()
        val preview = binding.subtitlePreviewText

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        fun fontName(font: PreferenceManager.Font): String = getString(
            when (font) {
                PreferenceManager.Font.DEFAULT -> R.string.subtitle_font_default
                PreferenceManager.Font.POPPINS -> R.string.poppins
                PreferenceManager.Font.DAYS -> R.string.days
                PreferenceManager.Font.MONO -> R.string.subtitle_font_mono
            }
        )

        fun colorName(color: Int): String {
            val ctx = preview.context
            val named = when (color) {
                ContextCompat.getColor(ctx, R.color.netflix_white) -> R.string.subtitle_color_white
                ContextCompat.getColor(ctx, R.color.orange) -> R.string.subtitle_color_orange
                ContextCompat.getColor(ctx, R.color.netflix_gray) -> R.string.subtitle_color_gray
                ContextCompat.getColor(ctx, R.color.netflix_green) -> R.string.subtitle_color_green
                ContextCompat.getColor(ctx, R.color.netflix_red) -> R.string.subtitle_color_red
                ContextCompat.getColor(ctx, R.color.cta_button_normal) -> R.string.subtitle_color_blue
                else -> null
            }
            return named?.let { getString(it) } ?: "#%06X".format(0xFFFFFF and color)
        }

        fun onOff(enabled: Boolean): String =
            getString(if (enabled) R.string.on else R.string.off)

        fun updateSummary() {
            binding.styleSummary.text = getString(
                R.string.subtitle_style_summary,
                fontName(state.font),
                state.sizeSp,
                colorName(state.color),
                onOff(state.background),
                onOff(state.outline),
            )
        }

        fun resolveTypeface(): Typeface {
            return when (state.font) {
                PreferenceManager.Font.DEFAULT -> Typeface.SANS_SERIF
                PreferenceManager.Font.MONO -> Typeface.MONOSPACE
                PreferenceManager.Font.POPPINS -> ResourcesCompat.getFont(
                    preview.context,
                    R.font.poppins
                )
                    ?: Typeface.SANS_SERIF

                PreferenceManager.Font.DAYS -> ResourcesCompat.getFont(preview.context, R.font.days)
                    ?: Typeface.SANS_SERIF
            }
        }

        fun applyPreview() {
            preview.setTextColor(state.color)
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, state.sizeSp.toFloat())
            preview.typeface = resolveTypeface()

            if (state.background) {
                preview.setBackgroundColor(
                    ContextCompat.getColor(preview.context, R.color.netflix_focus_overlay)
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
            updateSummary()
            onChanged?.invoke()
        }

        applyPreview()
        updateSummary()

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

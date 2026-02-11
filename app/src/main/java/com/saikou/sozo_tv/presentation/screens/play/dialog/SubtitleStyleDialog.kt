package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
        setupDialogWindow()

        binding.close.setOnClickListener { dismiss() }

        val prefs = PreferenceManager(requireContext())
        setupSubtitleStyleUi(binding, prefs) {
            onStyleChanged?.invoke()
        }
    }

    private fun setupDialogWindow() {
        dialog?.window?.apply {
            setWindowAnimations(R.style.DialogAnimation)
            setBackgroundDrawable(ColorDrawable(0))
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun setupSubtitleStyleUi(
        binding: DialogSubtitleStyleBinding,
        prefs: PreferenceManager,
        onChanged: (() -> Unit)? = null
    ) {
        var state = prefs.getSubtitleStyle()
        val preview = binding.subtitlePreviewText

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        fun fontName(font: PreferenceManager.Font): String = when (font) {
            PreferenceManager.Font.DEFAULT -> "Default"
            PreferenceManager.Font.POPPINS -> "Poppins"
            PreferenceManager.Font.DAYS -> "Days"
            PreferenceManager.Font.MONO -> "Mono"
        }

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

        fun updateSummary() {
            val summary =
                "${fontName(state.font)} • ${state.sizeSp}sp • ${colorName(state.color)} • " +
                        "BG ${if (state.background) "On" else "Off"} • " +
                        "Outline ${if (state.outline) "On" else "Off"}"
            binding.styleSummary.text = summary
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

        // initial
        applyPreview()
        updateSummary()

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

        // Colors
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

        // BG
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

        // Outline
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

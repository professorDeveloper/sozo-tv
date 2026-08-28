package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SubtitleAdapter
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.DialogSubtitleChooserBinding

class SubtitleChooserDialog : DialogFragment() {

    private var subtitles: List<SubTitle> = emptyList()
    private var currentSelected: SubTitle? = null
    private var subtitlesEnabled: Boolean = false

    private lateinit var adapter: SubtitleAdapter

    private var onSelected: ((SubTitle?) -> Unit)? = null
    private var onStyleChanged: (() -> Unit)? = null

    private var _binding: DialogSubtitleChooserBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            subtitles: List<SubTitle>,
            selectedSubtitle: SubTitle?,
            subtitlesEnabled: Boolean
        ): SubtitleChooserDialog {
            return SubtitleChooserDialog().apply {
                this.subtitles = subtitles
                this.currentSelected = selectedSubtitle
                this.subtitlesEnabled = subtitlesEnabled
            }
        }
    }

    fun setSubtitleSelectionListener(listener: (SubTitle?) -> Unit) {
        onSelected = listener
    }

    fun setOnSubtitleStyleChangedListener(listener: () -> Unit) {
        onStyleChanged = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogWindow()

        adapter = SubtitleAdapter(subtitles, currentSelected) { selectedSub ->
            adapter.setSelectedIndex(subtitles.indexOf(selectedSub))
            commit(selectedSub)
        }

        binding.rvSubtitles.adapter = adapter

        setEnabledState(subtitlesEnabled, updateFocus = true)

        binding.subtitleToggleOff.setOnClickListener { commit(null) }
        binding.subtitleToggleOn.setOnClickListener {
            setEnabledState(true)
            // "On" has to reach the player by itself. It used to only repaint this panel and
            // reveal the list, so a viewer who pressed On and then Back had changed nothing:
            // the toggle showed "on" and the subtitles stayed off. The dialog deliberately
            // stays open afterwards, so the language can still be picked from the list.
            currentSelected?.let { onSelected?.invoke(it) }
        }

        binding.subtitleStyleBtn.setOnClickListener {
            SubtitleStyleDialog.newInstance().apply {
                setOnStyleChangedListener { onStyleChanged?.invoke() }
            }.show(parentFragmentManager, "subtitle_style")
        }

        binding.close.setOnClickListener { dismiss() }

        if (subtitles.isEmpty()) {
            setEnabledState(false, updateFocus = true)
            binding.subtitleToggleOn.isEnabled = false
            binding.subtitleStyleBtn.isEnabled = true
        }
    }

    private fun commit(choice: SubTitle?) {
        subtitlesEnabled = choice != null
        currentSelected = choice
        onSelected?.invoke(choice)
        dismiss()
    }

    private fun setEnabledState(enabled: Boolean, updateFocus: Boolean = true) {
        subtitlesEnabled = enabled

        binding.subtitleToggleOn.isSelected = enabled
        binding.subtitleToggleOff.isSelected = !enabled

        binding.rvSubtitles.isVisible = enabled
        binding.subtitleOffHint.isVisible = !enabled

        if (enabled && currentSelected == null && subtitles.isNotEmpty()) {
            currentSelected = subtitles.first()
            adapter.setSelectedIndex(0)
        }

        if (!updateFocus) return
        if (!enabled) {
            binding.subtitleToggleOff.requestFocus()
            return
        }
        binding.rvSubtitles.post {
            if (_binding == null) return@post
            if (subtitles.isNotEmpty()) {
                binding.rvSubtitles.selectedPosition = adapter.selectedIndex.coerceAtLeast(0)
            }
            binding.rvSubtitles.requestFocus()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

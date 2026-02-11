package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
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

    /** Agar player subtitleView ga style ni real-time qo‘llamoqchi bo‘lsangiz */
    fun setOnSubtitleStyleChangedListener(listener: () -> Unit) {
        onStyleChanged = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogWindow()

        // Adapter (sizdagi adapter bilan ishlaydi)
        adapter = SubtitleAdapter(subtitles, currentSelected) { selectedSub ->
            currentSelected = selectedSub
            // subtitle tanlansa, avtomatik ON qilib qo‘yamiz (qulay UX)
            setEnabledState(true)
            adapter.selected = selectedSub
            adapter.notifyDataSetChanged()
        }

        binding.rvSubtitles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubtitles.adapter = adapter

        // ON/OFF initial state
        setEnabledState(subtitlesEnabled, updateFocus = false)

        binding.subtitleToggleOff.setOnClickListener { setEnabledState(false) }
        binding.subtitleToggleOn.setOnClickListener { setEnabledState(true) }

        binding.subtitleStyleBtn.setOnClickListener {
            SubtitleStyleDialog.newInstance().apply {
                setOnStyleChangedListener {
                    onStyleChanged?.invoke()
                }
            }.show(parentFragmentManager, "subtitle_style")
        }

        binding.close.setOnClickListener {
            val result: SubTitle? = if (subtitlesEnabled) currentSelected else null
            onSelected?.invoke(result)
            dismiss()
        }

        // Subtitle yo‘q bo‘lsa:
        if (subtitles.isEmpty()) {
            setEnabledState(false, updateFocus = false)
            binding.subtitleToggleOn.isEnabled = false
            binding.subtitleStyleBtn.isEnabled = true // style baribir o‘zgarsa ham bo‘ladi
        }
    }

    private fun setEnabledState(enabled: Boolean, updateFocus: Boolean = true) {
        subtitlesEnabled = enabled

        binding.subtitleToggleOn.isSelected = enabled
        binding.subtitleToggleOff.isSelected = !enabled

        binding.rvSubtitles.isVisible = enabled
        binding.subtitleOffHint.isVisible = !enabled

        // ON bo‘lsa lekin hech narsa tanlanmagan bo‘lsa, birinchisini default qilamiz
        if (enabled && currentSelected == null && subtitles.isNotEmpty()) {
            currentSelected = subtitles.first()
            adapter.selected = currentSelected
            adapter.notifyDataSetChanged()
        }

        if (updateFocus) {
            if (!enabled) binding.subtitleToggleOff.requestFocus()
            else {
                // list ko‘rinsin va fokus tushsin
                binding.rvSubtitles.post { binding.rvSubtitles.requestFocus() }
            }
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

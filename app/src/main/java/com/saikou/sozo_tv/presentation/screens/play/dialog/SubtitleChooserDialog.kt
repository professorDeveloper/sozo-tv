package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.adapters.SubtitleAdapter
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.DialogSubtitleChooserBinding

class SubtitleChooserDialog : DialogFragment() {

    private var subtitles: List<SubTitle> = emptyList()
    private var currentSelected: SubTitle? = null
    private var subtitlesEnabled: Boolean = false

    private var searchTitle: String = ""
    private var searchIsSerial: Boolean = false
    private var searchSeason: Int? = null
    private var searchEpisode: Int? = null

    private lateinit var adapter: SubtitleAdapter

    private var onSelected: ((SubTitle?) -> Unit)? = null
    private var onStyleChanged: (() -> Unit)? = null
    private var onOnlinePicked: ((SubTitle) -> Unit)? = null
    private var onOffsetChanged: ((Int) -> Unit)? = null

    private var offsetMs: Int = 0

    private var _binding: DialogSubtitleChooserBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            subtitles: List<SubTitle>,
            selectedSubtitle: SubTitle?,
            subtitlesEnabled: Boolean,
            offsetMs: Int = 0,
            searchTitle: String = "",
            isSerial: Boolean = false,
            season: Int? = null,
            episode: Int? = null,
        ): SubtitleChooserDialog {
            return SubtitleChooserDialog().apply {
                this.subtitles = subtitles
                this.currentSelected = selectedSubtitle
                this.subtitlesEnabled = subtitlesEnabled
                this.offsetMs = offsetMs
                this.searchTitle = searchTitle
                this.searchIsSerial = isSerial
                this.searchSeason = season
                this.searchEpisode = episode
            }
        }
    }

    fun setSubtitleSelectionListener(listener: (SubTitle?) -> Unit) {
        onSelected = listener
    }

    fun setOnSubtitleStyleChangedListener(listener: () -> Unit) {
        onStyleChanged = listener
    }

    fun setOnSubtitleOffsetChanged(listener: (Int) -> Unit) {
        onOffsetChanged = listener
    }

    fun setOnOnlinePicked(listener: (SubTitle) -> Unit) {
        onOnlinePicked = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow()

        adapter = SubtitleAdapter(subtitles, currentSelected) { selectedSub ->
            adapter.setSelectedIndex(subtitles.indexOf(selectedSub))
            commit(selectedSub)
        }
        binding.rvSubtitles.adapter = adapter

        binding.subtitleToggleOff.setOnClickListener { commit(null) }
        binding.subtitleToggleOn.setOnClickListener {
            setEnabledState(true)
            currentSelected?.let { onSelected?.invoke(it) }
        }

        binding.subtitleStyleBtn.setOnClickListener {
            SubtitleStyleDialog.newInstance().apply {
                setOnStyleChangedListener { onStyleChanged?.invoke() }
            }.show(parentFragmentManager, "subtitle_style")
        }

        binding.close.setOnClickListener { dismiss() }

        binding.subtitleOffsetStepper.setValue(offsetMs)
        binding.subtitleOffsetStepper.setOnValueChangedListener { value ->
            if (value == offsetMs) return@setOnValueChangedListener
            offsetMs = value
            onOffsetChanged?.invoke(value)
        }

        binding.searchOnlineRow.setOnClickListener { openSearch() }
        binding.searchOnlineRow.isVisible = searchTitle.isNotBlank()

        if (subtitles.isEmpty()) {
            setEnabledState(enabled = false, updateFocus = false)
            binding.subtitleToggleOn.isEnabled = false
            if (searchTitle.isNotBlank()) {
                binding.searchOnlineRow.post { _binding?.searchOnlineRow?.requestFocus() }
            } else {
                binding.subtitleToggleOff.post { _binding?.subtitleToggleOff?.requestFocus() }
            }
        } else {
            setEnabledState(subtitlesEnabled, updateFocus = true)
        }
    }

    private fun openSearch() {
        SubtitleSearchDialog.newInstance(searchTitle, searchIsSerial, searchSeason, searchEpisode)
            .apply { setOnSubtitlePicked { adopt(it) } }
            .show(parentFragmentManager, "subtitle_search")
    }

    private fun adopt(picked: SubTitle) {
        subtitles = subtitles + picked
        subtitlesEnabled = true
        currentSelected = picked
        onOnlinePicked?.invoke(picked)
        if (isAdded) dismissAllowingStateLoss()
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

        binding.rvSubtitles.isVisible = enabled && subtitles.isNotEmpty()
        binding.subtitleOffHint.isVisible = !enabled

        if (enabled && currentSelected == null && subtitles.isNotEmpty()) {
            currentSelected = subtitles.first()
            adapter.setSelectedIndex(0)
        }

        if (!updateFocus) return
        if (!enabled || subtitles.isEmpty()) {
            binding.subtitleToggleOff.post { _binding?.subtitleToggleOff?.requestFocus() }
            return
        }
        binding.rvSubtitles.post {
            val rv = _binding?.rvSubtitles ?: return@post
            rv.selectedPosition = adapter.selectedIndex.coerceIn(0, subtitles.lastIndex)
            rv.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

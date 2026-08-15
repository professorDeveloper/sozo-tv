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

/**
 * Subtitle track picker.
 *
 * Behaves like [com.saikou.sozo_tv.presentation.screens.play.VideoQualityDialog]: choosing an
 * entry applies it and closes. It previously did not, and that was the whole of the reported
 * "subtitles don't work on TV":
 *
 * - Clicking a track only mutated local state. The selection was handed to the player solely
 *   by the small "X" button, so pressing OK on a track appeared to do nothing at all.
 * - BACK — the natural way to leave a dialog with a remote — dismissed without ever calling
 *   the listener, silently discarding the choice.
 * - The list was a plain RecyclerView marked `focusable="true"`, so `requestFocus()` focused
 *   the *container*: no row highlighted, and the first D-pad press went nowhere.
 * - Every selection ran `notifyDataSetChanged()`, rebinding all rows and destroying the
 *   focused view, so the highlight jumped back to the top.
 */
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

    /** Lets the player re-apply subtitle styling live while this dialog is open. */
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

        // No layoutManager assignment: VerticalGridView owns its own, and replacing it
        // breaks the focus and scrolling behaviour that makes it usable with a remote.
        binding.rvSubtitles.adapter = adapter

        setEnabledState(subtitlesEnabled, updateFocus = true)

        // OFF is a real choice — apply it instead of waiting for a separate confirm.
        binding.subtitleToggleOff.setOnClickListener { commit(null) }
        // ON only reveals the list; it picks no track, so it must not commit.
        binding.subtitleToggleOn.setOnClickListener { setEnabledState(true) }

        binding.subtitleStyleBtn.setOnClickListener {
            SubtitleStyleDialog.newInstance().apply {
                setOnStyleChangedListener { onStyleChanged?.invoke() }
            }.show(parentFragmentManager, "subtitle_style")
        }

        // Plain cancel: any real choice has already been applied by the time the user
        // reaches this button, so closing must not re-apply or revert anything.
        binding.close.setOnClickListener { dismiss() }

        if (subtitles.isEmpty()) {
            setEnabledState(false, updateFocus = true)
            binding.subtitleToggleOn.isEnabled = false
            binding.subtitleStyleBtn.isEnabled = true
        }
    }

    /** Applies [choice] to the player and closes. Null means "subtitles off". */
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
        // Land the highlight on the track that is actually playing. setSelectedPosition is
        // what moves a VerticalGridView's D-pad cursor; requestFocus on its own would leave
        // the cursor at row 0 no matter which track is active.
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

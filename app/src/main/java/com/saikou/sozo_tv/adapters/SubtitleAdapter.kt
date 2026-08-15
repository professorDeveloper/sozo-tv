package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.SubtitleItemBinding
import com.saikou.sozo_tv.utils.loadImage

/**
 * Subtitle track list for [com.saikou.sozo_tv.presentation.screens.play.dialog.SubtitleChooserDialog].
 *
 * Mirrors [VideoOptionsAdapter], deliberately: the quality picker is the one that behaves
 * correctly with a remote, and every difference between the two was a bug here.
 *
 * The two that mattered on a TV:
 *
 * 1. **Targeted notifies, never [notifyDataSetChanged].** A full rebind destroys and recreates
 *    every row — including the one holding D-pad focus — so the highlight jumped back to the
 *    top of the list (or vanished) the moment the user picked anything.
 * 2. **Focus flags set once, in [onCreateViewHolder].** Setting them per bind is wasted work on
 *    a recycled view, and re-registering the click listener on every bind is how a stale
 *    position gets captured in a closure.
 */
class SubtitleAdapter(
    private val subtitles: List<SubTitle>,
    selectedSubtitle: SubTitle?,
    private val onItemClick: (SubTitle) -> Unit
) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    private var selectedPosition: Int =
        selectedSubtitle?.let { subtitles.indexOf(it) } ?: -1

    /** Index of the active track, or -1. Used to seed the grid's focus position. */
    val selectedIndex: Int get() = selectedPosition

    var selected: SubTitle?
        get() = subtitles.getOrNull(selectedPosition)
        set(value) = setSelectedIndex(value?.let { subtitles.indexOf(it) } ?: -1)

    inner class ViewHolder(val binding: SubtitleItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subtitle: SubTitle, position: Int) = with(binding) {
            tvLanguage.text = subtitle.label
            tvInfo.text = extractInfo(subtitle)

            val isSelected = position == selectedPosition
            imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            // Drives the selector's state_selected, so the active track stays
            // distinguishable once focus moves elsewhere in the list.
            root.isSelected = isSelected

            if (subtitle.flag.isNotEmpty()) flagUrl.loadImage(subtitle.flag)

            root.setOnClickListener { onItemClick(subtitle) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            SubtitleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Set once. The row layout already declares these, but an adapter that is reused
        // from a non-TV context would otherwise depend on the XML alone.
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(subtitles[position], position)

    override fun getItemCount(): Int = subtitles.size

    /** Moves the checkmark without rebinding the list — see the class note. */
    fun setSelectedIndex(index: Int) {
        if (index == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = index
        if (previous in subtitles.indices) notifyItemChanged(previous)
        if (selectedPosition in subtitles.indices) notifyItemChanged(selectedPosition)
    }

    private fun extractInfo(subtitle: SubTitle): String = "From: ${subtitle.label}"
}

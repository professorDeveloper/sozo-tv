package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.SubtitleItemBinding
import com.saikou.sozo_tv.utils.loadImage

class SubtitleAdapter(
    private val subtitles: List<SubTitle>,
    selectedSubtitle: SubTitle?,
    private val onItemClick: (SubTitle) -> Unit
) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    private var selectedPosition: Int =
        selectedSubtitle?.let { subtitles.indexOf(it) } ?: -1

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
            root.isSelected = isSelected

            if (subtitle.flag.isNotEmpty()) flagUrl.loadImage(subtitle.flag)

            root.setOnClickListener { onItemClick(subtitle) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            SubtitleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(subtitles[position], position)

    override fun getItemCount(): Int = subtitles.size

    fun setSelectedIndex(index: Int) {
        if (index == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = index
        if (previous in subtitles.indices) notifyItemChanged(previous)
        if (selectedPosition in subtitles.indices) notifyItemChanged(selectedPosition)
    }

    private fun extractInfo(subtitle: SubTitle): String = "From: ${subtitle.label}"
}

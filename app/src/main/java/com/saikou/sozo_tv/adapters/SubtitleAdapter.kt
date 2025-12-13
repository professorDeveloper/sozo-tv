package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.SubtitleItemBinding

class SubtitleAdapter(
    private val subtitles: List<SubTitle>,
    selectedSubtitle: SubTitle?,
    private val onItemClick: (SubTitle) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    var selected: SubTitle? = selectedSubtitle

    inner class ViewHolder(val binding: SubtitleItemBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            SubtitleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subtitle = subtitles[position]
        with(holder.binding) {
            tvLanguage.text = subtitle.label
            tvInfo.text = extractInfo(subtitle)
            imgSelected.visibility =
                if (subtitle == selected) android.view.View.VISIBLE else android.view.View.GONE
        }
        holder.itemView.setOnClickListener {
            onItemClick(subtitle)
        }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true
    }

    override fun getItemCount(): Int = subtitles.size

    private fun extractInfo(subtitle: SubTitle): String {
        return "From: ${subtitle.label}"
    }
}
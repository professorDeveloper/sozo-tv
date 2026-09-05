package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemVideoQualityBinding
import com.saikou.sozo_tv.parser.models.VideoOption

class VideoOptionsAdapter(
    private val videoOptions: List<VideoOption>,
    private val onItemClick: (VideoOption, Int) -> Unit,
) : RecyclerView.Adapter<VideoOptionsAdapter.VideoOptionViewHolder>() {

    private var selectedPosition = -1

    inner class VideoOptionViewHolder(private val binding: ItemVideoQualityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(videoOption: VideoOption, position: Int) = with(binding) {
            tvResolution.text = videoOption.resolution
            tvAudioType.text = videoOption.audioType.name
            tvFansub.text = videoOption.fansub
            tvFansub.isVisible = videoOption.fansub.isNotBlank()
            tvQuality.text = videoOption.quality
            tvQuality.isVisible = videoOption.quality.isNotBlank()
            tvMetaSeparator.isVisible =
                videoOption.fansub.isNotBlank() && videoOption.quality.isNotBlank()
            ivSelected.isVisible = position == selectedPosition
            root.isSelected = position == selectedPosition
            root.setOnClickListener {
                setDefaultSelected(position)
                onItemClick(videoOption, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VideoOptionViewHolder(
        ItemVideoQualityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VideoOptionViewHolder, position: Int) =
        holder.bind(videoOptions[position], position)

    override fun getItemCount(): Int = videoOptions.size

    fun setDefaultSelected(index: Int) {
        if (index !in videoOptions.indices || index == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = index
        if (previous in videoOptions.indices) notifyItemChanged(previous)
        notifyItemChanged(index)
    }
}

package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemVideoQualityBinding
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.VideoOption

class VideoOptionsAdapter(
    private var videoOptions: List<VideoOption>,
    private val onItemClick: (VideoOption, Int) -> Unit
) : RecyclerView.Adapter<VideoOptionsAdapter.VideoOptionViewHolder>() {

    private var selectedPosition = -1

    private lateinit var onItemClickk: (VideoOption, Int) -> Unit
    fun setOnItemClickListener(listener: (VideoOption, Int) -> Unit) {
        onItemClickk = listener
    }

    inner class VideoOptionViewHolder(private val binding: ItemVideoQualityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(videoOption: VideoOption, position: Int) {
            binding.tvResolution.text = videoOption.resolution

            binding.tvAudioType.text = videoOption.audioType.name
            binding.tvAudioType.setBackgroundResource(
                if (videoOption.audioType == AudioType.SUB)
                    R.drawable.badge_audio_type
                else
                    R.drawable.badge_audio_type
            )

            binding.tvFansub.text = videoOption.fansub

            binding.tvQuality.text = videoOption.quality


            binding.ivQualityIcon.setImageResource(R.drawable.ic_video_settings)

            binding.ivSelected.visibility =
                if (position == selectedPosition) View.VISIBLE else View.GONE

            binding.root.isSelected = position == selectedPosition

            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position

                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(selectedPosition)

                onItemClick(videoOption, position)
            }

            binding.root.setOnClickListener {
                onItemClickk.invoke(videoOption, position)
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.root.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .start()
                } else {
                    binding.root.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoOptionViewHolder {
        val binding = ItemVideoQualityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoOptionViewHolder, position: Int) {
        holder.bind(videoOptions[position], position)
    }

    override fun getItemCount(): Int = videoOptions.size

    fun updateVideoOptions(newVideoOptions: List<VideoOption>) {
        videoOptions = newVideoOptions
        notifyDataSetChanged()
    }

    fun setDefaultSelected(index: Int) {
        if (index in 0 until videoOptions.size) {
            val previousPosition = selectedPosition
            selectedPosition = index

            if (previousPosition != -1) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)
        }
    }

    fun getSelectedPosition(): Int = selectedPosition

    fun getSelectedVideoOption(): VideoOption? {
        return if (selectedPosition != -1 && selectedPosition < videoOptions.size) {
            videoOptions[selectedPosition]
        } else null
    }

    fun setSelectedByActiveState() {
        val activeIndex = videoOptions.indexOfFirst { it.isActive }
        if (activeIndex != -1) {
            setDefaultSelected(activeIndex)
        }
    }
}

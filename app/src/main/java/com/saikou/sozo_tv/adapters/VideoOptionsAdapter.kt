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

class VideoOptionsAdapter(
    private var videoOptions: List<VideoOption>,
    private val onItemClick: (VideoOption, Int) -> Unit
) : RecyclerView.Adapter<VideoOptionsAdapter.VideoOptionViewHolder>() {

    private var selectedPosition = -1

    inner class VideoOptionViewHolder(private val binding: ItemVideoQualityBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(videoOption: VideoOption, position: Int) {
            // Set resolution text
            binding.tvResolution.text = videoOption.resolution

            // Set audio type badge
            binding.tvAudioType.text = videoOption.audioType.name
            binding.tvAudioType.setBackgroundResource(
                if (videoOption.audioType == AudioType.SUB)
                    R.drawable.badge_audio_type
                else
                    R.drawable.badge_audio_type
            )

            binding.tvFansub.text = videoOption.fansub

            binding.tvQuality.text = videoOption.quality

            when {
                videoOption.resolution.contains("1080", ignoreCase = true) -> {
                    binding.ivQualityIcon.setImageResource(R.drawable.ic_hd_video)
                }
                videoOption.resolution.contains("720", ignoreCase = true) -> {
                    binding.ivQualityIcon.setImageResource(R.drawable.ic_hd_video)
                }
                else -> {
                    binding.ivQualityIcon.setImageResource(R.drawable.ic_video_settings)
                }
            }

            // Show/hide selection indicator
            binding.ivSelected.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

            // Set selection state for background
            binding.root.isSelected = position == selectedPosition

            // Handle item click
            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position

                // Notify changes for smooth animation
                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(selectedPosition)

                onItemClick(videoOption, position)
            }

            // TV focus handling
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
        val binding = ItemVideoOptionBinding.inflate(
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

    // Function to update the list
    fun updateVideoOptions(newVideoOptions: List<VideoOption>) {
        videoOptions = newVideoOptions
        notifyDataSetChanged()
    }

    // Function to set default selected item
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

    // Function to get currently selected item
    fun getSelectedPosition(): Int = selectedPosition

    // Function to get selected video option
    fun getSelectedVideoOption(): VideoOption? {
        return if (selectedPosition != -1 && selectedPosition < videoOptions.size) {
            videoOptions[selectedPosition]
        } else null
    }

    // Function to find and select item by isActive property
    fun setSelectedByActiveState() {
        val activeIndex = videoOptions.indexOfFirst { it.isActive }
        if (activeIndex != -1) {
            setDefaultSelected(activeIndex)
        }
    }
}

// Data classes (if not already defined)
data class VideoOption(
    val kwikUrl: String,
    val fansub: String,
    val resolution: String,
    val audioType: AudioType,
    val quality: String,
    val isActive: Boolean,
    val fullText: String
)

enum class AudioType {
    SUB, DUB
}


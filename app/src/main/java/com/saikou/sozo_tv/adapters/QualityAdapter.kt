package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.Format
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemQualityBinding

class QualityAdapter(
    private val qualities: List<Format>,
    private val onQualitySelected: (Format?, Boolean) -> Unit
) : RecyclerView.Adapter<QualityAdapter.QualityViewHolder>() {

    companion object {
        const val AUTO_POSITION = 0
        var lastSelectedPosition = AUTO_POSITION
    }

    private var selectedPosition = lastSelectedPosition

    inner class QualityViewHolder(
        private val binding: ItemQualityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            val qualityString = if (position == AUTO_POSITION) {
                "Auto"
            } else {
                val format = qualities[position - 1]
                if (format.frameRate > 0f) "${format.height}p (${format.frameRate} fps)"
                else "${format.height}p (fps: N/A)"
            }

            binding.root.apply {
                text = qualityString
                isChecked = position == selectedPosition

                setOnClickListener {
                    handleSelection(position)
                }
            }
        }

        private fun handleSelection(position: Int) {
            if (selectedPosition != position) {
                val previousPosition = selectedPosition
                selectedPosition = position
                lastSelectedPosition = position

                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(position)

                val selectedFormat = if (position == AUTO_POSITION) null
                else qualities[position - 1]
                onQualitySelected(selectedFormat, position == AUTO_POSITION)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QualityViewHolder {
        val binding = ItemQualityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QualityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = qualities.size + 1

    fun updateSelectedQuality(format: Format?) {
        val newPosition = if (format == null) {
            AUTO_POSITION
        } else {
            qualities.indexOfFirst {
                it.height == format.height && it.frameRate == format.frameRate
            } + 1 // Add 1 because of Auto at position 0
        }
        if (newPosition != -1 && newPosition != selectedPosition) {
            val previousPosition = selectedPosition
            selectedPosition = newPosition
            lastSelectedPosition = newPosition // Update the persistent selection
            notifyItemChanged(previousPosition)
            notifyItemChanged(newPosition)
        }
    }
}
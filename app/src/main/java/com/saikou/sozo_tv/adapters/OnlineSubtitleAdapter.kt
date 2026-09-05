package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.remote.subtitles.OnlineSubtitle
import com.saikou.sozo_tv.databinding.ItemOnlineSubtitleBinding

class OnlineSubtitleAdapter(
    private val items: List<OnlineSubtitle>,
    private val onPick: (OnlineSubtitle) -> Unit,
) : RecyclerView.Adapter<OnlineSubtitleAdapter.VH>() {

    inner class VH(private val binding: ItemOnlineSubtitleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OnlineSubtitle) = with(binding) {
            subtitlePrimary.text = item.primaryLabel
            subtitleDetail.text = item.detailLabel
            subtitleDetail.isVisible = item.detailLabel.isNotBlank()
            root.setOnClickListener { onPick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemOnlineSubtitleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size
}

package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.databinding.ItemChannelBinding

class ChannelsAdapter(
    private val channels: List<Channel>,
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(channel: Channel, onChannelClick: (Channel) -> Unit) {
            binding.tvChannelName.text = channel.name

            binding.tvChannelInfo.text = "${channel.country} • ${channel.language}"

            binding.ivGeoBlocked.visibility = if (channel.isGeoBlocked) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onChannelClick(channel) }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                binding.root.isSelected = hasFocus
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, onChannelClick)
    }

    override fun getItemCount(): Int = channels.size
}

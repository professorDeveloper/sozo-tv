package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemVideoServerBinding

class VideoServersAdapter(
    private val servers: List<ServerRow>,
    private var selectedPosition: Int,
    private val onItemClick: (ServerRow, Int) -> Unit,
) : RecyclerView.Adapter<VideoServersAdapter.VH>() {

    data class ServerRow(val name: String, val qualities: String)

    inner class VH(private val binding: ItemVideoServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ServerRow, position: Int) = with(binding) {
            tvServerName.text = row.name
            tvServerQualities.text = row.qualities
            tvServerQualities.isVisible = row.qualities.isNotBlank()
            ivServerSelected.isVisible = position == selectedPosition
            root.isSelected = position == selectedPosition
            root.setOnClickListener {
                select(position)
                onItemClick(row, position)
            }
        }
    }

    private fun select(position: Int) {
        if (position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous in servers.indices) notifyItemChanged(previous)
        if (position in servers.indices) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemVideoServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(servers[position], position)

    override fun getItemCount() = servers.size
}

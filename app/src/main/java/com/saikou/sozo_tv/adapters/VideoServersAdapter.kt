package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemVideoServerBinding
import com.saikou.sozo_tv.utils.applyTvFocusScale

class VideoServersAdapter(
    private val servers: List<ServerRow>,
    private var selectedPosition: Int,
    private val onItemClick: (ServerRow) -> Unit,
) : RecyclerView.Adapter<VideoServersAdapter.VH>() {

    data class ServerRow(val name: String, val qualities: String)

    inner class VH(private val binding: ItemVideoServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.applyTvFocusScale(scale = 1.02f)
        }

        fun bind(row: ServerRow, position: Int) = with(binding) {
            tvServerName.text = row.name
            tvServerQualities.text = row.qualities
            tvServerQualities.isVisible = row.qualities.isNotBlank()
            ivServerSelected.isVisible = position == selectedPosition
            root.isSelected = position == selectedPosition
            root.setOnClickListener { onItemClick(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemVideoServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(servers[position], position)

    override fun getItemCount() = servers.size
}

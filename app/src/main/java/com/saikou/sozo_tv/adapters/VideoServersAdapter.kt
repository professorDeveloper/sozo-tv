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

        // Deliberately not scaled. The row is match_parent, so growing it on focus pushes it
        // past the list it lives in and the edges clip — the background selector is what
        // marks focus here.

        fun bind(row: ServerRow, position: Int) = with(binding) {
            tvServerName.text = row.name
            tvServerQualities.text = row.qualities
            tvServerQualities.isVisible = row.qualities.isNotBlank()
            ivServerSelected.isVisible = position == selectedPosition
            root.isSelected = position == selectedPosition
            root.setOnClickListener { onItemClick(row, position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemVideoServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(servers[position], position)

    override fun getItemCount() = servers.size
}

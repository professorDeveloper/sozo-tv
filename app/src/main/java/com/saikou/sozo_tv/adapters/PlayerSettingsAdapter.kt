package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemPlayerSettingBinding

/** A settings row: what it is, what it is set to, and whether it is the current pick. */
data class SettingRow(
    val label: String,
    val value: String = "",
    val ticked: Boolean = false,
)

class PlayerSettingsAdapter(
    private val rows: List<SettingRow>,
    private val onPick: (Int) -> Unit,
) : RecyclerView.Adapter<PlayerSettingsAdapter.VH>() {

    inner class VH(private val binding: ItemPlayerSettingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SettingRow, position: Int) = with(binding) {
            settingLabel.text = row.label
            settingValue.text = row.value
            // A row with nothing to say underneath should not reserve the space
            // for it — a menu of one-line rows reads much faster.
            settingValue.isVisible = row.value.isNotEmpty()
            settingTick.isVisible = row.ticked
            root.isFocusable = true
            root.setOnClickListener { onPick(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemPlayerSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position], position)

    override fun getItemCount() = rows.size
}

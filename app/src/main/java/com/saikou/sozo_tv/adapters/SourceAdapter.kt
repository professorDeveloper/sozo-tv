package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.ItemSourceBinding

class SourceAdapter(
    private val onClick: (SubSource) -> Unit,
    private val currentSelectedId: String? = null
) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {

    private val items = mutableListOf<SubSource>()
    private var selectedIndex = -1

    init {
        selectedIndex = items.indexOfFirst { it.sourceId == currentSelectedId }
    }

    inner class SourceViewHolder(val binding: ItemSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubSource, isSelected: Boolean) {
            binding.tvCode.text = item.title.uppercase()
            binding.tvTitle.text = item.country

            val bgRes = if (isSelected) R.drawable.bg_item_selected
            else R.drawable.bg_item_normal

            binding.rootItem.background = ContextCompat.getDrawable(binding.root.context, bgRes)

            binding.rootItem.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = bindingAdapterPosition
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val binding = ItemSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<SubSource>) {
        items.clear()
        items.addAll(newList)
        selectedIndex = items.indexOfFirst { it.sourceId == currentSelectedId }
        notifyDataSetChanged()
    }

    fun setSelectedIndex(sourceId: String?) {
        selectedIndex = items.indexOfFirst { it.sourceId == sourceId }
        if (selectedIndex != -1) notifyDataSetChanged()
    }
}
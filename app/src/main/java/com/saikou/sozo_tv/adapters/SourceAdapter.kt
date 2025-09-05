package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.ItemSourceBinding

class SourceAdapter(
    private val onClick: (SubSource) -> Unit,
) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {

    private val items = mutableListOf<SubSource>()
    private var selectedIndex = -1

    inner class SourceViewHolder(val binding: ItemSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubSource, isSelected: Boolean) {
            binding.tvCode.text = item.country.uppercase()
            binding.tvTitle.text = item.title

            val bgRes = if (isSelected)
                R.drawable.bg_item_selected
            else
                R.drawable.bg_item_normal

            binding.rootItem.background =
                ContextCompat.getDrawable(binding.root.context, bgRes)

            binding.rootItem.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = bindingAdapterPosition
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
                onClick(item)
            }
            binding.root.setOnFocusChangeListener { view, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(
                        binding.root.context,
                        R.anim.zoom_in
                    )

                    else -> AnimationUtils.loadAnimation(
                        binding.root.context,
                        R.anim.zoom_out
                    )
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
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
        selectedIndex = -1
        notifyDataSetChanged()
    }

    fun removeItem(item: SubSource) {
        val index = items.indexOfFirst { it.sourceId == item.sourceId }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun setSelectedIndex(item: SubSource) {
        val index = items.indexOfFirst { it.sourceId == item.sourceId }
        selectedIndex = index
        notifyDataSetChanged()
    }

    fun upsertItem(item: SubSource) {
        val index = items.indexOfFirst { it.sourceId == item.sourceId }
        if (index != -1) {
            items[index] = item
            notifyItemChanged(index)
        } else {
            items.add(item)
            notifyItemInserted(items.size - 1)
        }
    }
}

package com.saikou.sozo_tv.presentation.screens.category

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemTvCategoryBinding
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible

class CategoryTabAdapter(private var isFiltered: Boolean = true) :
    RecyclerView.Adapter<CategoryTabAdapter.SeriesTabVh>() {
    private val list = ArrayList<String>()
    private var selectedPosition: Int = 1
    private lateinit var focusedItemListener: (String, Int) -> Unit
    private var lastItemClickListener: (() -> Unit)? = null

    fun setFocusedItemListener(listener: (String, Int) -> Unit) {
        focusedItemListener = listener
    }

    fun setLastItemClickListener(listener: () -> Unit) {
        lastItemClickListener = listener
    }

    inner class SeriesTabVh(private val binding: ItemTvCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun onBind(data: String, position: Int) {
            binding.title.text = data
            val isSelected = (position == selectedPosition)
            // Drive the label through the view's state, not a flat colour. The layout already
            // points title at the @color/color_item_tv_category_tv state list and marks it
            // duplicateParentState, so setting isSelected here is enough - and setTextColor(int)
            // would replace that list outright, which is why a focused tab used to render white
            // text on its white focused background.
            binding.root.isSelected = isSelected
            if (isFiltered) {
                if (position != 0) {
                    binding.filterIcon.gone()
                    binding.root.setBackgroundResource(if (isSelected) R.drawable.background_item_tv_category_tv_selected else R.drawable.background_item_tv_category_tv)
                } else {
                    binding.filterIcon.visible()
                    binding.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_default)
                }
            } else {
                binding.root.setBackgroundResource(if (isSelected) R.drawable.background_item_tv_category_tv_selected else R.drawable.background_item_tv_category_tv)
            }
            binding.root.applyTvFocusScale()
            binding.root.setOnClickListener {
                if (isFiltered) {
                    if (position == 0) {
                        lastItemClickListener?.invoke()
                    } else {
                        val previousPosition = selectedPosition
                        selectedPosition = position
                        notifyItemChanged(previousPosition)
                        notifyItemChanged(selectedPosition)
                        focusedItemListener.invoke(data, position)
                    }
                } else {
                    val previousPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    focusedItemListener.invoke(data, position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesTabVh {
        return SeriesTabVh(
            ItemTvCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    /**
     * Targeted notifies, never notifyDataSetChanged: a full rebind destroys and recreates every
     * row, including the one the remote is sitting on, so the highlight vanished every time the
     * caller re-asserted the selection.
     */
    fun setSelectedPosition(position: Int) {
        if (position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous in list.indices) notifyItemChanged(previous)
        if (selectedPosition in list.indices) notifyItemChanged(selectedPosition)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: ArrayList<String>) {
        list.clear()
        if (isFiltered) list.add(0, "Filter")
        // Drop blank labels so no empty chip renders (BUG B).
        list.addAll(newList.filter { it.isNotBlank() })
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = list.size
    override fun onBindViewHolder(holder: SeriesTabVh, position: Int) {
        holder.onBind(list[position], position)
    }
}
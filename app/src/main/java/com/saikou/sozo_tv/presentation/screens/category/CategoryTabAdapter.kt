package com.saikou.sozo_tv.presentation.screens.category

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemTvCategoryBinding

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

            val context = binding.root.context
            val selectedColor = ContextCompat.getColor(context, R.color.selected_category_color)
            val defaultTextColor =
                ContextCompat.getColor(context, R.color.color_item_tv_category_tv)
            val isSelected = (position == selectedPosition)
            binding.title.setTextColor(if (isSelected) selectedColor else defaultTextColor)
            if (isFiltered) {
                if (position != 0) {
                    binding.root.setBackgroundResource(if (isSelected) R.drawable.background_item_tv_category_tv_selected else R.drawable.background_item_tv_category_tv)
                } else {
                    binding.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_default)
                }
            } else {
                binding.root.setBackgroundResource(if (isSelected) R.drawable.background_item_tv_category_tv_selected else R.drawable.background_item_tv_category_tv)
            }
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(
                        binding.root.context, R.anim.zoom_in
                    )

                    else -> AnimationUtils.loadAnimation(
                        binding.root.context, R.anim.zoom_out
                    )
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
            }
            binding.filterIcon.isVisible = position == 0
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

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    fun submitList(newList: ArrayList<String>) {
        list.clear()
        if (isFiltered) list.add(0, "Filter")
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = list.size
    override fun onBindViewHolder(holder: SeriesTabVh, position: Int) {
        holder.onBind(list[position], position)
    }
}
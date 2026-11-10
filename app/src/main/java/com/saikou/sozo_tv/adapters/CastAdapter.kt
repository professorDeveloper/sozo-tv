package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemCastBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.loadImage

class CastAdapter : RecyclerView.Adapter<CastAdapter.CastVh>() {
    lateinit var itemClickkedListener: (Cast) -> Unit
    var list = ArrayList<Cast>()

    override fun onCreateViewHolder(
        p0: ViewGroup,
        p1: Int
    ): CastVh {
        return CastVh(ItemCastBinding.inflate(LayoutInflater.from(p0.context), p0, false))
    }


    fun setOnItemClickListener(listener: (Cast) -> Unit) {
        itemClickkedListener = listener
    }

    override fun onBindViewHolder(
        p0: CastVh,
        p1: Int
    ) {
        p0.onBind(list[p1])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class CastVh(val binding: ItemCastBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBind(item: Cast) {
            binding.apply {
                binding.userNameTxt.text = item.name
                binding.characterTxt.text = item.role
                binding.accountImg.loadImage(item.image)
                binding.root.isFocusable = true
                binding.root.isClickable = true
                binding.root.setOnClickListener {
                    if (::itemClickkedListener.isInitialized) itemClickkedListener(item)
                }
            }
        }
    }

    fun submitList(newList: ArrayList<Cast>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}
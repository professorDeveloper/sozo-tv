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
    private val castList = ArrayList<Cast>()

    private lateinit var itemClickkedListener: (Cast) -> Unit

    fun setOnItemClickListener(listener: (Cast) -> Unit) {
        itemClickkedListener = listener
    }

    inner class CastVh(private val binding: ItemCastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun onBind(cast: Cast) {
            binding.userNameTxt.text = cast.name
            // Most sources send no role at all, and an empty line under every face read as a
            // half-loaded row.
            binding.characterTxt.isVisible = cast.role.isNotBlank()
            binding.characterTxt.text = cast.role
            binding.accountImg.loadImage(cast.image)
            // Nothing behind extensions can serve a person page: the repository's
            // characterDetail/creditDetail both fail unconditionally, so every cast card
            // opened a "not available" placeholder. The row stays informational.
            binding.root.isClickable = false
            binding.root.isFocusable = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CastVh {
        return CastVh(ItemCastBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int {
        return castList.size
    }

    override fun onBindViewHolder(holder: CastVh, position: Int) {
        holder.onBind(castList[position])
    }

    fun submitCast(cast: List<Cast>) {
        castList.clear()
        castList.addAll(cast)
        notifyDataSetChanged()
    }
}
package com.saikou.sozo_tv.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemCastBinding
import com.saikou.sozo_tv.domain.model.Cast
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
            binding.characterTxt.text = cast.role
            binding.accountImg.loadImage(cast.image)
            binding.root.setOnClickListener {
                itemClickkedListener.invoke(cast)
            }
            binding.root.setOnFocusChangeListener { _, hasFocus ->
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
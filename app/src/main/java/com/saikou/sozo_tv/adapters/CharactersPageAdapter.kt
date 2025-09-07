package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.databinding.ItemMovieBinding

class CharactersPageAdapter :
    RecyclerView.Adapter<CharactersPageAdapter.CharacterViewHolder>() {

    private val characterList: MutableList<CharacterEntity> = mutableListOf()
    private var clickListener: ((CharacterEntity) -> Unit)? = null

    fun setClickListener(listener: (CharacterEntity) -> Unit) {
        clickListener = listener
    }

    fun updateCharacters(newList: List<CharacterEntity>) {
        characterList.clear()
        characterList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = characterList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding = ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.bind(characterList[position])
    }

    inner class CharacterViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(character: CharacterEntity) {
            binding.apply {
                // Load character image
                Glide.with(root.context)
                    .load(character.image)
                    .placeholder(R.drawable.placeholder) // optional placeholder
                    .into(itemImg)

                // Set name
                topContainer.text = character.name

                // Click listener
                root.setOnClickListener {
                    clickListener?.invoke(character)
                }

                // Focus animation (zoom in/out)
                root.setOnFocusChangeListener { _, hasFocus ->
                    val animation = AnimationUtils.loadAnimation(
                        root.context,
                        if (hasFocus) R.anim.zoom_in else R.anim.zoom_out
                    )
                    root.startAnimation(animation)
                    animation.fillAfter = true
                }

                root.isFocusable = true
                root.isFocusableInTouchMode = true
            }
        }
    }
}

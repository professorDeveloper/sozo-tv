package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.EpisodeItemLittleBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.visible

class EpisodePlayerAdapter(
    var currentIndex: Int,
    var defaultImg: String,
) : RecyclerView.Adapter<EpisodePlayerAdapter.EpisodePlayerViewHolder>() {

    private val list = ArrayList<Data>()
    private lateinit var onEpisodeClick: (position: Int, data: Data) -> Unit
    fun setOnEpisodeClick(listener: (position: Int, data: Data) -> Unit) {
        onEpisodeClick = listener
    }

    inner class EpisodePlayerViewHolder(private val itemBinding: EpisodeItemLittleBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        @SuppressLint("SetTextI18n")
        fun onBind(data: Data, position: Int) {
            itemBinding.apply {
                if (position == currentIndex) {
                    itemBinding.nowPlayingBadge.visible()
                } else {
                    itemBinding.nowPlayingBadge.gone()
                }

                itemBinding.itemImg.loadImage(data.snapshot ?: defaultImg)

                // The real episode number, not the row's position: with the list
                // paged, row 0 of the second page is episode 101, and labelling
                // it "Episode 1" is worse than no label.
                val number = data.episode?.takeIf { it > 0 } ?: (position + 1)
                topContainer.text = root.context.getString(R.string.episode_n, number)
                root.setOnClickListener {
                    val previousIndex = currentIndex
                    currentIndex = position
                    notifyItemChanged(previousIndex)
                    notifyItemChanged(currentIndex)
                    onEpisodeClick(position, data)
                }
            }
        }
    }

    fun submitList(newList: List<Data>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodePlayerViewHolder {
        return EpisodePlayerViewHolder(
            EpisodeItemLittleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: EpisodePlayerViewHolder, position: Int) {
        holder.onBind(list[position], position)
    }
}

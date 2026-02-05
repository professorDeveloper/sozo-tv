package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
                    itemBinding.shimmerRibbonView.visible()
                } else {
                    itemBinding.shimmerRibbonView.gone()
                }

                itemBinding.itemImg.loadImage(data.snapshot ?: defaultImg)

                topContainer.text = "Episode ${position + 1}"
                shimmerTopRibbon.ribbon.text = "Episde ${data.episode ?: -1}"
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

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
import java.util.Locale

class EpisodePlayerAdapter(
    var currentIndex: Int,
    var defaultImg: String,
) : RecyclerView.Adapter<EpisodePlayerAdapter.EpisodePlayerViewHolder>() {

    private val list = ArrayList<Data>()
    private var progress: Map<String, Pair<Long, Long>> = emptyMap()
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

                val number = data.episode?.takeIf { it > 0 } ?: (position + 1)
                topContainer.text = root.context.getString(R.string.episode_n, number)

                val watched = data.session?.let { progress[it] }
                if (watched != null) {
                    val percent = (watched.first * 100 / watched.second).toInt().coerceIn(1, 100)
                    progressBar.progress = percent
                    progressBar.visible()
                    timeStr.text =
                        root.context.getString(R.string.episode_stopped_at, stamp(watched.first))
                    timeStr.visible()
                } else {
                    progressBar.gone()
                    timeStr.gone()
                }
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

    fun setProgress(next: Map<String, Pair<Long, Long>>) {
        progress = next
        notifyDataSetChanged()
    }

    fun selectEpisode(index: Int) {
        if (index == currentIndex) return
        val previous = currentIndex
        currentIndex = index
        if (previous in list.indices) notifyItemChanged(previous)
        if (index in list.indices) notifyItemChanged(index)
    }

    private fun stamp(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total / 60) % 60
        val sec = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.ROOT, "%02d:%02d", m, sec)
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

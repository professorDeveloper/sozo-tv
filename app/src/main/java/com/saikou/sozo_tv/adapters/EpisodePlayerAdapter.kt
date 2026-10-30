package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
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

    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<Data>() {
        override fun areItemsTheSame(oldItem: Data, newItem: Data) =
            oldItem.session == newItem.session && oldItem.episode == newItem.episode &&
                oldItem.season == newItem.season && oldItem.serverId == newItem.serverId
        override fun areContentsTheSame(oldItem: Data, newItem: Data) = oldItem == newItem
    })
    private val list get() = differ.currentList
    private var progress: Map<String, Pair<Long, Long>> = emptyMap()
    private lateinit var onEpisodeClick: (position: Int, data: Data) -> Unit
    fun setOnEpisodeClick(listener: (position: Int, data: Data) -> Unit) {
        onEpisodeClick = listener
    }

    inner class EpisodePlayerViewHolder(private val itemBinding: EpisodeItemLittleBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        @SuppressLint("SetTextI18n")
        fun onBind(data: Data, position: Int) {
            bindState(data, position)
            itemBinding.itemImg.loadImage(data.snapshot ?: defaultImg)
            val number = data.episode?.takeIf { it > 0 } ?: (position + 1)
            itemBinding.topContainer.text = itemBinding.root.context.getString(R.string.episode_n, number)
            itemBinding.root.setOnClickListener {
                val selected = bindingAdapterPosition
                if (selected == RecyclerView.NO_POSITION) return@setOnClickListener
                selectEpisode(selected)
                onEpisodeClick(selected, list[selected])
            }
        }

        fun bindState(data: Data, position: Int) {
            itemBinding.apply {
                if (position == currentIndex) nowPlayingBadge.visible() else nowPlayingBadge.gone()
                val watched = data.session?.let { progress[it] }
                val percent = episodeProgressPercent(watched)
                if (percent != null) {
                    progressBar.progress = percent
                    progressBar.visible()
                    timeStr.text = root.context.getString(R.string.episode_stopped_at, stamp(watched!!.first))
                    timeStr.visible()
                } else {
                    progressBar.gone()
                    timeStr.gone()
                }
            }
        }
    }

    fun setProgress(next: Map<String, Pair<Long, Long>>) {
        val previous = progress
        progress = next.toMap()
        list.forEachIndexed { index, episode ->
            if (previous[episode.session] != progress[episode.session]) {
                notifyItemChanged(index, STATE_PAYLOAD)
            }
        }
    }

    fun selectEpisode(index: Int) {
        if (index == currentIndex) return
        val previous = currentIndex
        currentIndex = index
        if (previous in list.indices) notifyItemChanged(previous, STATE_PAYLOAD)
        if (index in list.indices) notifyItemChanged(index, STATE_PAYLOAD)
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
        differ.submitList(newList.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodePlayerViewHolder {
        return EpisodePlayerViewHolder(
            EpisodeItemLittleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: EpisodePlayerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == STATE_PAYLOAD }) {
            holder.bindState(list[position], position)
        } else holder.onBind(list[position], position)
    }

    private companion object {
        const val STATE_PAYLOAD = "episode-state"
    }

    override fun onBindViewHolder(holder: EpisodePlayerViewHolder, position: Int) {
        holder.onBind(list[position], position)
    }
}

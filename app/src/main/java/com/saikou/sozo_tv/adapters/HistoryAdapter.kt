package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.databinding.ItemMovieHistoryBinding
import com.saikou.sozo_tv.utils.loadImage
import java.util.concurrent.TimeUnit

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val list = ArrayList<WatchHistoryEntity>()
    private lateinit var setItemHistoryListener: (WatchHistoryEntity) -> Unit
    fun setItemHistoryListener(listener: (WatchHistoryEntity) -> Unit) {
        setItemHistoryListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<WatchHistoryEntity>) {
        this.list.clear()
        this.list.addAll(newList)
        notifyDataSetChanged()
    }

    inner class HistoryViewHolder(private val itemBinding: ItemMovieHistoryBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(item: WatchHistoryEntity) {
            itemBinding.apply {
                val ctx = root.context

                // The show, not the episode label. `title` is stored as
                // "<episode title> - Episode N", which came out as "Play -
                // Episode 1" for a single-episode movie and "Episode 0 -
                // Episode 1" for an untitled one. mediaName is the name a
                // person actually recognises.
                movieTitle.text = item.mediaName.ifBlank { item.title }
                movieTitle.marqueeRepeatLimit = -1
                movieTitle.isSelected = true

                // Episode and source belong under the title as ordinary text,
                // not stamped across the poster in a red badge that wrapped to
                // two lines and hid a third of the artwork.
                val meta = buildList {
                    if (item.isEpisode && item.epIndex >= 0) {
                        add(ctx.getString(R.string.episode_n, item.epIndex + 1))
                    }
                    if (item.currentSourceName.isNotBlank()) add(item.currentSourceName)
                }
                timeStr.text = meta.joinToString(" · ")
                timeStr.isVisible = meta.isNotEmpty()

                // Where you stopped sits on the bar that shows it. This slot
                // used to hold `categoryProperty + " - " + release_year`, and
                // both are written empty/placeholder by the player — every card
                // read "- 2024/01/01".
                val total = item.totalDuration
                val position = item.lastPosition.coerceIn(0L, if (total > 0) total else Long.MAX_VALUE)
                infoItem.text = if (total > 0) {
                    ctx.getString(
                        R.string.history_progress_of,
                        formatMillisToTime(position),
                        formatMillisToTime(total),
                    )
                } else {
                    formatMillisToTime(position)
                }

                // A ProgressBar with max = 0 renders full, so an unknown
                // duration made a title stopped 13 seconds in look watched.
                progressBar.isVisible = total > 0
                if (total > 0) {
                    progressBar.max = total.toInt()
                    progressBar.progress = position.toInt()
                }

                // The old test was `lastPosition - totalDuration > -30000`,
                // which is true for ANY position when the duration is unknown —
                // so the badge showed on every card in the grid.
                val remaining = if (total > 0) total - position else -1L
                movieStatus.isVisible = remaining in 0 until 60_000
                movieStatus.text = ctx.getString(
                    if (remaining in 0 until 1_000) R.string.history_finished
                    else R.string.history_almost_finished
                )

                coverImage.loadImage(item.image)
                root.setOnClickListener { setItemHistoryListener.invoke(item) }
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(
            ItemMovieHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(item = list.get(position))
    }

    @SuppressLint("DefaultLocale")
    fun formatMillisToTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.databinding.ItemMovieHistoryBinding
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.visible
import java.util.concurrent.TimeUnit

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val list = ArrayList<WatchHistoryEntity>()
    private lateinit var setItemHistoryListener: (WatchHistoryEntity) -> Unit
    fun setItemHistoryListener(listener: (WatchHistoryEntity) -> Unit) {
        setItemHistoryListener = listener
    }

    fun submitList(newList: List<WatchHistoryEntity>) {
        this.list.clear()
        this.list.addAll(newList)
    }

    inner class HistoryViewHolder(private val itemBinding: ItemMovieHistoryBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: WatchHistoryEntity) {
            itemBinding.apply {
                timeStr.text = "Stopped at ${formatMillisToTime(item.lastPosition)}"
                infoItem.text = item.categoryProperty + " - " + item.release_year
                coverImage.loadImage(item.image)
                movieTitle.text = item.title
                movieStatus.isVisible = item.lastPosition - item.totalDuration > -30000
                progressBar.max = item.totalDuration.toInt()
                progressBar.progress = item.lastPosition.toInt()
                root.setOnClickListener {
                    setItemHistoryListener.invoke(item)
                }
                if (item.isEpisode) {
                    val title = item.title
                    movieTitle.text = title
                    movieTitle.marqueeRepeatLimit = -1
                    movieTitle.isSelected = true
                    movieStatus.visible()
                    movieStatus.text =
                        if (item.currentSourceName.isNotEmpty()) "Episode ${item.epIndex + 1} || ${item.currentSourceName}" else "Episode ${item.epIndex + 1}"
                }
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
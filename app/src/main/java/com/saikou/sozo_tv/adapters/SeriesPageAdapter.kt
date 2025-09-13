package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.databinding.EpisodeItemBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.visible
import java.util.concurrent.TimeUnit

class SeriesPageAdapter(
    val localEpisode: ArrayList<WatchHistoryEntity> = arrayListOf()
) : RecyclerView.Adapter<SeriesPageAdapter.EpisodeViewHolder>() {

    var episodeList: ArrayList<Data> = arrayListOf()
    private lateinit var onItemClicked: (Data, Int) -> Unit

    fun setOnItemClickedListener(listener: (Data, Int) -> Unit) {
        onItemClicked = listener
    }

    override fun getItemCount(): Int = episodeList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = EpisodeItemBinding.inflate(inflater, parent, false)
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(episodeList[position])
    }

    fun updateEpisodeItems(episodeList: List<Data>) {
        this.episodeList.clear()
        this.episodeList.addAll(episodeList)
        notifyDataSetChanged()
    }

    inner class EpisodeViewHolder(private val binding: EpisodeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(data: Data) {
            binding.apply {
                // progress data
                val getLocalEp = localEpisode.find { it.session == data.session }
                if (getLocalEp != null) {
//                    timeStr.visible()
                    progressBar.visible()
                    progressBar.max = getLocalEp.totalDuration.toInt()
                    progressBar.progress = getLocalEp.lastPosition.toInt()
                }else {
//                    timeStr.gone()
                    progressBar.gone()
                }
                binding.country.text = data.episode.toString()
                root.setOnClickListener { onItemClicked.invoke(data, absoluteAdapterPosition) }
                topContainer.text = "Episode ${data.episode ?: 0}"
                binding.root.setOnFocusChangeListener { _, hasFocus ->
                    val animation = when {
                        hasFocus -> AnimationUtils.loadAnimation(
                            binding.root.context, R.anim.zoom_in
                        )

                        else -> AnimationUtils.loadAnimation(
                            binding.root.context, R.anim.zoom_out
                        )
                    }
                    binding.root.startAnimation(animation)
                    animation.fillAfter = true
                }

                itemImg.loadImage(data.snapshot ?: LocalData.anime404)
            }
        }
    }

    @SuppressLint("DefaultLocale")
    fun formatMillisToTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

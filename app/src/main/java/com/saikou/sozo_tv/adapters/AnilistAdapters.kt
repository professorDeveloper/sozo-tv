package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.remote.anilist.AnilistListEntry
import com.saikou.sozo_tv.data.remote.anilist.AnilistStatus
import com.saikou.sozo_tv.databinding.ItemAnilistEntryBinding
import com.saikou.sozo_tv.databinding.ItemAnilistStatusBinding
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.loadImage

/** One title in the AniList library grid. */
class AnilistEntryAdapter(
    private val onClick: (AnilistListEntry) -> Unit,
) : ListAdapter<AnilistListEntry, AnilistEntryAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemAnilistEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(entry: AnilistListEntry) = with(binding) {
            val media = entry.media
            entryTitle.text = media.displayTitle
            entryProgress.text = media.episodes
                ?.let { "${entry.progress} / $it" }
                ?: entry.progress.toString()

            media.coverImage?.takeIf { it.isNotBlank() }?.let { coverImage.loadImage(it) }

            progressBar.progress = ((entry.completion ?: 0f) * 100).toInt()

            val behind = entry.behindBy
            behindBadge.isVisible = behind > 0
            if (behind > 0) behindBadge.text = "+$behind"

            root.applyTvFocusScale(scale = 1.06f)
            root.setOnClickListener { onClick(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemAnilistEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        /**
         * Compared by CONTENT, not just id: a "+1" writes a new progress value
         * onto the same entry, and an identity-only diff would leave the old
         * number on screen.
         */
        val DIFF = object : DiffUtil.ItemCallback<AnilistListEntry>() {
            override fun areItemsTheSame(a: AnilistListEntry, b: AnilistListEntry) = a.id == b.id
            override fun areContentsTheSame(a: AnilistListEntry, b: AnilistListEntry) =
                a.progress == b.progress && a.status == b.status && a.media.id == b.media.id
        }
    }
}

/** The status filter row. */
class AnilistStatusAdapter(
    private val onPick: (AnilistStatus) -> Unit,
) : RecyclerView.Adapter<AnilistStatusAdapter.VH>() {

    private val statuses = AnilistStatus.entries
    private var counts: Map<AnilistStatus, Int> = emptyMap()
    private var selected: AnilistStatus = AnilistStatus.CURRENT

    fun setCounts(counts: Map<AnilistStatus, Int>) {
        this.counts = counts
        notifyItemRangeChanged(0, statuses.size)
    }

    fun setSelected(status: AnilistStatus) {
        if (selected == status) return
        val previous = statuses.indexOf(selected)
        selected = status
        // Two targeted updates rather than notifyDataSetChanged: rebinding the
        // whole row while the d-pad sits on one of these chips drops focus back
        // to the screen root.
        notifyItemChanged(previous)
        notifyItemChanged(statuses.indexOf(status))
    }

    inner class VH(private val binding: ItemAnilistStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(status: AnilistStatus) = with(binding.statusLabel) {
            val count = counts[status] ?: 0
            text = if (count > 0) "${status.label}  $count" else status.label
            isSelected = status == selected
            applyTvFocusScale(scale = 1.04f)
            setOnClickListener { onPick(status) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemAnilistStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(statuses[position])

    override fun getItemCount() = statuses.size
}

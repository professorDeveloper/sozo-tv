package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.ItemGrouppedSourceBinding
import com.saikou.sozo_tv.domain.model.GroupedSource

class GroupedSourceAdapter(
    private val onSourceClick: (SubSource) -> Unit,
    private val selectedAnimeId: String?,
    private val selectedMovieId: String?
) : RecyclerView.Adapter<GroupedSourceAdapter.GroupedViewHolder>() {

    private val items = mutableListOf<GroupedSource>()

    inner class GroupedViewHolder(val binding: ItemGrouppedSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var sourceAdapter: SourceAdapter

        fun bind(item: GroupedSource) {
            binding.tvGroupTitle.text = item.title
            val selectedId = when (item.type) {
                "anime" -> selectedAnimeId
                "movie" -> selectedMovieId
                else -> null
            }

            sourceAdapter = SourceAdapter(
                onClick = onSourceClick,
                currentSelectedId = selectedId
            )

            binding.rvSources.apply {
                layoutManager = LinearLayoutManager(
                    context,
                    LinearLayoutManager.VERTICAL,
                    false
                )
                adapter = sourceAdapter
            }

            sourceAdapter.updateList(item.sources)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupedViewHolder {
        val binding = ItemGrouppedSourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupedViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<GroupedSource>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}